package dev.stade.media

actual val isBackgroundRemovalSupported: Boolean = false

actual suspend fun removeImageBackground(bytes: ByteArray): ByteArray? = null
