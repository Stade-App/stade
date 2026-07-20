package dev.stade.group

import dev.stade.message.IMAGE_BODY_PREFIX
import dev.stade.message.MessageType
import dev.stade.message.VOICE_BODY_PREFIX
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class GroupInfo(
    val id: String,
    val ownerId: String,
    val name: String,
    val inviteToken: String,
    val createdAt: Long,
    val memberIds: List<String> = emptyList(),
    val creatorStadeId: String = ""
)

data class GroupMessage(
    val id: String,
    val groupId: String,
    val senderId: String,
    val body: String,
    val timestamp: Long,
    val isOwn: Boolean,
    val isRead: Boolean
) {
    val type: MessageType
        get() = when {
            body.startsWith(IMAGE_BODY_PREFIX) -> MessageType.IMAGE
            body.startsWith(VOICE_BODY_PREFIX) -> MessageType.VOICE
            else -> MessageType.TEXT
        }

    @OptIn(ExperimentalEncodingApi::class)
    fun imageBytes(): ByteArray? =
        if (type == MessageType.IMAGE)
            runCatching { Base64.Default.decode(body.removePrefix(IMAGE_BODY_PREFIX)) }.getOrNull()
        else null

    @OptIn(ExperimentalEncodingApi::class)
    fun voiceOpusBytes(): ByteArray? =
        if (type == MessageType.VOICE)
            runCatching {
                val raw = Base64.Default.decode(body.removePrefix(VOICE_BODY_PREFIX))
                raw.copyOfRange(4, raw.size)
            }.getOrNull()
        else null

    @OptIn(ExperimentalEncodingApi::class)
    fun voiceDurationMs(): Int? =
        if (type == MessageType.VOICE)
            runCatching {
                val raw = Base64.Default.decode(body.removePrefix(VOICE_BODY_PREFIX))
                ((raw[0].toInt() and 0xFF) shl 24) or ((raw[1].toInt() and 0xFF) shl 16) or
                    ((raw[2].toInt() and 0xFF) shl 8) or (raw[3].toInt() and 0xFF)
            }.getOrNull()
        else null
}

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
const val GRP_INV_PREFIX = "\u0002GRPI:"
const val GROUP_INVITE_PREFIX = "STADE-GRP:"

