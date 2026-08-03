package dev.stade.security

import dev.stade.StadeApplication
import java.io.File

actual fun clearTorIdentity() {
    val torDir = File(StadeApplication.instance.filesDir, "stade/tor")
    if (torDir.exists()) runCatching { torDir.deleteRecursively() }
}
