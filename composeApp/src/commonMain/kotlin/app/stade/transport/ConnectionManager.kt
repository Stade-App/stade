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

/** Bir adrese yapılan son deneme sonucu. */
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
    /** Şu anda bağlantı kurma/handshake aşamasında olan kişi id'leri. Paralel dial'ı engeller. */
    private val dialing = mutableSetOf<String>()

    /** contactId -> (address -> son deneme). UI tanı kartında gösterilir. */
    private val _diagnostics = MutableStateFlow<Map<String, Map<String, DialAttempt>>>(emptyMap())
    val diagnostics: StateFlow<Map<String, Map<String, DialAttempt>>> = _diagnostics.asStateFlow()

    private fun recordAttempt(contactId: String, attempt: DialAttempt) {
        val cur = _diagnostics.value
        val perAddr = (cur[contactId] ?: emptyMap()).toMutableMap()
        perAddr[attempt.address] = attempt
        _diagnostics.value = cur + (contactId to perAddr)
    }

    /** Bu cihazın eriştiğimiz tüm taşıma katmanlarındaki TÜM dış adresleri (örn. her ağ arabirimi için lan://ip:port, tor://onion:port). */
    fun selfAddresses(): List<String> =
        registry.all().flatMap { runCatching { it.selfAddresses() }.getOrDefault(emptyList()) }
            .filter { it.isNotBlank() }
            .distinct()

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
        dialing.clear()
        for (plugin in registry.all()) runCatching { plugin.stop() }
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private suspend fun dialerLoop(owner: LocalIdentity) {
        while (scope.isActive) {
            val now = nowMs()
            for (contact in contacts.all().filter { it.ownerId == owner.id }) {
                if (sync.isConnected(contact.id)) continue
                if (contact.id in dialing) continue  // bu kişi için handshake zaten devam ediyor
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
        // Kendi reachable adreslerimizi (lan://kendi-ip, tor://kendi-onion) hesapla — bu adreslere
        // dial yaparsak kendimize bağlanır, handshake başarısız olur.
        val selfSet = runCatching { selfAddresses().toSet() }.getOrDefault(emptySet())
        // 1) Kişi üzerinde kayıtlı adresleri (lan://, tor://) doğrudan deneyelim — internet üzerinden ulaşmanın tek yolu.
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
            // Handshake bitene (veya bağlantı kopana) kadar dialing setinde kalsın.
            scope.launch {
                try {
                    val before = sync.isConnected(contact.id)
                    sync.handleConnection(owner, connection)
                    val after = sync.isConnected(contact.id)
                    if (after) {
                        recordAttempt(contact.id, DialAttempt(addr, nowMs(), DialAttempt.Status.HANDSHAKE_OK, "bağlandı ✓"))
                    } else if (before) {
                        // zaten bağlıydı, bu deneme reddedildi
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
        // 2) Aynı LAN'daysak UDP keşfi ile bulunan tüm peer'leri dene.
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


