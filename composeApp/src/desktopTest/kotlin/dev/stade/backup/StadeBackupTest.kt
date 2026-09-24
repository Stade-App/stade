package dev.stade.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

private class Fixture {
    val root: File = File.createTempFile("stade-backup-fixture", "").let {
        it.delete()
        it.mkdirs()
        it
    }
    val targets = BackupTargets(
        vaultMetaPath = File(root, "stade.vault").absolutePath,
        encryptedDbPath = File(root, "stade.db.enc").absolutePath,
        plaintextDbPath = File(root, "stade.db").absolutePath,
        onionKeyPath = File(root, "tor/onion.key").absolutePath
    )

    val meta = Random(11).nextBytes(180)
    val db = Random(12).nextBytes(250_000)
    val onion = "ED25519-V3:9sLp0wQ==".encodeToByteArray()

    fun seed(withOnion: Boolean = true) {
        File(targets.vaultMetaPath).writeBytes(meta)
        File(targets.encryptedDbPath).writeBytes(db)
        File(targets.plaintextDbPath).writeBytes(Random(13).nextBytes(4096))
        if (withOnion) {
            File(targets.onionKeyPath).parentFile.mkdirs()
            File(targets.onionKeyPath).writeBytes(onion)
        }
    }

    fun clearInstall() {
        File(targets.vaultMetaPath).delete()
        File(targets.encryptedDbPath).delete()
        File(targets.plaintextDbPath).delete()
        File(targets.onionKeyPath).delete()
    }

    fun export(passphrase: String): ByteArray {
        val out = ByteArrayOutputStream()
        assertTrue(StadeBackup.export(out, passphrase.toCharArray(), targets), "export should succeed")
        return out.toByteArray()
    }

    fun restore(archive: ByteArray, passphrase: String): RestoreOutcome {
        val scratch = File.createTempFile("stade-restore", ".tmp").apply { deleteOnExit() }
        return StadeBackup.restore(
            ByteArrayInputStream(archive),
            passphrase.toCharArray(),
            targets,
            scratch
        )
    }
}

class StadeBackupTest {

    @Test
    fun anAccountSurvivesExportAndRestoreOntoAnEmptyInstall() {
        val f = Fixture()
        f.seed()
        val archive = f.export("a-long-enough-passphrase")
        f.clearInstall()

        val outcome = f.restore(archive, "a-long-enough-passphrase")
        if (outcome !is RestoreOutcome.Restored) fail("expected Restored, got $outcome")
        assertTrue(outcome.onionKeyIncluded)

        assertContentEquals(f.meta, File(f.targets.vaultMetaPath).readBytes())
        assertContentEquals(f.db, File(f.targets.encryptedDbPath).readBytes())
        assertContentEquals(f.onion, File(f.targets.onionKeyPath).readBytes())
    }

    @Test
    fun theStalePlaintextDatabaseIsRemovedOnRestore() {
        val f = Fixture()
        f.seed()
        val archive = f.export("a-long-enough-passphrase")
        File(f.targets.plaintextDbPath).writeBytes(Random(14).nextBytes(2048))

        f.restore(archive, "a-long-enough-passphrase")
        assertFalse(
            File(f.targets.plaintextDbPath).exists(),
            "a leftover plaintext db would shadow the restored ciphertext"
        )
    }

    @Test
    fun anAccountWithoutATorAddressStillRestores() {
        val f = Fixture()
        f.seed(withOnion = false)
        val archive = f.export("a-long-enough-passphrase")
        f.clearInstall()

        val outcome = f.restore(archive, "a-long-enough-passphrase")
        if (outcome !is RestoreOutcome.Restored) fail("expected Restored, got $outcome")
        assertFalse(outcome.onionKeyIncluded)
        assertContentEquals(f.db, File(f.targets.encryptedDbPath).readBytes())
    }

    @Test
    fun aWrongPassphraseLeavesTheExistingInstallUntouched() {
        val f = Fixture()
        f.seed()
        val archive = f.export("a-long-enough-passphrase")
        val replacement = Random(15).nextBytes(64)
        File(f.targets.encryptedDbPath).writeBytes(replacement)

        val outcome = f.restore(archive, "not-the-passphrase")
        assertEquals(RestoreOutcome.Failed(RestoreFailure.WrongPassphrase), outcome)
        assertContentEquals(
            replacement,
            File(f.targets.encryptedDbPath).readBytes(),
            "a failed restore must not overwrite the current account"
        )
    }

    @Test
    fun aForeignFileIsRejected() {
        val f = Fixture()
        f.seed()
        val outcome = f.restore("just some text".encodeToByteArray(), "a-long-enough-passphrase")
        assertEquals(RestoreOutcome.Failed(RestoreFailure.NotABackup), outcome)
    }

    @Test
    fun exportFailsWhenThereIsNothingToBackUp() {
        val f = Fixture()
        val out = ByteArrayOutputStream()
        assertFalse(StadeBackup.export(out, "a-long-enough-passphrase".toCharArray(), f.targets))
        assertEquals(0, out.size())
    }

    @Test
    fun aBackupMissingItsDatabaseIsRejected() {
        val f = Fixture()
        f.seed()
        val out = ByteArrayOutputStream()
        BackupArchive.write(
            out,
            "a-long-enough-passphrase".toCharArray(),
            listOf(BackupSource(StadeBackup.ENTRY_VAULT, f.meta.size.toLong()) { f.meta.inputStream() })
        )
        val outcome = f.restore(out.toByteArray(), "a-long-enough-passphrase")
        assertEquals(RestoreOutcome.Failed(RestoreFailure.Incomplete), outcome)
    }

    @Test
    fun noScratchFilesAreLeftBehind() {
        val f = Fixture()
        f.seed()
        val archive = f.export("a-long-enough-passphrase")
        f.restore(archive, "a-long-enough-passphrase")
        val leftovers = f.root.walkTopDown().filter { it.name.endsWith(".restore") }.toList()
        assertTrue(leftovers.isEmpty(), "staging files should be promoted or removed: $leftovers")
    }
}
