package app.stade.transport

import app.stade.contact.ContactManager
import app.stade.contact.Contact
import app.stade.identity.LocalIdentity
import app.stade.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ConnectionManager(
    private val registry: ConnectionRegistry,
    private val contacts: ContactManager,
    private val sync: SyncEngine
) {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var ownerRef: LocalIdentity? = null
    private val tasks = mutableListOf<Job>()
    private val backoff = mutableMapOf<String, Long>()

    /** Bu cihazın eriştiğimiz tüm taşıma katmanlarındaki dış adresleri (örn. lan://ip:port, tor://onion:port). */
    fun selfAddresses(): List<String> =
        registry.all().mapNotNull { runCatching { it.selfAddress() }.getOrNull() }
            .filter { it.isNotBlank() }

    suspend fun start(owner: LocalIdentity) = mutex.withLock {
        if (ownerRef?.id == owner.id) return@withLock
        // Owner değişti ya da ilk başlatma: önce temiz başla.
        if (ownerRef != null) stopInternal()
        ownerRef = owner
        if (!scope.isActive) scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        for (plugin in registry.all()) {
            tasks += scope.launch {
                runCatching {
                    plugin.start { connection -> sync.handleConnection(owner, connection) }
                }
            }
        }
        tasks += scope.launch { dialerLoop(owner) }
    }

    suspend fun stop() = mutex.withLock { stopInternal() }

    private suspend fun stopInternal() {
        ownerRef = null
        tasks.forEach { it.cancel() }
        tasks.clear()
        backoff.clear()
        for (plugin in registry.all()) runCatching { plugin.stop() }
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private suspend fun dialerLoop(owner: LocalIdentity) {
        while (scope.isActive) {
            val now = nowMs()
            for (contact in contacts.all().filter { it.ownerId == owner.id }) {
                if (sync.isConnected(contact.id)) continue
                val attempted = tryDial(owner, contact, now)
                if (attempted && !sync.isConnected(contact.id)) {
                    // Bu kişi için 10s sonra tekrar dene.
                    backoff[contact.id] = now + 10_000L
                }
            }
            delay(5_000)
        }
    }

    private suspend fun tryDial(owner: LocalIdentity, contact: Contact, now: Long): Boolean {
        var attempted = false
        // 1) Kişi üzerinde kayıtlı adresleri (lan://, tor://) doğrudan deneyelim — internet üzerinden ulaşmanın tek yolu.
        for (addr in contact.addresses) {
            if (sync.isConnected(contact.id)) return attempted
            val key = "${contact.id}|$addr"
            if ((backoff[key] ?: 0L) > now) continue
            val plugin = pluginForAddress(addr) ?: continue
            attempted = true
            val conn = runCatching { plugin.connect(addr) }.getOrNull()
            if (conn == null) {
                backoff[key] = now + 30_000L
                continue
            }
            scope.launch { sync.handleConnection(owner, conn) }
            return attempted
        }
        // 2) Aynı LAN'daysak UDP keşfi ile bulunan tüm peer'leri dene.
        if ((backoff[contact.id] ?: 0L) <= now) {
            for (plugin in registry.all()) {
                val discoverable = plugin as? DiscoverableTransport ?: continue
                for (addr in discoverable.discoveredPeers()) {
                    if (sync.isConnected(contact.id)) return attempted
                    val conn = runCatching { plugin.connect(addr) }.getOrNull() ?: continue
                    attempted = true
                    scope.launch { sync.handleConnection(owner, conn) }
                }
            }
        }
        return attempted
    }

    private fun pluginForAddress(address: String): TransportPlugin? {
        val type = when {
            address.startsWith("tor://") -> TransportType.TOR
            address.startsWith("lan://") -> TransportType.LAN
            else -> return null
        }
        return registry.get(type)
    }

    private fun nowMs(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
}


