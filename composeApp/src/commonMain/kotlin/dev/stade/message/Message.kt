package dev.stade.message

import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

enum class MessageDirection { IN, OUT }
enum class MessageType { TEXT, IMAGE, VOICE }
const val IMAGE_BODY_PREFIX = "STADE_IMG_V1:"
const val VOICE_BODY_PREFIX = "STADE_VOI_V1:"
const val REPLY_BODY_PREFIX = "STADE_RPL_V1:"

@Serializable
data class Message(
    val id: String,
    val contactId: String,
    val direction: MessageDirection,
    val body: String,
    val timestamp: Long,
    val delivered: Boolean,
    val read: Boolean
) {
    val replyToId: String?
        get() = parseReplyWrapper(body)?.first

    private val effectiveBody: String
        get() = parseReplyWrapper(body)?.second ?: body

    val displayBody: String
        get() = effectiveBody

    val type: MessageType
        get() = when {
            effectiveBody.startsWith(IMAGE_BODY_PREFIX) -> MessageType.IMAGE
            effectiveBody.startsWith(VOICE_BODY_PREFIX) -> MessageType.VOICE
            else -> MessageType.TEXT
        }

    @OptIn(ExperimentalEncodingApi::class)
    fun imageBytes(): ByteArray? =
        if (type == MessageType.IMAGE)
            runCatching { Base64.Default.decode(effectiveBody.removePrefix(IMAGE_BODY_PREFIX)) }.getOrNull()
        else null

    @OptIn(ExperimentalEncodingApi::class)
    fun voiceOpusBytes(): ByteArray? =
        if (type == MessageType.VOICE)
            runCatching {
                val raw = Base64.Default.decode(effectiveBody.removePrefix(VOICE_BODY_PREFIX))
                raw.copyOfRange(4, raw.size)
            }.getOrNull()
        else null

    @OptIn(ExperimentalEncodingApi::class)
    fun voiceDurationMs(): Int? =
        if (type == MessageType.VOICE)
            runCatching {
                val raw = Base64.Default.decode(effectiveBody.removePrefix(VOICE_BODY_PREFIX))
                ((raw[0].toInt() and 0xFF) shl 24) or ((raw[1].toInt() and 0xFF) shl 16) or
                    ((raw[2].toInt() and 0xFF) shl 8) or (raw[3].toInt() and 0xFF)
            }.getOrNull()
        else null
}

@OptIn(ExperimentalEncodingApi::class)
fun encodeImageBody(bytes: ByteArray): String =
    IMAGE_BODY_PREFIX + Base64.Default.encode(bytes)

@OptIn(ExperimentalEncodingApi::class)
fun encodeVoiceBody(opusBytes: ByteArray, durationMs: Int): String {
    val header = byteArrayOf(
        (durationMs ushr 24).toByte(),
        (durationMs ushr 16).toByte(),
        (durationMs ushr 8).toByte(),
        durationMs.toByte()
    )
    return VOICE_BODY_PREFIX + Base64.Default.encode(header + opusBytes)
}

/**
 * Wraps an already-encoded body (plain text, or an IMAGE/VOICE-prefixed body) with a
 * reply-to reference. The id is length-prefixed so the inner body can contain any bytes
 * (including another prefix) without delimiter collisions.
 */
fun encodeReplyBody(replyToId: String, innerBody: String): String =
    REPLY_BODY_PREFIX + replyToId.length.toString() + ":" + replyToId + innerBody

/** Returns (replyToId, innerBody) if [body] carries a reply wrapper, else null. */
fun parseReplyWrapper(body: String): Pair<String, String>? {
    if (!body.startsWith(REPLY_BODY_PREFIX)) return null
    val rest = body.substring(REPLY_BODY_PREFIX.length)
    val sep = rest.indexOf(':')
    if (sep < 0) return null
    val len = rest.substring(0, sep).toIntOrNull() ?: return null
    val afterSep = rest.substring(sep + 1)
    if (len < 0 || len > afterSep.length) return null
    val id = afterSep.substring(0, len)
    val inner = afterSep.substring(len)
    return id to inner
}
