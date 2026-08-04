package dev.stade.emoji

import org.jetbrains.compose.resources.DrawableResource

data class CustomEmoji(val key: String, val drawable: DrawableResource)

object CustomEmojiCatalog {
    val all: List<CustomEmoji> = emptyList()
}
