package app.stade.security

import android.content.Context
import java.io.File

actual class VaultFactory(private val context: Context) {
    actual fun create(): Vault {
        val dbsDir = context.getDatabasePath("stade.db").parentFile
            ?: File(context.filesDir, "databases")
        if (!dbsDir.exists()) dbsDir.mkdirs()
        return FileVault(dbsDir)
    }
}

