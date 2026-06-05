package app.stade.ui

expect suspend fun saveImageToGallery(bytes: ByteArray, suggestedName: String): Boolean
expect suspend fun copyImageToClipboard(bytes: ByteArray): Boolean

