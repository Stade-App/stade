package dev.stade.media

fun normalizeRotation(degrees: Int): Int = ((degrees % 360) + 360) % 360

expect fun rotateImageBytes(bytes: ByteArray, degrees: Int): ByteArray
