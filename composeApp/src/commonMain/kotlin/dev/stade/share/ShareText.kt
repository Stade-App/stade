package dev.stade.share

expect val isShareSheetSupported: Boolean

expect suspend fun shareText(text: String, title: String)
