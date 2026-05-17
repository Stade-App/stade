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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
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
    private val pendingDial = mutableSetOf<String>()
    private val pendingAttempts = mutableMapOf<String, Int>()
    private val pendingWake = Channel<Unit>(capacity = Channel.CONFLATED)

    private val _diagnostics = MutableStateFlow<Map<String, Map<String, DialAttempt>>>(emptyMap())
    val diagnostics: StateFlow<Map<String, Map<String, DialAttempt>>> = _diagnostics.asStateFlow()

    private val _pendingDials = MutableStateFlow<Map<String, DialAttempt>>(emptyMap())
    val pendingDials: StateFlow<Map<String, DialAttempt>> = _pendingDials.asStateFlow()

    private fun recordPending(attempt: DialAttempt) {
        _pendingDials.value = _pendingDials.value + (attempt.address to attempt)
    }

    private fun clearPending(addr: String) {
        _pendingDials.value = _pendingDials.value - addr
    }

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

    fun queueDial(addresses: List<String>) {
        val selfSet = runCatching { selfAddresses().toSet() }.getOrDefault(emptySet())
        synchronized(pendingDial) {
            for (a in addresses) {
                if (a.isNotBlank() && a !in selfSet) {
                    pendingDial.add(a)
                    pendingAttempts.remove(a)
                    recordPending(DialAttempt(a, nowMs(), DialAttempt.Status.TRYING, "kuyrukta…"))
                }
            }
        }
        backoff.keys.removeAll { it.startsWith("pending|") }
        pendingWake.trySend(Unit)
    }

    private fun snapshotPendingAddresses(): List<String> =
        synchronized(pendingDial) { pendingDial.toList() }

    private fun consumePendingAddress(addr: String) {
        synchronized(pendingDial) {
            pendingDial.remove(addr)
            pendingAttempts.remove(addr)
        }
        clearPending(addr)
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
        tasks += scope.launch { pendingDialLoop(owner) }
    }

    suspend fun stop() = mutex.withLock { stopInternal() }

    suspend fun restart(type: TransportType) = mutex.withLock {
        val owner = ownerRef ?: return@withLock
        val plugin = registry.get(type) ?: return@withLock
        runCatching { plugin.stop() }
        if (!scope.isActive) scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        tasks += scope.launch {
            runCatching {
                plugin.start { connection -> sync.handleConnection(owner, connection) }
            }
        }
    }

    private suspend fun stopInternal() {
        ownerRef = null
        tasks.forEach { it.cancel() }
        tasks.clear()
        backoff.clear()
        dialing.clear()
        synchronized(pendingDial) {
            pendingDial.clear()
            pendingAttempts.clear()
        }
        _pendingDials.value = emptyMap()
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
            delay(5_000)
        }
    }

    /** Bekleyen davet adreslerini mevcut kişi bağlantı döngüsünden bağımsız olarak dener. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun pendingDialLoop(owner: LocalIdentity) {
        while (scope.isActive) {
            val now = nowMs()
            tryDialPending(owner, now)
            select<Unit> {
                pendingWake.onReceive { }
                onTimeout(3_000) { }
            }
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
                consumePendingAddress(addr)
                continue
            }
            val key = "pending|$addr"
            if ((backoff[key] ?: 0L) > now) continue
            val plugin = pluginForAddress(addr)
            if (plugin == null) {
                recordPending(DialAttempt(addr, nowMs(), DialAttempt.Status.CONNECT_FAIL, "taşıma hazır değil — bekleniyor"))
                backoff[key] = nowMs() + 4_000L
                continue
            }
            val pluginInfo = plugin.info.value
            if (!pluginInfo.running) {
                recordPending(DialAttempt(addr, nowMs(), DialAttempt.Status.TRYING, "taşıma başlatılıyor (${pluginInfo.message ?: ""})"))
                backoff[key] = nowMs() + 4_000L
                continue
            }
            val attemptIdx = (pendingAttempts[addr] ?: 0) + 1
            synchronized(pendingDial) { pendingAttempts[addr] = attemptIdx }
            val transportLabel = when (plugin.type) {
                TransportType.TOR -> "Tor"
                TransportType.LAN -> "LAN"
                TransportType.BLUETOOTH -> "Bluetooth"
                TransportType.REMOVABLE -> "Removable"
            }
            recordPending(DialAttempt(addr, nowMs(), DialAttempt.Status.TRYING, "$transportLabel üzerinden bağlanılıyor (deneme #$attemptIdx)…"))
            // Bağlantı denemesi çalışırken aynı adrese paralel deneme açılmasın
            backoff[key] = nowMs() + 180_000L
            scope.launch {
                val connResult = runCatching { plugin.connect(addr) }
                val conn = connResult.getOrNull()
                if (conn == null) {
                    val err = connResult.exceptionOrNull()?.message?.take(120) ?: "bağlanılamadı"
                    recordPending(DialAttempt(addr, nowMs(), DialAttempt.Status.CONNECT_FAIL, "$err — yeniden denenecek"))
                    backoff[key] = nowMs() + nextPendingBackoffMs(attemptIdx)
                    pendingWake.trySend(Unit)
                    return@launch
                }
                recordPending(DialAttempt(addr, nowMs(), DialAttempt.Status.CONNECT_OK, "handshake yapılıyor…"))
                val sessionConnected = runCatching { sync.handleConnection(owner, conn) }.getOrDefault(false)
                if (sessionConnected) {
                    recordPending(DialAttempt(addr, nowMs(), DialAttempt.Status.HANDSHAKE_OK, "bağlandı ✓"))
                    consumePendingAddress(addr)
                } else {
                    recordPending(DialAttempt(addr, nowMs(), DialAttempt.Status.HANDSHAKE_FAIL, "handshake başarısız — yeniden denenecek"))
                    backoff[key] = nowMs() + nextPendingBackoffMs(attemptIdx)
                    pendingWake.trySend(Unit)
                }
            }
            // Diğer adresleri de paralel dene; her birinin kendi backoff'u var.
        }
    }

    private fun nextPendingBackoffMs(attemptIdx: Int): Long = when {
        attemptIdx <= 2 -> 3_000L
        attemptIdx <= 4 -> 6_000L
        attemptIdx <= 7 -> 10_000L
        attemptIdx <= 12 -> 20_000L
        else -> 40_000L
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
                    val sessionConnected = sync.handleConnection(owner, connection)
                    if (sessionConnected) {
                        recordAttempt(contact.id, DialAttempt(addr, nowMs(), DialAttempt.Status.HANDSHAKE_OK, "bağlandı ✓"))
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
