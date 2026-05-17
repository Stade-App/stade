package app.stade.message

import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

enum class MessageDirection { IN, OUT }
enum class MessageType { TEXT, IMAGE }
const val IMAGE_BODY_PREFIX = "STADE_IMG_V1:"

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
        get() = if (body.startsWith(IMAGE_BODY_PREFIX)) MessageType.IMAGE else MessageType.TEXT

    @OptIn(ExperimentalEncodingApi::class)
    fun imageBytes(): ByteArray? =
        if (type == MessageType.IMAGE)
            runCatching { Base64.Default.decode(body.removePrefix(IMAGE_BODY_PREFIX)) }.getOrNull()
        else null
}

@OptIn(ExperimentalEncodingApi::class)
fun encodeImageBody(bytes: ByteArray): String =
    IMAGE_BODY_PREFIX + Base64.Default.encode(bytes)
