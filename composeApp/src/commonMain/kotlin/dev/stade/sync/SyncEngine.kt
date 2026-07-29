package dev.stade.sync

import dev.stade.contact.Contact
import dev.stade.contact.ContactManager
import dev.stade.contact.HandshakeService
import dev.stade.contact.InvitePayload
import dev.stade.crypto.CryptoApi
import dev.stade.crypto.Encoding
import dev.stade.crypto.PqCrypto
import dev.stade.crypto.RatchetSessions
import dev.stade.group.GRP_INV_PREFIX
import dev.stade.group.GRP_JOIN_PREFIX
import dev.stade.group.GRP_KICK_PREFIX
import dev.stade.group.GRP_LEAVE_PREFIX
import dev.stade.group.GRP_MSG_PREFIX
import dev.stade.group.GRP_RXN_PREFIX
import dev.stade.group.GRP_WELCOME_PREFIX
import dev.stade.group.GroupManager
import dev.stade.identity.LocalIdentity
import dev.stade.identity.StadeId
import dev.stade.message.MessageManager
import dev.stade.message.REACTION_BODY_PREFIX
import dev.stade.message.parseReactionWrapper
import dev.stade.stadium.STD_JOIN_PREFIX
import dev.stade.stadium.STD_LEAVE_PREFIX
import dev.stade.stadium.STD_MSG_PREFIX
import dev.stade.stadium.STD_WELCOME_PREFIX
import dev.stade.stadium.StadiumManager
import dev.stade.transport.Connection
import dev.stade.ui.i18n.I18n
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
    private val handshakeService: HandshakeService,
    val groupManager: GroupManager? = null,
    val stadiumManager: StadiumManager? = null
) {
    private val protocolVersion = 3
    private val json = Json { ignoreUnknownKeys = true }
    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SyncEvent> = _events
    private val sessions = mutableMapOf<String, ContactSession>()
    private val sessionsLock = Mutex()
    private val _connected = MutableStateFlow<Set<String>>(emptySet())
    val connectedContacts: StateFlow<Set<String>> = _connected.asStateFlow()
    @Volatile var selfAddressesProvider: () -> List<String> = { emptyList() }
    @Volatile private var forgottenIds = emptySet<String>()

    sealed interface SyncEvent {
        data class ContactConnected(val contactId: String, val isNew: Boolean) : SyncEvent
        data class ContactDisconnected(val contactId: String) : SyncEvent
        data class MessageReceived(val contactId: String, val messageId: String) : SyncEvent
        data class GroupMessageReceived(val groupId: String) : SyncEvent
        data class GroupInviteReceived(val groupId: String, val groupName: String) : SyncEvent
        data class GroupMemberRemoved(val groupId: String) : SyncEvent
        data class RemovedFromGroup(val groupId: String, val groupName: String) : SyncEvent
        data class HandshakeRejected(val reason: String) : SyncEvent
        data class DecryptFailed(val contactId: String) : SyncEvent
        data class SendFailed(val contactId: String, val reason: String) : SyncEvent
        data class ReactionUpdated(val messageId: String) : SyncEvent
        data class StadiumMessageReceived(val stadiumId: String) : SyncEvent
        data class StadiumContactReleased(val contactId: String, val forget: Boolean) : SyncEvent
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

    suspend fun handleConnection(owner: LocalIdentity, connection: Connection, outbound: Boolean = false): Boolean {
        var sessionStarted = false
        coroutineScope {
            val handshakeOutcome = handshake(owner, connection)
            if (handshakeOutcome == null) {
                connection.close()
                return@coroutineScope
            }
            val (contact, isNew) = handshakeOutcome
            contacts.markSeen(contact.id, Clock.System.now().toEpochMilliseconds())
            val preferOutbound = owner.stadeId < contact.id
            if (outbound != preferOutbound) {
                val existing = sessionsLock.withLock { sessions[contact.id] }
                if (existing != null) {
                    runCatching { connection.close() }
                    return@coroutineScope
                }
            }
            val stale = sessionsLock.withLock { sessions[contact.id] }
            stale?.let { runCatching { it.cancelAndJoin() } }
            val session = sessionsLock.withLock {
                sessions[contact.id]?.let { runCatching { it.cancel() } }
                ContactSession(this@coroutineScope, owner, contact, connection).also {
                    sessions[contact.id] = it
                    _connected.value = sessions.keys.toSet()
                }
            }
            _events.tryEmit(SyncEvent.ContactConnected(contact.id, isNew))
            sessionStarted = true
            try {
                session.run()
            } finally {
                sessionsLock.withLock {
                    if (sessions[contact.id] === session) sessions.remove(contact.id)
                    _connected.value = sessions.keys.toSet()
                }
                _events.tryEmit(SyncEvent.ContactDisconnected(contact.id))
            }
        }
        return sessionStarted
    }

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

        if (peerHello.protocolVersion != protocolVersion) {
            _events.tryEmit(SyncEvent.HandshakeRejected(I18n.current.hsProtocolMismatch(peerHello.protocolVersion, protocolVersion)))
            return null
        }
        if (peerHello.signingPublicKey.size != 32 ||
            peerHello.handshakePublicKey.size != 32 ||
            peerHello.mlkemPublicKey.size != 1184 ||
            peerHello.mldsaPublicKey.size != 1952
        ) {
            _events.tryEmit(SyncEvent.HandshakeRejected(I18n.current.hsKeySizeBad))
            return null
        }
        if (peerHello.signingPublicKey.contentEquals(owner.publicSigningKey)) {
            _events.tryEmit(SyncEvent.HandshakeRejected(I18n.current.hsSelfConnected))
            return null
        }

        val derivedPeerId = StadeId.derive(peerHello.signingPublicKey, peerHello.mldsaPublicKey, crypto::hash)
        if (derivedPeerId != peerHello.stadeId) {
            _events.tryEmit(SyncEvent.HandshakeRejected(I18n.current.hsStadeIdMismatch))
            return null
        }
        if (peerHello.stadeId in forgottenIds) return null

        val expectedPeerTc = transcriptCommitment(
            protocolVersion,
            peerHello.signingPublicKey,
            peerHello.handshakePublicKey,
            peerHello.mlkemPublicKey,
            peerHello.mldsaPublicKey
        )
        if (!expectedPeerTc.contentEquals(peerHello.transcriptCommitment)) {
            _events.tryEmit(SyncEvent.HandshakeRejected(I18n.current.hsTranscriptMismatch))
            return null
        }

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
            _events.tryEmit(SyncEvent.HandshakeRejected(I18n.current.hsAuthStadeIdMismatch))
            return null
        }

        val peerAuthMessage = AUTH_PREFIX + ourNonce + peerHello.transcriptCommitment + ourTc
        val edOk = crypto.verify(peerHello.signingPublicKey, peerAuthMessage, peerAuth.edSignature)
        val dsaOk = pq.verifyMlDsa(peerHello.mldsaPublicKey, peerAuthMessage, peerAuth.mldsaSignature)
        if (!edOk || !dsaOk) {
            _events.tryEmit(SyncEvent.HandshakeRejected(
                if (!edOk && !dsaOk) I18n.current.hsSignaturesInvalid
                else if (!edOk) I18n.current.hsEdInvalid
                else I18n.current.hsMldsaInvalid
            ))
            return null
        }

        val existing = contacts.findByStadeId(peerHello.stadeId)
            ?: contacts.findByPublicKey(peerHello.signingPublicKey)
        if (existing != null) {
            if (existing.ownerId != owner.id) return null
            if (peerHello.addresses.isNotEmpty()) {
                val nonLan = (existing.addresses + peerHello.addresses).filter { !it.startsWith("lan://") }
                val currentLan = peerHello.addresses.filter { it.startsWith("lan://") }
                val merged = (nonLan + currentLan).distinct()
                if (merged != existing.addresses) {
                    runCatching { contacts.setAddresses(existing.id, merged) }
                }
            }
            return existing to false
        }

        val invite = InvitePayload(
            stadeId = peerHello.stadeId,
            nickname = peerHello.nickname.ifBlank { I18n.current.unknownNickname },
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
            val enc = handshakeService.encapsulateForPeer(invite)
            kemCt = enc.ciphertext
            kemSs = enc.sharedSecret
            val rec = SyncRecord(RecordType.KEM_OFFER,
                json.encodeToString(KemOfferPayload.serializer(), KemOfferPayload(kemCt)).encodeToByteArray())
            runCatching { connection.send(FrameCodec.encode(rec)) }.getOrElse { return null }
        } else {
            val frame = withTimeoutOrNull(45_000) { connection.receive() } ?: return null
            val rec = FrameCodec.decode(frame) ?: return null
            if (rec.type != RecordType.KEM_OFFER) return null
            val offer = runCatching {
                json.decodeFromString(KemOfferPayload.serializer(), rec.payload.decodeToString())
            }.getOrNull() ?: return null
            kemCt = offer.ciphertext
            kemSs = runCatching { handshakeService.decapsulate(owner, kemCt) }.getOrNull() ?: run {
                _events.tryEmit(SyncEvent.HandshakeRejected(I18n.current.hsMlkemDecapFailed))
                return null
            }
        }

        val rootKey = runCatching {
            handshakeService.deriveRootKey(owner, invite, kemCt, kemSs)
        }.getOrNull() ?: return null

        val nickname = peerHello.nickname.ifBlank { I18n.current.contactNameFallback(peerHello.stadeId.takeLast(4)) }
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

        suspend fun cancelAndJoin() {
            val job = rootJob
            if (job != null) {
                job.cancel()
                runCatching { job.join() }
            }
        }

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
                try {
                    handleRecord(record)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                }
            }
        }

        private suspend fun runPinger() {
            try {
                while (true) {
                    delay(30_000)
                    runCatching {
                        connection.send(FrameCodec.encode(SyncRecord(RecordType.PING, ByteArray(0))))
                    }.getOrElse {
                        runCatching { connection.close() }
                        return
                    }
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
                    if (messages.isEnvelopeProcessed(payload.messageId)) {
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
                    messages.markEnvelopeProcessed(payload.messageId, contact.id, Clock.System.now().toEpochMilliseconds())
                    val bodyStr = plain.decodeToString()

                    when {
                        bodyStr.startsWith(REACTION_BODY_PREFIX) -> {
                            val wrapper = if ((contacts.get(contact.id)?.kind ?: 0) == 0) parseReactionWrapper(bodyStr) else null
                            if (wrapper != null) {
                                if (wrapper.add) messages.upsertReaction(wrapper.targetMessageId, contact.id, wrapper.emoji)
                                else messages.deleteReaction(wrapper.targetMessageId, contact.id)
                                _events.tryEmit(SyncEvent.ReactionUpdated(wrapper.targetMessageId))
                            }
                        }
                        groupManager != null && bodyStr.startsWith(GRP_RXN_PREFIX) -> {
                            val stripped = bodyStr.removePrefix(GRP_RXN_PREFIX)
                            val colonIdx = stripped.indexOf(':')
                            if (colonIdx >= 0 && stripped.substring(0, colonIdx) in groupManager.groupsForContact(contact.id)) {
                                val wrapper = parseReactionWrapper(stripped.substring(colonIdx + 1))
                                if (wrapper != null) {
                                    if (wrapper.add) messages.upsertReaction(wrapper.targetMessageId, contact.id, wrapper.emoji)
                                    else messages.deleteReaction(wrapper.targetMessageId, contact.id)
                                    _events.tryEmit(SyncEvent.ReactionUpdated(wrapper.targetMessageId))
                                }
                            }
                        }
                        groupManager != null && bodyStr.startsWith(GRP_MSG_PREFIX) -> {
                            val groupId = groupManager.handleIncomingGroupMsg(contact.id, payload.messageId, bodyStr, payload.timestamp)
                            if (groupId != null) _events.tryEmit(SyncEvent.GroupMessageReceived(groupId))
                        }
                        groupManager != null && bodyStr.startsWith(GRP_JOIN_PREFIX) -> {
                            val welcomeBody = groupManager.handleJoinRequest(contact.id, bodyStr)
                            if (welcomeBody != null) {
                                runCatching {
                                    val msgId = Encoding.toHex(crypto.randomBytes(16))
                                    val ts = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                                    val sealed = ratchet.seal(owner, contact, welcomeBody.encodeToByteArray())
                                    val mp = MessagePayload(msgId, ts, sealed)
                                    val frame = json.encodeToString(MessagePayload.serializer(), mp).encodeToByteArray()
                                    outbox.enqueue(contact.id, msgId, frame)
                                    outboxSignal.tryEmit(Unit)
                                }
                                val joinedGroupId = bodyStr.removePrefix(GRP_JOIN_PREFIX).substringBefore(':')
                                val ts2 = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                                for (memberId in groupManager.getMembers(joinedGroupId)) {
                                    if (memberId == contact.id) continue
                                    val member = contacts.get(memberId) ?: continue
                                    if (member.ownerId != owner.id) continue
                                    val msgId2 = Encoding.toHex(crypto.randomBytes(16))
                                    runCatching { queueOutgoing(owner, member, msgId2, welcomeBody, ts2) }
                                }
                            }
                        }
                        groupManager != null && bodyStr.startsWith(GRP_WELCOME_PREFIX) -> {
                            groupManager.handleGroupWelcome(owner.id, contact.id, bodyStr)
                        }
                        groupManager != null && bodyStr.startsWith(GRP_INV_PREFIX) -> {
                            runCatching {
                                if ((contacts.get(contact.id)?.kind ?: 0) != 0) return@runCatching
                                val inviteCode = bodyStr.removePrefix(GRP_INV_PREFIX)
                                val parsed = groupManager.parseInviteLink(inviteCode) ?: return@runCatching
                                val pending = dev.stade.group.PendingJoinData(
                                    parsed.groupId, parsed.groupName, parsed.inviteToken
                                )
                                groupManager.storePendingJoin(parsed.creatorStadeId, pending)
                                val msgId2 = Encoding.toHex(crypto.randomBytes(16))
                                val ts2 = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                                val joinBody = "$GRP_JOIN_PREFIX${parsed.groupId}:${parsed.inviteToken}"
                                val sealed = ratchet.seal(owner, contact, joinBody.encodeToByteArray())
                                val mp = MessagePayload(msgId2, ts2, sealed)
                                val frame = json.encodeToString(MessagePayload.serializer(), mp).encodeToByteArray()
                                outbox.enqueue(contact.id, msgId2, frame)
                                outboxSignal.tryEmit(Unit)
                                _events.tryEmit(SyncEvent.GroupInviteReceived(parsed.groupId, parsed.groupName))
                            }
                        }
                        groupManager != null && bodyStr.startsWith(GRP_KICK_PREFIX) -> {
                            val outcome = groupManager.handleKick(contact.id, bodyStr, owner.stadeId)
                            if (outcome != null) {
                                _events.tryEmit(
                                    if (outcome.wasSelf) SyncEvent.RemovedFromGroup(outcome.groupId, outcome.groupName)
                                    else SyncEvent.GroupMemberRemoved(outcome.groupId)
                                )
                            }
                        }
                        groupManager != null && bodyStr.startsWith(GRP_LEAVE_PREFIX) -> {
                            val groupId = groupManager.handleMemberLeft(contact.id, bodyStr)
                            if (groupId != null) _events.tryEmit(SyncEvent.GroupMemberRemoved(groupId))
                        }
                        stadiumManager != null && bodyStr.startsWith(STD_JOIN_PREFIX) -> {
                            val welcomeBody = stadiumManager.handleJoinRequest(contact.id, bodyStr)
                            if (welcomeBody != null) {
                                runCatching {
                                    val msgId = Encoding.toHex(crypto.randomBytes(16))
                                    val ts = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                                    val sealed = ratchet.seal(owner, contact, welcomeBody.encodeToByteArray())
                                    val mp = MessagePayload(msgId, ts, sealed)
                                    val frame = json.encodeToString(MessagePayload.serializer(), mp).encodeToByteArray()
                                    outbox.enqueue(contact.id, msgId, frame)
                                    outboxSignal.tryEmit(Unit)
                                }
                                runCatching {
                                    val fresh = contacts.get(contact.id)
                                    if (fresh != null && fresh.kind == 0 &&
                                        messages.lastMessage(contact.id) == null &&
                                        (groupManager?.groupsForContact(contact.id) ?: emptyList()).isEmpty()
                                    ) contacts.setKind(contact.id, 1)
                                }
                            }
                        }
                        stadiumManager != null && bodyStr.startsWith(STD_WELCOME_PREFIX) -> {
                            stadiumManager.handleStadiumWelcome(owner.id, contact.id, bodyStr)
                        }
                        stadiumManager != null && bodyStr.startsWith(STD_LEAVE_PREFIX) -> {
                            val leftStadiumId = stadiumManager.handleLeaveRequest(contact.id, bodyStr)
                            if (leftStadiumId != null) {
                                val fresh = contacts.get(contact.id)
                                if (fresh != null && fresh.kind == 1 &&
                                    stadiumManager.stadiumsForContact(contact.id).isEmpty() &&
                                    stadiumManager.allStadiums(owner.id).none { it.creatorStadeId == contact.id } &&
                                    (groupManager?.groupsForContact(contact.id) ?: emptyList()).isEmpty() &&
                                    messages.lastMessage(contact.id) == null
                                ) {
                                    _events.tryEmit(SyncEvent.StadiumContactReleased(contact.id, forget = false))
                                }
                            }
                        }
                        stadiumManager != null && bodyStr.startsWith(STD_MSG_PREFIX) -> {
                            val stripped = bodyStr.removePrefix(STD_MSG_PREFIX)
                            val colonIdx = stripped.indexOf(':')
                            if (colonIdx >= 0) {
                                val stadiumId = stripped.substring(0, colonIdx)
                                val rest = stripped.substring(colonIdx + 1)
                                val newlineIdx = rest.indexOf('\n')
                                if (newlineIdx >= 0) {
                                    val memberCount = rest.substring(0, newlineIdx).toLongOrNull()
                                    val stadiumBody = rest.substring(newlineIdx + 1)
                                    val stadium = stadiumManager.getStadium(stadiumId)
                                    if (memberCount != null && stadium != null && !stadium.isOwner && stadium.creatorStadeId == contact.id) {
                                        val ok = stadiumManager.handleIncomingBroadcast(stadiumId, payload.messageId, memberCount, stadiumBody, payload.timestamp)
                                        if (ok) _events.tryEmit(SyncEvent.StadiumMessageReceived(stadiumId))
                                    }
                                }
                            }
                        }
                        else -> {
                            if ((contacts.get(contact.id)?.kind ?: 0) == 0) {
                                val saved = messages.saveIncoming(payload.messageId, contact.id, bodyStr, payload.timestamp)
                                if (saved != null) {
                                    _events.tryEmit(SyncEvent.MessageReceived(contact.id, payload.messageId))
                                    contacts.markSeen(contact.id, payload.timestamp)
                                }
                            }
                        }
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
                    messages.markDelivered(ack.messageId, contact.id)
                    outbox.removeForMessage(ack.messageId, contact.id)
                    if (stadiumManager != null && stadiumManager.takeFarewellIfMatches(contact.id, ack.messageId)) {
                        _events.tryEmit(SyncEvent.StadiumContactReleased(contact.id, forget = true))
                    }
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
            forgottenIds = forgottenIds + contactId
            sessions.remove(contactId)?.let { runCatching { it.cancel() } }
            _connected.value = sessions.keys.toSet()
        }
        runCatching { ratchet.forget(contactId) }
    }

    fun unforget(stadeId: String) {
        forgottenIds = forgottenIds - stadeId
    }

    suspend fun disconnectContact(contactId: String) {
        sessionsLock.withLock {
            sessions.remove(contactId)?.let { runCatching { it.cancel() } }
            _connected.value = sessions.keys.toSet()
        }
    }
}
