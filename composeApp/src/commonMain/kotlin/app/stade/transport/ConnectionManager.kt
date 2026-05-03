package app.stade.transport

import app.stade.contact.ContactManager
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
                if ((backoff[contact.id] ?: 0L) > now) continue
                val attempted = tryDial(owner, contact.id)
                if (attempted && !sync.isConnected(contact.id)) {
                    // Backoff: 10s sonra tekrar dene.
                    backoff[contact.id] = now + 10_000L
                }
            }
            delay(5_000)
        }
    }

    private suspend fun tryDial(owner: LocalIdentity, contactId: String): Boolean {
        var attempted = false
        for (plugin in registry.all()) {
            val discoverable = plugin as? DiscoverableTransport ?: continue
            for (addr in discoverable.discoveredPeers()) {
                if (sync.isConnected(contactId)) return attempted
                val conn = runCatching { plugin.connect(addr) }.getOrNull() ?: continue
                attempted = true
                scope.launch { sync.handleConnection(owner, conn) }
            }
        }
        return attempted
    }

    private fun nowMs(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
}


