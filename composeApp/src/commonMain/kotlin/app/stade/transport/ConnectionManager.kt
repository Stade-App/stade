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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DialAttempt(
    val address: String,
    val timestamp: Long,
    val status: Status,
    val detail: String? = null
) {
    enum class Status { TRYING, CONNECT_OK, CONNECT_FAIL, HANDSHAKE_OK, HANDSHAKE_FAIL }
}

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
    private val dialing = mutableSetOf<String>()
    /**
     * Davet kabul edilmiş ama henüz Contact'a dönüşmemiş adresler.
     * Handshake bittiğinde SyncEngine bu bağlantıyı işler ve Contact otomatik
     * eklenir; bu listeden çıkan adresler ContactManager içindeki contact'a
     * geçer.
     */
    private val pendingDial = mutableSetOf<String>()

    private val _diagnostics = MutableStateFlow<Map<String, Map<String, DialAttempt>>>(emptyMap())
    val diagnostics: StateFlow<Map<String, Map<String, DialAttempt>>> = _diagnostics.asStateFlow()

    private fun recordAttempt(contactId: String, attempt: DialAttempt) {
        val cur = _diagnostics.value
        val perAddr = (cur[contactId] ?: emptyMap()).toMutableMap()
        perAddr[attempt.address] = attempt
        _diagnostics.value = cur + (contactId to perAddr)
    }

    fun selfAddresses(): List<String> =
        registry.all().flatMap { runCatching { it.selfAddresses() }.getOrDefault(emptyList()) }
            .filter { it.isNotBlank() }
            .distinct()

    /**
     * Davet kabul edildiğinde, henüz contact olmayan ama bağlanması istenen
     * adresleri kuyruğa alır. Dialer döngüsü bunları bilinen contact adresleriyle
     * birlikte deneyecek; başarılı handshake olunca SyncEngine yeni Contact'ı
     * yaratır.
     */
    fun queueDial(addresses: List<String>) {
        val selfSet = runCatching { selfAddresses().toSet() }.getOrDefault(emptySet())
        synchronized(pendingDial) {
            for (a in addresses) {
                if (a.isNotBlank() && a !in selfSet) pendingDial.add(a)
            }
        }
    }

    private fun snapshotPendingAddresses(): List<String> =
        synchronized(pendingDial) { pendingDial.toList() }

    private fun consumePendingAddress(addr: String) {
        synchronized(pendingDial) { pendingDial.remove(addr) }
    }

    suspend fun start(owner: LocalIdentity) = mutex.withLock {
        if (ownerRef?.id == owner.id) return@withLock
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
        dialing.clear()
        synchronized(pendingDial) { pendingDial.clear() }
        for (plugin in registry.all()) runCatching { plugin.stop() }
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private suspend fun dialerLoop(owner: LocalIdentity) {
        while (scope.isActive) {
            val now = nowMs()
            for (contact in contacts.all().filter { it.ownerId == owner.id }) {
                if (sync.isConnected(contact.id)) continue
                if (contact.id in dialing) continue
                val attempted = tryDial(owner, contact, now)
                if (attempted && !sync.isConnected(contact.id)) {
                    backoff[contact.id] = now + 10_000L
                }
            }
            // Davet kabul edildiğinde gelen ama henüz contact olmayan adresler
            tryDialPending(owner, now)
            delay(5_000)
        }
    }

    private suspend fun tryDialPending(owner: LocalIdentity, now: Long) {
        val pending = snapshotPendingAddresses()
        if (pending.isEmpty()) return
        val knownAddrs: Set<String> = contacts.all()
            .filter { it.ownerId == owner.id }
            .flatMap { it.addresses }
            .toSet()
        for (addr in pending) {
            if (addr in knownAddrs) {
                consumePendingAddress(addr) // zaten contact'ta var
                continue
            }
            val key = "pending|$addr"
            if ((backoff[key] ?: 0L) > now) continue
            val plugin = pluginForAddress(addr) ?: continue
            val conn = runCatching { plugin.connect(addr) }.getOrNull()
            if (conn == null) {
                backoff[key] = nowMs() + 30_000L
                continue
            }
            scope.launch {
                runCatching { sync.handleConnection(owner, conn) }
                consumePendingAddress(addr)
            }
            return // her tarama döngüsünde tek pending tetikle
        }
    }

    private suspend fun tryDial(owner: LocalIdentity, contact: Contact, now: Long): Boolean {
        var attempted = false
        val selfSet = runCatching { selfAddresses().toSet() }.getOrDefault(emptySet())
        for (addr in contact.addresses) {
            if (sync.isConnected(contact.id)) return attempted
            if (addr in selfSet) {
                recordAttempt(contact.id, DialAttempt(addr, now, DialAttempt.Status.CONNECT_FAIL, "bu kendi adresin (bayat) — güncellenmeli"))
                continue
            }
            val key = "${contact.id}|$addr"
            if ((backoff[key] ?: 0L) > now) continue
            val plugin = pluginForAddress(addr)
            if (plugin == null) {
                recordAttempt(contact.id, DialAttempt(addr, now, DialAttempt.Status.CONNECT_FAIL, "taşıma kapalı"))
                continue
            }
            attempted = true
            dialing += contact.id
            recordAttempt(contact.id, DialAttempt(addr, now, DialAttempt.Status.TRYING))
            val conn = runCatching { plugin.connect(addr) }
            val connection = conn.getOrNull()
            if (connection == null) {
                dialing -= contact.id
                val err = conn.exceptionOrNull()?.message?.take(80) ?: "ulaşılamadı / zaman aşımı"
                recordAttempt(contact.id, DialAttempt(addr, nowMs(), DialAttempt.Status.CONNECT_FAIL, err))
                backoff[key] = nowMs() + 30_000L
                continue
            }
            recordAttempt(contact.id, DialAttempt(addr, nowMs(), DialAttempt.Status.CONNECT_OK, "handshake yapılıyor…"))
            scope.launch {
                try {
                    val before = sync.isConnected(contact.id)
                    sync.handleConnection(owner, connection)
                    val after = sync.isConnected(contact.id)
                    if (after) {
                        recordAttempt(contact.id, DialAttempt(addr, nowMs(), DialAttempt.Status.HANDSHAKE_OK, "bağlandı ✓"))
                    } else if (before) {
                        recordAttempt(contact.id, DialAttempt(addr, nowMs(), DialAttempt.Status.HANDSHAKE_FAIL, "zaten bağlı"))
                    } else {
                        recordAttempt(contact.id, DialAttempt(addr, nowMs(), DialAttempt.Status.HANDSHAKE_FAIL, "handshake başarısız"))
                        backoff[key] = nowMs() + 30_000L
                    }
                } finally {
                    dialing -= contact.id
                }
            }
            return attempted
        }
        if ((backoff[contact.id] ?: 0L) <= now) {
            for (plugin in registry.all()) {
                val discoverable = plugin as? DiscoverableTransport ?: continue
                for (addr in discoverable.discoveredPeers()) {
                    if (sync.isConnected(contact.id)) return attempted
                    if (contact.id in dialing) return attempted
                    val conn = runCatching { plugin.connect(addr) }.getOrNull() ?: continue
                    attempted = true
                    dialing += contact.id
                    scope.launch {
                        try {
                            sync.handleConnection(owner, conn)
                        } finally {
                            dialing -= contact.id
                        }
                    }
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
