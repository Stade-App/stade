package dev.stade.stadium

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.stade.crypto.CryptoApi
import dev.stade.crypto.Encoding
import dev.stade.db.StadeDb
import dev.stade.message.SearchResult
import dev.stade.message.previewBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class StadiumManager(private val db: StadeDb, private val crypto: CryptoApi) {

    private val pendingJoins = mutableMapOf<String, PendingStadiumJoin>()

    fun createStadium(ownerId: String, creatorStadeId: String, name: String): StadiumInfo {
        val id = Encoding.toHex(crypto.randomBytes(16))
        val inviteToken = Encoding.toHex(crypto.randomBytes(16))
        val now = Clock.System.now().toEpochMilliseconds()
        db.stadeDbQueries.insertStadium(id, ownerId, name, creatorStadeId, 1L, inviteToken, 0L, now)
        return StadiumInfo(id, ownerId, name, creatorStadeId, true, inviteToken, 0L, now)
    }

    fun getStadium(stadiumId: String): StadiumInfo? =
        db.stadeDbQueries.selectStadium(stadiumId).executeAsOneOrNull()?.toDomain()

    fun allStadiums(ownerId: String): List<StadiumInfo> =
        db.stadeDbQueries.selectStadiums(ownerId).executeAsList().map { it.toDomain() }

    fun observeStadiums(ownerId: String): Flow<List<StadiumInfo>> =
        db.stadeDbQueries.selectStadiums(ownerId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    fun observeMessages(stadiumId: String): Flow<List<StadiumMessage>> =
        db.stadeDbQueries.selectStadiumMessages(stadiumId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toStadiumMessage() } }

    fun observeLastMessage(stadiumId: String): Flow<StadiumMessage?> =
        db.stadeDbQueries.selectLastStadiumMessage(stadiumId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toStadiumMessage() }

    fun lastMessage(stadiumId: String): StadiumMessage? =
        db.stadeDbQueries.selectLastStadiumMessage(stadiumId).executeAsOneOrNull()?.toStadiumMessage()

    fun getMemberContactIds(stadiumId: String): List<String> =
        db.stadeDbQueries.selectStadiumMembers(stadiumId).executeAsList().map { it.contactId }

    fun subscriberCount(stadiumId: String): Long =
        db.stadeDbQueries.countStadiumMembers(stadiumId).executeAsOne()

    fun observeSubscriberCount(stadiumId: String): Flow<Long> =
        db.stadeDbQueries.countStadiumMembers(stadiumId)
            .asFlow()
            .mapToOne(Dispatchers.Default)

    fun saveOutgoing(messageId: String, stadiumId: String, body: String, timestamp: Long) {
        if (db.stadeDbQueries.stadiumMessageExists(messageId).executeAsOne() > 0L) return
        db.stadeDbQueries.insertStadiumMessage(messageId, stadiumId, body, timestamp, 1L)
    }

    fun rename(stadiumId: String, name: String) {
        db.stadeDbQueries.renameStadium(name, stadiumId)
    }

    fun setMuted(stadiumId: String, muted: Boolean) {
        db.stadeDbQueries.setStadiumMuted(if (muted) 1L else 0L, stadiumId)
    }

    fun deleteStadium(stadiumId: String) {
        db.stadeDbQueries.transaction {
            db.stadeDbQueries.deleteStadiumMessages(stadiumId)
            db.stadeDbQueries.deleteStadiumMembers(stadiumId)
            db.stadeDbQueries.deleteStadium(stadiumId)
        }
    }

    fun leaveStadium(stadiumId: String) {
        db.stadeDbQueries.transaction {
            db.stadeDbQueries.deleteStadiumMessages(stadiumId)
            db.stadeDbQueries.deleteStadium(stadiumId)
        }
    }

    fun handleLeaveRequest(contactId: String, rawBody: String): String? {
        val stadiumId = rawBody.removePrefix(STD_LEAVE_PREFIX)
        val stadium = db.stadeDbQueries.selectStadium(stadiumId).executeAsOneOrNull() ?: return null
        if (stadium.isOwner != 1L) return null
        db.stadeDbQueries.deleteStadiumMember(stadiumId, contactId)
        val count = db.stadeDbQueries.countStadiumMembers(stadiumId).executeAsOne()
        db.stadeDbQueries.setStadiumMemberCount(count, stadiumId)
        return stadiumId
    }

    fun handleIncomingBroadcast(stadiumId: String, messageId: String, memberCount: Long, body: String, timestamp: Long): Boolean {
        val stadium = db.stadeDbQueries.selectStadium(stadiumId).executeAsOneOrNull() ?: return false
        if (stadium.isOwner == 1L) return false
        if (db.stadeDbQueries.stadiumMessageExists(messageId).executeAsOne() > 0L) return true
        db.stadeDbQueries.transaction {
            db.stadeDbQueries.insertStadiumMessage(messageId, stadiumId, body, timestamp, 0L)
            db.stadeDbQueries.setStadiumMemberCount(memberCount, stadiumId)
        }
        return true
    }

    fun handleJoinRequest(contactId: String, rawBody: String): String? {
        val stripped = rawBody.removePrefix(STD_JOIN_PREFIX)
        val colonIdx = stripped.indexOf(':')
        if (colonIdx < 0) return null
        val stadiumId = stripped.substring(0, colonIdx)
        val inviteToken = stripped.substring(colonIdx + 1)

        val stadium = db.stadeDbQueries.selectStadium(stadiumId).executeAsOneOrNull() ?: return null
        if (stadium.isOwner != 1L || stadium.inviteToken != inviteToken) return null

        val now = Clock.System.now().toEpochMilliseconds()
        db.stadeDbQueries.insertStadiumMember(stadiumId, contactId, now)
        val count = db.stadeDbQueries.countStadiumMembers(stadiumId).executeAsOne()
        db.stadeDbQueries.setStadiumMemberCount(count, stadiumId)

        return "$STD_WELCOME_PREFIX$stadiumId:$count:${stadium.name}"
    }

    fun handleStadiumWelcome(ownerId: String, creatorStadeId: String, rawBody: String) {
        val stripped = rawBody.removePrefix(STD_WELCOME_PREFIX)
        val firstColon = stripped.indexOf(':')
        if (firstColon < 0) return
        val stadiumId = stripped.substring(0, firstColon)
        val rest = stripped.substring(firstColon + 1)
        val secondColon = rest.indexOf(':')
        if (secondColon < 0) return
        val memberCount = rest.substring(0, secondColon).toLongOrNull() ?: return
        val name = rest.substring(secondColon + 1)

        val existing = db.stadeDbQueries.selectStadium(stadiumId).executeAsOneOrNull()
        if (existing == null) {
            val now = Clock.System.now().toEpochMilliseconds()
            val token = Encoding.toHex(crypto.randomBytes(16))
            db.stadeDbQueries.insertStadium(stadiumId, ownerId, name, creatorStadeId, 0L, token, memberCount, now)
        } else {
            db.stadeDbQueries.setStadiumMemberCount(memberCount, stadiumId)
        }
    }

    fun storePendingJoin(creatorStadeId: String, data: PendingStadiumJoin) {
        pendingJoins[creatorStadeId] = data
        val json = "${data.stadiumId}\u0001${data.stadiumName}\u0001${data.inviteToken}"
        db.stadeDbQueries.putKv("std.pending.$creatorStadeId", json.encodeToByteArray())
    }

    fun getPendingJoinForContact(contactStadeId: String): PendingStadiumJoin? {
        if (pendingJoins.containsKey(contactStadeId)) return pendingJoins[contactStadeId]
        val raw = runCatching {
            db.stadeDbQueries.getKv("std.pending.$contactStadeId").executeAsOneOrNull()
        }.getOrNull() ?: return null
        val parts = raw.decodeToString().split("\u0001")
        if (parts.size < 3) return null
        val data = PendingStadiumJoin(parts[0], parts[1], parts[2])
        pendingJoins[contactStadeId] = data
        return data
    }

    fun clearPendingJoin(contactStadeId: String) {
        pendingJoins.remove(contactStadeId)
        runCatching { db.stadeDbQueries.deleteKv("std.pending.$contactStadeId") }
    }

    fun buildInviteLink(handshakeInvite: String, stadium: StadiumInfo): String {
        val sep = Char(0x1f).toString()
        val data = "${stadium.id}$sep${stadium.name}$sep${stadium.inviteToken}$sep${stadium.creatorStadeId}"
        return "$handshakeInvite$STADIUM_INVITE_SUFFIX_MARKER${Encoding.toBase32(data.encodeToByteArray())}"
    }

    fun splitInviteLink(fullCode: String): Pair<String, String>? {
        val idx = fullCode.indexOf(STADIUM_INVITE_SUFFIX_MARKER)
        if (idx < 0) return null
        return fullCode.substring(0, idx) to fullCode.substring(idx + STADIUM_INVITE_SUFFIX_MARKER.length)
    }

    fun parseStadiumData(b32: String): StadiumInviteData? {
        val bytes = runCatching { Encoding.fromBase32(b32.trim()) }.getOrNull() ?: return null
        val sep = Char(0x1f).toString()
        val parts = bytes.decodeToString().split(sep)
        if (parts.size < 4) return null
        return StadiumInviteData(parts[0], parts[1], parts[2], parts[3])
    }

    fun searchMessages(ownerId: String, query: String, limit: Long = 50): List<SearchResult> =
        db.stadeDbQueries.searchStadiumMessages(ownerId, query, limit).executeAsList().map {
            SearchResult(it.id, it.stadiumId, false, it.stadiumName, previewBody(it.body, ""), it.timestamp, isStadium = true)
        }

    private fun dev.stade.db.Stadium.toDomain() = StadiumInfo(
        id = id,
        ownerId = ownerId,
        name = name,
        creatorStadeId = creatorStadeId,
        isOwner = isOwner == 1L,
        inviteToken = inviteToken,
        memberCount = memberCount,
        createdAt = createdAt,
        muted = muted == 1L
    )

    private fun dev.stade.db.StadiumMessage.toStadiumMessage() = StadiumMessage(
        id = id,
        stadiumId = stadiumId,
        body = body,
        timestamp = timestamp,
        isOwn = outgoing == 1L
    )
}
