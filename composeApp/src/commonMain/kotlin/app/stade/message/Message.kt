package app.stade.message

import kotlinx.serialization.Serializable

enum class MessageDirection { IN, OUT }

@Serializable
data class Message(
    val id: String,
    val contactId: String,
    val direction: MessageDirection,
    val body: String,
    val timestamp: Long,
    val delivered: Boolean,
    val read: Boolean
)
