package dev.stade.sticker

data class Sticker(
    val id: String,
    val ownerId: String,
    val imageBytes: ByteArray,
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean = other is Sticker && other.id == id
    override fun hashCode(): Int = id.hashCode()
}
