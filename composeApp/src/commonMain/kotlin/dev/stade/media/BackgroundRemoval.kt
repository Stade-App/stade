package dev.stade.media

expect val isBackgroundRemovalSupported: Boolean

expect suspend fun removeImageBackground(bytes: ByteArray): ByteArray?
