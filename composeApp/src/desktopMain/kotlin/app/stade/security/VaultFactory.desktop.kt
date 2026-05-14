package app.stade.security

import java.io.File

actual class VaultFactory {
    actual fun create(): Vault {
        val home = System.getProperty("user.home")
        val dir = File(home, ".stade").apply { mkdirs() }
        return FileVault(dir)
    }
}

