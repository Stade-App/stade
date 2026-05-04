package app.stade.sync

import app.stade.contact.Contact
import app.stade.contact.ContactManager
import app.stade.contact.HandshakeService
import app.stade.contact.InvitePayload
import app.stade.crypto.CryptoApi
import app.stade.crypto.RatchetSessions
import app.stade.identity.LocalIdentity
import app.stade.message.MessageManager
import app.stade.transport.Connection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class SyncEngine(
    private val crypto: CryptoApi,
    private val contacts: ContactManager,
    private val messages: MessageManager,
    private val ratchet: RatchetSessions,
    private val outbox: Outbox,
    private val handshakeService: HandshakeService
) {
    private val protocolVersion = 2
    private val json = Json { ignoreUnknownKeys = true }
    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SyncEvent> = _events
    private val sessions = mutableMapOf<String, ContactSession>()
    private val sessionsLock = Mutex()
    private val _connected = MutableStateFlow<Set<String>>(emptySet())
    val connectedContacts: StateFlow<Set<String>> = _connected.asStateFlow()
    /** ConnectionManager kurulduğunda set edilir. Kendi reachable adreslerimizi peer'e yollamak için. */
    @Volatile var selfAddressesProvider: () -> List<String> = { emptyList() }

    sealed interface SyncEvent {
        data class ContactConnected(val contactId: String, val isNew: Boolean) : SyncEvent
        data class ContactDisconnected(val contactId: String) : SyncEvent
        data class MessageReceived(val contactId: String, val messageId: String) : SyncEvent
        data class HandshakeRejected(val reason: String) : SyncEvent
        data class DecryptFailed(val contactId: String) : SyncEvent
        data class SendFailed(val contactId: String, val reason: String) : SyncEvent
    }

    suspend fun queueOutgoing(owner: LocalIdentity, contact: Contact, messageId: String, body: String, timestamp: Long) {
        val sealed = try {
            ratchet.seal(owner, contact, body.encodeToByteArray())
        } catch (e: Throwable) {
            // Daha önce ChatService.send buradaki istisnayı runCatching ile sessizce yutuyordu.
            // Artık event olarak yayınla; UI snackbar'da göstersin ki kullanıcı sebebi görsün.
            _events.tryEmit(SyncEvent.SendFailed(contact.id, e.message ?: e::class.simpleName ?: "bilinmeyen hata"))
            return
        }
        val payload = MessagePayload(messageId, timestamp, sealed)
        val frame = json.encodeToString(MessagePayload.serializer(), payload).encodeToByteArray()
        outbox.enqueue(contact.id, messageId, frame)
        sessionsLock.withLock { sessions[contact.id] }?.notifyOutbox()
    }

    suspend fun handleConnection(owner: LocalIdentity, connection: Connection) {
        coroutineScope {
            val handshakeOutcome = handshake(owner, connection)
            if (handshakeOutcome == null) {
                connection.close()
                return@coroutineScope
            }
            val (contact, isNew) = handshakeOutcome
            contacts.markSeen(contact.id, Clock.System.now().toEpochMilliseconds())
            // Tek aktif bağlantı per kişi: eski oturumu iptal et.
            val session = sessionsLock.withLock {
                sessions[contact.id]?.let { existing ->
                    runCatching { existing.cancel() }
                }
                ContactSession(this, owner, contact, connection).also { sessions[contact.id] = it }
            }
            updateConnectedSet()
            _events.tryEmit(SyncEvent.ContactConnected(contact.id, isNew))
            try {
                session.run()
            } finally {
                sessionsLock.withLock {
                    if (sessions[contact.id] === session) sessions.remove(contact.id)
                }
                updateConnectedSet()
                _events.tryEmit(SyncEvent.ContactDisconnected(contact.id))
            }
        }
    }

    private fun updateConnectedSet() {
        _connected.value = sessions.keys.toSet()
    }

    /** Returns (contact, isNew) on success, null on failure. */
    private suspend fun handshake(owner: LocalIdentity, connection: Connection): Pair<Contact, Boolean>? {
        val ourNonce = crypto.randomBytes(32)
        val ourHello = HelloPayload(
            protocolVersion = protocolVersion,
            ownerId = owner.id,
            signingPublicKey = owner.publicSigningKey,
            nonce = ourNonce,
            handshakePublicKey = owner.publicHandshakeKey,
            nickname = owner.nickname,
            addresses = runCatching { selfAddressesProvider() }.getOrDefault(emptyList())
        )
        runCatching {
            connection.send(FrameCodec.encode(SyncRecord(RecordType.HELLO, json.encodeToString(HelloPayload.serializer(), ourHello).encodeToByteArray())))
        }.getOrElse { return null }

        // Tor onion bağlantılarında her RTT 5-10sn olabildiği için cömert davran.
        val helloFrame = withTimeoutOrNull(45_000) { connection.receive() } ?: return null
        val helloRecord = FrameCodec.decode(helloFrame) ?: return null
        if (helloRecord.type != RecordType.HELLO) return null
        val peerHello = runCatching {
            json.decodeFromString(HelloPayload.serializer(), helloRecord.payload.decodeToString())
        }.getOrNull() ?: return null
        if (peerHello.protocolVersion != protocolVersion) {
            _events.tryEmit(SyncEvent.HandshakeRejected("Protokol uyumsuz: v${peerHello.protocolVersion}"))
            return null
        }
        if (peerHello.signingPublicKey.contentEquals(owner.publicSigningKey)) {
            _events.tryEmit(SyncEvent.HandshakeRejected("Kendine bağlandın (bayat LAN adresi)"))
            return null
        }

        val ourSig = crypto.sign(owner.privateSigningKey, "stade-auth".encodeToByteArray() + peerHello.nonce)
        val ourAuth = AuthPayload(owner.id, ourSig)
        runCatching {
            connection.send(FrameCodec.encode(SyncRecord(RecordType.AUTH, json.encodeToString(AuthPayload.serializer(), ourAuth).encodeToByteArray())))
        }.getOrElse { return null }

        val authFrame = withTimeoutOrNull(45_000) { connection.receive() } ?: return null
        val authRecord = FrameCodec.decode(authFrame) ?: return null
        if (authRecord.type != RecordType.AUTH) return null
        val peerAuth = runCatching {
            json.decodeFromString(AuthPayload.serializer(), authRecord.payload.decodeToString())
        }.getOrNull() ?: return null
        if (peerAuth.ownerId != peerHello.ownerId) return null

        val authOk = crypto.verify(
            peerHello.signingPublicKey,
            "stade-auth".encodeToByteArray() + ourNonce,
            peerAuth.challengeSignature
        )
        if (!authOk) {
            _events.tryEmit(SyncEvent.HandshakeRejected("İmza doğrulanamadı"))
            return null
        }

        // Kişi bilinmiyorsa ve peer geçerli bir handshake pubkey sundu ise: otomatik ekle.
        val existing = contacts.findByPublicKey(peerHello.signingPublicKey)
        if (existing != null) {
            if (existing.ownerId != owner.id) return null
            // Peer'in adres setini güncelle (her bağlantıda taze adresleri öğrenelim).
            if (peerHello.addresses.isNotEmpty()) {
                val merged = (existing.addresses + peerHello.addresses).distinct()
                if (merged != existing.addresses) {
                    runCatching { contacts.setAddresses(existing.id, merged) }
                }
            }
            return existing to false
        }

        if (peerHello.handshakePublicKey.size != owner.publicHandshakeKey.size) {
            _events.tryEmit(SyncEvent.HandshakeRejected("Handshake anahtarı eksik"))
            return null
        }
        val invite = InvitePayload(
            ownerId = peerHello.ownerId,
            nickname = peerHello.nickname.ifBlank { "Bilinmeyen" },
            signingPublicKey = peerHello.signingPublicKey,
            handshakePublicKey = peerHello.handshakePublicKey,
            addresses = peerHello.addresses
        )
        val rootKey = runCatching { handshakeService.deriveRootKey(owner, invite) }.getOrNull() ?: return null
        val isAlice = handshakeService.isAlice(owner, invite)
        val nickname = peerHello.nickname.ifBlank { "Kişi-${peerHello.ownerId.take(6)}" }
        val newContact = runCatching {
            contacts.addFromHandshake(
                owner = owner,
                nickname = nickname,
                peerSigningKey = peerHello.signingPublicKey,
                peerHandshakeKey = peerHello.handshakePublicKey,
                rootKey = rootKey,
                isAlice = isAlice,
                addresses = peerHello.addresses
            )
        }.getOrNull() ?: contacts.findByPublicKey(peerHello.signingPublicKey) ?: return null
        return newContact to true
    }

    private inner class ContactSession(
        private val parent: CoroutineScope,
        private val owner: LocalIdentity,
        private val contact: Contact,
        @Volatile private var connection: Connection
    ) {
        // replay = 1 sayesinde, abone olmadan önce yapılan tryEmit() çağrıları kaybolmaz.
        // Bu, Tor handshake 30-60sn sürerken kuyruğa atılan mesajların ilk session
        // başladığında drain edilmesini garanti eder.
        private val outboxSignal = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 8)
        private var rootJob: Job? = null

        fun notifyOutbox() { outboxSignal.tryEmit(Unit) }

        fun cancel() { rootJob?.cancel() }

        suspend fun run() = coroutineScope {
            rootJob = coroutineContext[Job]
            val sender = launch { runSender(this@coroutineScope) }
            val receiver = launch { runReceiver(this@coroutineScope) }
            val pinger = launch { runPinger() }
            try {
                receiver.join()
            } finally {
                sender.cancel()
                pinger.cancel()
                runCatching { connection.close() }
            }
        }

        private suspend fun runSender(scope: CoroutineScope) {
            // İlk kuyruğu drain et (session başlamadan önce queueOutgoing edilmiş olabilir).
            if (!drainOutbox(scope)) return
            // Sonra notifyOutbox çağrılarını dinle.
            outboxSignal.collect {
                if (!scope.isActive) return@collect
                if (!drainOutbox(scope)) return@collect
            }
        }

        /** Outbox'ta bekleyen tüm öğeleri sırayla gönderir. false dönerse bağlantı koptu. */
        private suspend fun drainOutbox(scope: CoroutineScope): Boolean {
            val pending = runCatching { outbox.pending(contact.id) }.getOrNull() ?: return true
            for (item in pending) {
                if (!scope.isActive) return false
                val ok = runCatching {
                    connection.send(FrameCodec.encode(SyncRecord(RecordType.MESSAGE, item.payload)))
                }.isSuccess
                if (!ok) {
                    scope.cancel()
                    return false
                }
                outbox.bump(item.id)
            }
            return true
        }

        private suspend fun runReceiver(scope: CoroutineScope) {
            while (scope.isActive) {
                val frame = runCatching { connection.receive() }.getOrNull() ?: return
                val record = FrameCodec.decode(frame) ?: continue
                handleRecord(record)
            }
        }

        private suspend fun runPinger() {
            try {
                while (true) {
                    delay(30_000)
                    runCatching {
                        connection.send(FrameCodec.encode(SyncRecord(RecordType.PING, ByteArray(0))))
                    }.getOrElse { return }
                }
            } catch (_: CancellationException) {
            }
        }

        private suspend fun handleRecord(record: SyncRecord) {
            when (record.type) {
                RecordType.MESSAGE -> {
                    val payload = runCatching {
                        json.decodeFromString(MessagePayload.serializer(), record.payload.decodeToString())
                    }.getOrNull() ?: return
                    val plain = ratchet.open(owner, contact, payload.ratchetFrame)
                    if (plain == null) {
                        // Şifre çözülemedi — büyük ihtimalle taraflar arasında ratchet state
                        // senkronizasyonu bozulmuş (kişi yeniden eklendi, eski outbox vs.).
                        // Kullanıcıya bildir; ACK GÖNDERME (sender retry'a tabi tutsun).
                        _events.tryEmit(SyncEvent.DecryptFailed(contact.id))
                        return
                    }
                    val saved = messages.saveIncoming(payload.messageId, contact.id, plain.decodeToString(), payload.timestamp)
                    if (saved != null) {
                        _events.tryEmit(SyncEvent.MessageReceived(contact.id, payload.messageId))
                        contacts.markSeen(contact.id, payload.timestamp)
                    }
                    val ack = AckPayload(payload.messageId)
                    runCatching {
                        connection.send(FrameCodec.encode(SyncRecord(RecordType.ACK, json.encodeToString(AckPayload.serializer(), ack).encodeToByteArray())))
                    }
                }
                RecordType.ACK -> {
                    val ack = runCatching {
                        json.decodeFromString(AckPayload.serializer(), record.payload.decodeToString())
                    }.getOrNull() ?: return
                    messages.markDelivered(ack.messageId)
                }
                RecordType.PING -> { }
                RecordType.BYE -> { runCatching { connection.close() } }
                else -> { }
            }
        }
    }

    fun isConnected(contactId: String): Boolean = _connected.value.contains(contactId)

    /**
     * Kişiye ait aktif oturumu kapatır ve ratchet state'ini unutur.
     * Kişiyi DB'den silmeden önce çağrılmalı.
     */
    suspend fun forgetContact(contactId: String) {
        sessionsLock.withLock {
            sessions.remove(contactId)?.let { runCatching { it.cancel() } }
        }
        updateConnectedSet()
        runCatching { ratchet.forget(contactId) }
    }
}
