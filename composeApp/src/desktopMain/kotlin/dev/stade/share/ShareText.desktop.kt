package dev.stade.share

actual val isShareSheetSupported: Boolean = false

actual suspend fun shareText(text: String, title: String) {
}
