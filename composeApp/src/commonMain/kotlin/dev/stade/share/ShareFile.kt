package dev.stade.share

expect suspend fun shareFile(bytes: ByteArray, filename: String, mimeType: String, title: String): Boolean
