package dev.stade.message

import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

enum class MessageDirection { IN, OUT }
enum class MessageType { TEXT, IMAGE, VOICE }
const val IMAGE_BODY_PREFIX = "STADE_IMG_V1:"
const val VOICE_BODY_PREFIX = "STADE_VOI_V1:"

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
