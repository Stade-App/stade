package dev.stade.security

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileVaultTest {

    @Test
    fun flushAndCloseEncryptsThenRemovesPlaintextDatabase() {
        val root = Files.createTempDirectory("stade-vault-test")
        try {
            val vault = FileVault(root.toFile())
            vault.setup("1234")
            val plaintext = root.resolve("stade.db").toFile()
            plaintext.writeText("SQLite format 3\u0000test data")

            vault.flushAndClose()

            assertFalse(plaintext.exists())
            assertTrue(root.resolve("stade.db.enc").toFile().exists())
            assertFalse(vault.isUnlocked())
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
