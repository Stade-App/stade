package app.stade.group

data class GroupInfo(
    val id: String,
    val ownerId: String,
    val name: String,
    val inviteToken: String,
    val createdAt: Long,
    val memberIds: List<String> = emptyList()
)

data class GroupMessage(
    val id: String,
    val groupId: String,
    val senderId: String,
    val body: String,
    val timestamp: Long,
    val isOwn: Boolean,
    val isRead: Boolean
)

data class GroupInviteData(
    val groupId: String,
    val groupName: String,
    val inviteToken: String,
    val creatorStadeId: String
)

data class PendingJoinData(
    val groupId: String,
    val groupName: String,
    val inviteToken: String
)

const val GRP_MSG_PREFIX = "\u0002GRP1:"
const val GRP_JOIN_PREFIX = "\u0002GRPJ:"
const val GRP_WELCOME_PREFIX = "\u0002GRPW:"
// Otomatik gönderilen grup daveti (oluşturucudan davet edilen kişiye).
// Alıcı tarafta otomatik olarak importGroupInvite çağrılır ve bağlantı kurulduğunda join request atılır.
const val GRP_INV_PREFIX = "\u0002GRPI:"
const val GROUP_INVITE_PREFIX = "STADE-GRP:"

