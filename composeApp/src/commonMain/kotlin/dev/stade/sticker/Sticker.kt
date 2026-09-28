package dev.stade.sticker

data class Sticker(
    val id: String,
    val ownerId: String,
    val imageBytes: ByteArray,
    val createdAt: Long,
    val packId: String? = null
) {
    override fun equals(other: Any?): Boolean = other is Sticker && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

data class StickerPack(
    val id: String,
    val ownerId: String,
    val title: String,
    val author: String,
    val origin: String,
    val createdAt: Long
)
