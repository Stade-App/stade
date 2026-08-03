package dev.stade.security

import java.io.File

actual fun clearTorIdentity() {
    val home = System.getProperty("user.home")
    val torDir = File(home, ".stade/tor")
    if (torDir.exists()) runCatching { torDir.deleteRecursively() }
}
