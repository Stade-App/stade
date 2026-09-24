package dev.stade.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

private fun sourcesOf(vararg pairs: Pair<String, ByteArray>): List<BackupSource> =
    pairs.map { (name, bytes) ->
        BackupSource(name, bytes.size.toLong()) { ByteArrayInputStream(bytes) }
    }

private fun pack(passphrase: String, vararg pairs: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    BackupArchive.write(out, passphrase.toCharArray(), sourcesOf(*pairs))
    return out.toByteArray()
}

private fun scratch(): File =
    File.createTempFile("stade-backup-test", ".bin").apply { deleteOnExit() }

private fun unpack(archive: ByteArray, passphrase: String): BackupReadOutcome =
    BackupArchive.read(ByteArrayInputStream(archive), passphrase.toCharArray(), scratch())

private fun readEntry(outcome: BackupReadOutcome.Ok, name: String): ByteArray {
    val entry = outcome.entries.first { it.name == name }
    return outcome.payload.inputStream().use { stream ->
        var skipped = 0L
        while (skipped < entry.offset) skipped += stream.skip(entry.offset - skipped)
        stream.readNBytes(entry.length.toInt())
    }
}

class BackupArchiveTest {

    @Test
    fun aBackupRoundTripsEveryEntry() {
        val vault = Random(1).nextBytes(512)
        val db = Random(2).nextBytes(300_000)
        val onion = "ED25519-V3:abcdef".encodeToByteArray()

        val archive = pack("correct horse battery staple", "vault" to vault, "db" to db, "onion" to onion)
        val outcome = unpack(archive, "correct horse battery staple")
        if (outcome !is BackupReadOutcome.Ok) fail("expected Ok, got $outcome")

        assertEquals(listOf("vault", "db", "onion"), outcome.entries.map { it.name })
        assertContentEquals(vault, readEntry(outcome, "vault"))
        assertContentEquals(db, readEntry(outcome, "db"))
        assertContentEquals(onion, readEntry(outcome, "onion"))
    }

    @Test
    fun payloadsSpanningManyChunksSurvive() {
        val big = Random(3).nextBytes(BackupArchive.CHUNK_SIZE * 2 + 7777)
        val archive = pack("pw", "db" to big)
        val outcome = unpack(archive, "pw")
        if (outcome !is BackupReadOutcome.Ok) fail("expected Ok, got $outcome")
        assertContentEquals(big, readEntry(outcome, "db"))
    }

    @Test
    fun anEmptyEntryIsPreserved() {
        val archive = pack("pw", "vault" to ByteArray(0), "db" to byteArrayOf(9))
        val outcome = unpack(archive, "pw")
        if (outcome !is BackupReadOutcome.Ok) fail("expected Ok, got $outcome")
        assertEquals(0L, outcome.entries.first { it.name == "vault" }.length)
        assertContentEquals(byteArrayOf(9), readEntry(outcome, "db"))
    }

    @Test
    fun theWrongPassphraseIsRejected() {
        val archive = pack("right", "db" to Random(4).nextBytes(4096))
        assertEquals(BackupReadOutcome.WrongPassphrase, unpack(archive, "wrong"))
    }

    @Test
    fun aTruncatedArchiveIsRejected() {
        val archive = pack("pw", "db" to Random(5).nextBytes(200_000))
        val cut = archive.copyOfRange(0, archive.size - 64)
        assertEquals(BackupReadOutcome.Damaged, unpack(cut, "pw"))
    }

    @Test
    fun aFlippedCiphertextBitIsRejected() {
        val archive = pack("pw", "db" to Random(6).nextBytes(8192))
        val tampered = archive.copyOf()
        tampered[tampered.size - 32] = (tampered[tampered.size - 32].toInt() xor 0x01).toByte()
        assertEquals(BackupReadOutcome.WrongPassphrase, unpack(tampered, "pw"))
    }

    @Test
    fun aTamperedHeaderIsRejected() {
        val archive = pack("pw", "db" to Random(7).nextBytes(8192))
        val tampered = archive.copyOf()
        tampered[30] = (tampered[30].toInt() xor 0x7F).toByte()
        val outcome = unpack(tampered, "pw")
        assertTrue(
            outcome is BackupReadOutcome.WrongPassphrase ||
                outcome is BackupReadOutcome.Damaged ||
                outcome is BackupReadOutcome.NotABackup,
            "a modified header must never authenticate, got $outcome"
        )
    }

    @Test
    fun reorderedChunksAreRejected() {
        val archive = pack("pw", "db" to Random(8).nextBytes(BackupArchive.CHUNK_SIZE * 2))
        val headerLen = archive.size - (BackupArchive.CHUNK_SIZE + 16) * 2
        val sealedLen = BackupArchive.CHUNK_SIZE + 16
        val swapped = archive.copyOf()
        val first = archive.copyOfRange(headerLen, headerLen + sealedLen)
        val second = archive.copyOfRange(headerLen + sealedLen, headerLen + 2 * sealedLen)
        second.copyInto(swapped, headerLen)
        first.copyInto(swapped, headerLen + sealedLen)
        assertEquals(BackupReadOutcome.WrongPassphrase, unpack(swapped, "pw"))
    }

    @Test
    fun foreignFilesAreNotMistakenForBackups() {
        assertEquals(BackupReadOutcome.NotABackup, unpack(ByteArray(0), "pw"))
        assertEquals(BackupReadOutcome.NotABackup, unpack("hello world".encodeToByteArray(), "pw"))
        assertEquals(BackupReadOutcome.NotABackup, unpack(Random(9).nextBytes(200), "pw"))
    }

    @Test
    fun theArchiveIsNotPlaintext() {
        val secret = "ED25519-V3:THIS-IS-THE-ONION-KEY".encodeToByteArray()
        val archive = pack("pw", "onion" to secret)
        val haystack = archive.decodeToString()
        assertTrue(
            !haystack.contains("ED25519-V3"),
            "onion key material must not be readable in the archive"
        )
    }
}
