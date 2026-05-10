package app.stade.sync

import app.stade.contact.Contact
import app.stade.contact.ContactManager
import app.stade.contact.HandshakeService
import app.stade.contact.InvitePayload
import app.stade.crypto.CryptoApi
import app.stade.crypto.PqCrypto
import app.stade.crypto.RatchetSessions
import app.stade.identity.LocalIdentity
import app.stade.identity.StadeId
import app.stade.message.MessageManager
import app.stade.transport.Connection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    private val pq: PqCrypto,
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

    /**
     * v2 PQXDH handshake.
     *
     * Akış:
     *   1. Her iki taraf HELLO gönderir/alır. Versiyon eşitliği zorunlu;
     *      transcriptCommitment downgrade saldırısını engeller.
     *   2. Karşılıklı Stade ID self-conflict kontrolü.
     *   3. AUTH: Ed25519 + ML-DSA hibrit imza (ikisi de geçmek zorunda).
     *   4. Yeni kişiyse: deterministik şekilde Alice = leksikografik küçük Stade ID.
     *      Alice ML-KEM encapsulate eder + KEM_OFFER gönderir.
     *      Bob KEM_OFFER alır + decapsulate eder.
     *      Her iki taraf rootKey hesaplar + Contact ekler.
     *   5. Mevcut kişiyse: KEM değiş-tokuşu yok; sadece adresler merge edilir.
     */
    private suspend fun handshake(owner: LocalIdentity, connection: Connection): Pair<Contact, Boolean>? {
        val ourNonce = crypto.randomBytes(32)
        val ourTc = transcriptCommitment(
            protocolVersion,
            owner.publicSigningKey,
            owner.publicHandshakeKey,
            owner.publicMlKemKey,
            owner.publicMlDsaKey
        )
        val ourHello = HelloPayload(
            protocolVersion = protocolVersion,
            stadeId = owner.stadeId,
            nickname = owner.nickname,
            signingPublicKey = owner.publicSigningKey,
            handshakePublicKey = owner.publicHandshakeKey,
            mlkemPublicKey = owner.publicMlKemKey,
            mldsaPublicKey = owner.publicMlDsaKey,
            nonce = ourNonce,
            transcriptCommitment = ourTc,
            addresses = runCatching { selfAddressesProvider() }.getOrDefault(emptyList())
        )
        runCatching {
            connection.send(FrameCodec.encode(SyncRecord(RecordType.HELLO, json.encodeToString(HelloPayload.serializer(), ourHello).encodeToByteArray())))
        }.getOrElse { return null }

        val helloFrame = withTimeoutOrNull(45_000) { connection.receive() } ?: return null
        val helloRecord = FrameCodec.decode(helloFrame) ?: return null
        if (helloRecord.type != RecordType.HELLO) return null
        val peerHello = runCatching {
            json.decodeFromString(HelloPayload.serializer(), helloRecord.payload.decodeToString())
        }.getOrNull() ?: return null

        // ── Versiyon ve format kontrolleri ─────────────────────────────────
        if (peerHello.protocolVersion != protocolVersion) {
            _events.tryEmit(SyncEvent.HandshakeRejected("Protokol uyumsuz: v${peerHello.protocolVersion} (uygulama v$protocolVersion)"))
            return null
        }
        if (peerHello.signingPublicKey.size != 32 ||
            peerHello.handshakePublicKey.size != 32 ||
            peerHello.mlkemPublicKey.size != 1184 ||
            peerHello.mldsaPublicKey.size != 1952
        ) {
            _events.tryEmit(SyncEvent.HandshakeRejected("Anahtar boyutları hatalı"))
            return null
        }
        if (peerHello.signingPublicKey.contentEquals(owner.publicSigningKey)) {
            _events.tryEmit(SyncEvent.HandshakeRejected("Kendine bağlandın (bayat adres)"))
            return null
        }

        // Stade ID public key'lerden türetilebilir olmalı (cüzdan tutarlılığı)
        val derivedPeerId = StadeId.derive(peerHello.signingPublicKey, peerHello.mldsaPublicKey, crypto::hash)
        if (derivedPeerId != peerHello.stadeId) {
            _events.tryEmit(SyncEvent.HandshakeRejected("Stade ID anahtarlarla eşleşmiyor"))
            return null
        }

        // Transcript commitment doğrulama
        val expectedPeerTc = transcriptCommitment(
            protocolVersion,
            peerHello.signingPublicKey,
            peerHello.handshakePublicKey,
            peerHello.mlkemPublicKey,
            peerHello.mldsaPublicKey
        )
        if (!expectedPeerTc.contentEquals(peerHello.transcriptCommitment)) {
            _events.tryEmit(SyncEvent.HandshakeRejected("Transcript commitment uyumsuz (downgrade?)"))
            return null
        }

        // ── AUTH alış-veriş (hibrit imza) ──────────────────────────────────
        val authMessage = AUTH_PREFIX + peerHello.nonce + ourTc + peerHello.transcriptCommitment
        val ourEdSig = crypto.sign(owner.privateSigningKey, authMessage)
        val ourDsaSig = pq.signMlDsa(owner.privateMlDsaKey, owner.publicMlDsaKey, authMessage)
        val ourAuth = AuthPayload(owner.stadeId, ourEdSig, ourDsaSig)
        runCatching {
            connection.send(FrameCodec.encode(SyncRecord(RecordType.AUTH, json.encodeToString(AuthPayload.serializer(), ourAuth).encodeToByteArray())))
        }.getOrElse { return null }

        val authFrame = withTimeoutOrNull(45_000) { connection.receive() } ?: return null
        val authRecord = FrameCodec.decode(authFrame) ?: return null
        if (authRecord.type != RecordType.AUTH) return null
        val peerAuth = runCatching {
            json.decodeFromString(AuthPayload.serializer(), authRecord.payload.decodeToString())
        }.getOrNull() ?: return null
        if (peerAuth.stadeId != peerHello.stadeId) {
            _events.tryEmit(SyncEvent.HandshakeRejected("AUTH Stade ID HELLO ile eşleşmiyor"))
            return null
        }

        val peerAuthMessage = AUTH_PREFIX + ourNonce + peerHello.transcriptCommitment + ourTc
        val edOk = crypto.verify(peerHello.signingPublicKey, peerAuthMessage, peerAuth.edSignature)
        val dsaOk = pq.verifyMlDsa(peerHello.mldsaPublicKey, peerAuthMessage, peerAuth.mldsaSignature)
        if (!edOk || !dsaOk) {
            _events.tryEmit(SyncEvent.HandshakeRejected(
                if (!edOk && !dsaOk) "İmzalar doğrulanamadı"
                else if (!edOk) "Ed25519 imzası geçersiz"
                else "ML-DSA imzası geçersiz (post-quantum doğrulama başarısız)"
            ))
            return null
        }

        // ── Mevcut veya yeni kişi ──────────────────────────────────────────
        val existing = contacts.findByStadeId(peerHello.stadeId)
            ?: contacts.findByPublicKey(peerHello.signingPublicKey)
        if (existing != null) {
            if (existing.ownerId != owner.id) return null
            if (peerHello.addresses.isNotEmpty()) {
                val merged = (existing.addresses + peerHello.addresses).distinct()
                if (merged != existing.addresses) {
                    runCatching { contacts.setAddresses(existing.id, merged) }
                }
            }
            return existing to false
        }

        // ── Yeni kişi: KEM değiş-tokuşu + root key türetimi ────────────────
        val invite = InvitePayload(
            stadeId = peerHello.stadeId,
            nickname = peerHello.nickname.ifBlank { "Bilinmeyen" },
            signingPublicKey = peerHello.signingPublicKey,
            handshakePublicKey = peerHello.handshakePublicKey,
            mlkemPublicKey = peerHello.mlkemPublicKey,
            mldsaPublicKey = peerHello.mldsaPublicKey,
            addresses = peerHello.addresses
        )
        val isAlice = handshakeService.isAlice(owner, invite)
        val kemCt: ByteArray
        val kemSs: ByteArray
        if (isAlice) {
            // Alice encapsulate eder ve gönderir
            val enc = handshakeService.encapsulateForPeer(invite)
            kemCt = enc.ciphertext
            kemSs = enc.sharedSecret
            val rec = SyncRecord(RecordType.KEM_OFFER,
                json.encodeToString(KemOfferPayload.serializer(), KemOfferPayload(kemCt)).encodeToByteArray())
            runCatching { connection.send(FrameCodec.encode(rec)) }.getOrElse { return null }
        } else {
            // Bob bekler
            val frame = withTimeoutOrNull(45_000) { connection.receive() } ?: return null
            val rec = FrameCodec.decode(frame) ?: return null
            if (rec.type != RecordType.KEM_OFFER) return null
            val offer = runCatching {
                json.decodeFromString(KemOfferPayload.serializer(), rec.payload.decodeToString())
            }.getOrNull() ?: return null
            kemCt = offer.ciphertext
            kemSs = runCatching { handshakeService.decapsulate(owner, kemCt) }.getOrNull() ?: run {
                _events.tryEmit(SyncEvent.HandshakeRejected("ML-KEM decapsulate başarısız"))
                return null
            }
        }

        val rootKey = runCatching {
            handshakeService.deriveRootKey(owner, invite, kemCt, kemSs)
        }.getOrNull() ?: return null

        val nickname = peerHello.nickname.ifBlank { "Kişi-${peerHello.stadeId.takeLast(4)}" }
        val newContact = runCatching {
            contacts.addFromHandshake(
                owner = owner,
                nickname = nickname,
                peerSigningKey = peerHello.signingPublicKey,
                peerHandshakeKey = peerHello.handshakePublicKey,
                peerMlKemKey = peerHello.mlkemPublicKey,
                peerMlDsaKey = peerHello.mldsaPublicKey,
                rootKey = rootKey,
                isAlice = isAlice,
                addresses = peerHello.addresses
            )
        }.getOrNull() ?: contacts.findByStadeId(peerHello.stadeId) ?: return null
        return newContact to true
    }

    private fun transcriptCommitment(
        proto: Int,
        edPub: ByteArray,
        xPub: ByteArray,
        kemPub: ByteArray,
        dsaPub: ByteArray
    ): ByteArray {
        val out = ByteArray(4)
        out[0] = ((proto ushr 24) and 0xff).toByte()
        out[1] = ((proto ushr 16) and 0xff).toByte()
        out[2] = ((proto ushr 8) and 0xff).toByte()
        out[3] = (proto and 0xff).toByte()
        return crypto.hash(TC_PREFIX + out + edPub + xPub + kemPub + dsaPub)
    }

    companion object {
        private val AUTH_PREFIX = "stade-auth-v2".encodeToByteArray()
        private val TC_PREFIX = "stade-tc-v2".encodeToByteArray()
    }

    private inner class ContactSession(
        private val parent: CoroutineScope,
        private val owner: LocalIdentity,
        private val contact: Contact,
        @Volatile private var connection: Connection
    ) {
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
            if (!drainOutbox(scope)) return
            outboxSignal.collect {
                if (!scope.isActive) return@collect
                if (!drainOutbox(scope)) return@collect
            }
        }

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
                    if (messages.exists(payload.messageId)) {
                        val ack = AckPayload(payload.messageId)
                        runCatching {
                            connection.send(FrameCodec.encode(SyncRecord(RecordType.ACK, json.encodeToString(AckPayload.serializer(), ack).encodeToByteArray())))
                        }
                        return
                    }
                    val plain = ratchet.open(owner, contact, payload.ratchetFrame)
                    if (plain == null) {
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

    suspend fun forgetContact(contactId: String) {
        sessionsLock.withLock {
            sessions.remove(contactId)?.let { runCatching { it.cancel() } }
        }
        updateConnectedSet()
        runCatching { ratchet.forget(contactId) }
    }
}
