package app.stade.group

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.stade.crypto.CryptoApi
import app.stade.crypto.Encoding
import app.stade.db.StadeDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class GroupManager(private val db: StadeDb, private val crypto: CryptoApi) {

    private val pendingJoins = mutableMapOf<String, PendingJoinData>()

    fun createGroup(ownerId: String, creatorStadeId: String, name: String): GroupInfo {
        val id = Encoding.toHex(crypto.randomBytes(16))
        val inviteToken = Encoding.toHex(crypto.randomBytes(16))
        val now = Clock.System.now().toEpochMilliseconds()
        db.stadeDbQueries.insertGroup(id, ownerId, name, inviteToken, now, creatorStadeId)
        return GroupInfo(id, ownerId, name, inviteToken, now, emptyList(), creatorStadeId)
    }

    fun getGroup(groupId: String): GroupInfo? =
        db.stadeDbQueries.selectGroup(groupId).executeAsOneOrNull()?.let {
            val members = db.stadeDbQueries.selectGroupMembers(it.id).executeAsList().map { m -> m.contactId }
            GroupInfo(it.id, it.ownerId, it.name, it.inviteToken, it.createdAt, members, it.creatorStadeId)
        }

    fun allGroups(ownerId: String): List<GroupInfo> {
        val rows = db.stadeDbQueries.selectGroups(ownerId).executeAsList()
        if (rows.isEmpty()) return emptyList()
        val membersByGroup = db.stadeDbQueries.selectAllGroupMembersForOwner(ownerId)
            .executeAsList()
            .groupBy({ it.groupId }, { it.contactId })
        return rows.map { row ->
            GroupInfo(row.id, row.ownerId, row.name, row.inviteToken, row.createdAt, membersByGroup[row.id].orEmpty(), row.creatorStadeId)
        }
    }

    fun observeGroups(ownerId: String): Flow<List<GroupInfo>> =
        db.stadeDbQueries.selectGroups(ownerId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                if (rows.isEmpty()) return@map emptyList()
                val membersByGroup = db.stadeDbQueries.selectAllGroupMembersForOwner(ownerId)
                    .executeAsList()
                    .groupBy({ it.groupId }, { it.contactId })
                rows.map { row ->
                    GroupInfo(row.id, row.ownerId, row.name, row.inviteToken, row.createdAt, membersByGroup[row.id].orEmpty(), row.creatorStadeId)
                }
            }

    fun addMember(groupId: String, contactId: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        db.stadeDbQueries.insertGroupMember(groupId, contactId, now)
    }

    fun getMembers(groupId: String): List<String> =
        db.stadeDbQueries.selectGroupMembers(groupId).executeAsList().map { it.contactId }

    fun deleteGroup(groupId: String) {
        db.stadeDbQueries.transaction {
            db.stadeDbQueries.deleteGroupMessages(groupId)
            db.stadeDbQueries.deleteGroupMembers(groupId)
            db.stadeDbQueries.deleteGroup(groupId)
        }
    }

    fun removeMember(groupId: String, contactId: String) {
        db.stadeDbQueries.deleteGroupMember(groupId, contactId)
    }

    fun observeMessages(groupId: String): Flow<List<GroupMessage>> =
        db.stadeDbQueries.selectGroupMessages(groupId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toGroupMessage() } }

    fun observeLastMessage(groupId: String): Flow<GroupMessage?> =
        db.stadeDbQueries.selectLastGroupMessage(groupId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toGroupMessage() }

    fun observeUnreadCount(groupId: String): Flow<Long> =
        db.stadeDbQueries.countGroupUnread(groupId)
            .asFlow()
            .mapToOne(Dispatchers.Default)

    fun lastMessage(groupId: String): GroupMessage? =
        db.stadeDbQueries.selectLastGroupMessage(groupId).executeAsOneOrNull()?.toGroupMessage()

    fun unreadCount(groupId: String): Long =
        db.stadeDbQueries.countGroupUnread(groupId).executeAsOne()

    fun markRead(groupId: String) {
        db.stadeDbQueries.markGroupRead(groupId)
    }

    fun saveOutgoing(messageId: String, groupId: String, ownerStadeId: String, body: String, timestamp: Long) {
        if (db.stadeDbQueries.groupMessageExists(messageId).executeAsOne() > 0L) return
        db.stadeDbQueries.insertGroupMessage(messageId, groupId, ownerStadeId, body, timestamp, 1L, 1L)
    }

    fun deleteGroupMessages(messageIds: Collection<String>) {
        if (messageIds.isEmpty()) return
        db.stadeDbQueries.transaction {
            messageIds.forEach { id ->
                db.stadeDbQueries.deleteGroupMessageById(id)
            }
        }
    }

    fun leaveGroupLocally(groupId: String) {
        db.stadeDbQueries.transaction {
            db.stadeDbQueries.deleteGroupMessages(groupId)
            db.stadeDbQueries.deleteGroupMembers(groupId)
            db.stadeDbQueries.deleteGroup(groupId)
        }
    }

    fun handleIncomingGroupMsg(contactId: String, messageId: String, rawBody: String, timestamp: Long): String? {
        val stripped = rawBody.removePrefix(GRP_MSG_PREFIX)
        val colonIdx = stripped.indexOf(':')
        if (colonIdx < 0) return null
        val groupId = stripped.substring(0, colonIdx)
        val rest = stripped.substring(colonIdx + 1)
        val newlineIdx = rest.indexOf('\n')
        val senderId = if (newlineIdx >= 0) rest.substring(0, newlineIdx) else rest
        val body = if (newlineIdx >= 0) rest.substring(newlineIdx + 1) else ""

        if (senderId != contactId) return null

        val group = db.stadeDbQueries.selectGroup(groupId).executeAsOneOrNull() ?: return null
        val isMember = db.stadeDbQueries.isGroupMember(groupId, contactId).executeAsOne() > 0L
        if (!isMember) return null

        if (db.stadeDbQueries.groupMessageExists(messageId).executeAsOne() > 0L) return groupId
        db.stadeDbQueries.insertGroupMessage(messageId, groupId, senderId, body, timestamp, 0L, 0L)
        return groupId
    }

    fun handleJoinRequest(contactId: String, rawBody: String): String? {
        val stripped = rawBody.removePrefix(GRP_JOIN_PREFIX)
        val colonIdx = stripped.indexOf(':')
        if (colonIdx < 0) return null
        val groupId = stripped.substring(0, colonIdx)
        val inviteToken = stripped.substring(colonIdx + 1)

        val group = db.stadeDbQueries.selectGroup(groupId).executeAsOneOrNull() ?: return null
        if (group.inviteToken != inviteToken) return null

        addMember(groupId, contactId)

        val members = getMembers(groupId)
        val memberLine = members.joinToString("\n")
        return "$GRP_WELCOME_PREFIX$groupId:${group.name}\n$memberLine"
    }

    fun handleGroupWelcome(ownerId: String, creatorStadeId: String, rawBody: String) {
        val stripped = rawBody.removePrefix(GRP_WELCOME_PREFIX)
        val colonIdx = stripped.indexOf(':')
        if (colonIdx < 0) return
        val groupId = stripped.substring(0, colonIdx)
        val rest = stripped.substring(colonIdx + 1)
        val newlineIdx = rest.indexOf('\n')
        val groupName = if (newlineIdx >= 0) rest.substring(0, newlineIdx) else rest
        val memberIds = if (newlineIdx >= 0) {
            rest.substring(newlineIdx + 1).split('\n').filter { it.isNotBlank() }
        } else emptyList()

        if (db.stadeDbQueries.selectGroup(groupId).executeAsOneOrNull() == null) {
            val now = Clock.System.now().toEpochMilliseconds()
            val newToken = Encoding.toHex(crypto.randomBytes(16))
            db.stadeDbQueries.insertGroup(groupId, ownerId, groupName, newToken, now, creatorStadeId)
        }
        memberIds.forEach { memberId ->
            runCatching { addMember(groupId, memberId) }
        }
    }

    fun storePendingJoin(creatorStadeId: String, data: PendingJoinData) {
        pendingJoins[creatorStadeId] = data
        val json = "${data.groupId}\u0001${data.groupName}\u0001${data.inviteToken}"
        db.stadeDbQueries.putKv("grp.pending.$creatorStadeId", json.encodeToByteArray())
    }

    fun getPendingJoinForContact(contactStadeId: String): PendingJoinData? {
        if (pendingJoins.containsKey(contactStadeId)) return pendingJoins[contactStadeId]
        val raw = runCatching {
            db.stadeDbQueries.getKv("grp.pending.$contactStadeId").executeAsOneOrNull()
        }.getOrNull() ?: return null
        val parts = raw.decodeToString().split("\u0001")
        if (parts.size < 3) return null
        val data = PendingJoinData(parts[0], parts[1], parts[2])
        pendingJoins[contactStadeId] = data
        return data
    }

    fun clearPendingJoin(contactStadeId: String) {
        pendingJoins.remove(contactStadeId)
        runCatching { db.stadeDbQueries.deleteKv("grp.pending.$contactStadeId") }
    }

    fun generateInviteLink(groupId: String, groupName: String, inviteToken: String, creatorStadeId: String): String {
        val data = "$groupId\u0000$groupName\u0000$inviteToken\u0000$creatorStadeId"
        return "$GROUP_INVITE_PREFIX${Encoding.toBase32(data.encodeToByteArray())}"
    }

    fun parseInviteLink(code: String): GroupInviteData? {
        val upper = code.trim().uppercase()
        if (!upper.startsWith(GROUP_INVITE_PREFIX.uppercase())) return null
        val b32 = upper.substring(GROUP_INVITE_PREFIX.length)
        val bytes = runCatching { Encoding.fromBase32(b32) }.getOrNull() ?: return null
        val parts = bytes.decodeToString().split("\u0000")
        if (parts.size < 4) return null
        return GroupInviteData(parts[0], parts[1], parts[2], parts[3])
    }

    fun groupsForContact(contactId: String): List<String> =
        db.stadeDbQueries.memberGroupIds(contactId).executeAsList()

    private fun app.stade.db.GroupMessage.toGroupMessage() = GroupMessage(
        id = id,
        groupId = groupId,
        senderId = senderId,
        body = body,
        timestamp = timestamp,
        isOwn = outgoing == 1L,
        isRead = read == 1L
    )
}

