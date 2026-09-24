package dev.stade.backup

import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupSource(
    val name: String,
    val length: Long,
    val open: () -> InputStream
)

class BackupEntry(val name: String, val length: Long, val offset: Long)

sealed interface BackupReadOutcome {
    class Ok(val payload: File, val entries: List<BackupEntry>) : BackupReadOutcome
    data object WrongPassphrase : BackupReadOutcome
    data object Damaged : BackupReadOutcome
    data object NotABackup : BackupReadOutcome
}

object BackupArchive {

    private val MAGIC = byteArrayOf(
        'S'.code.toByte(), 'T'.code.toByte(), 'D'.code.toByte(),
        'B'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte()
    )
    private const val VERSION: Byte = 1
    private const val SALT_LEN = 16
    private const val PREFIX_LEN = 8
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val TAG_LEN = TAG_BITS / 8
    private const val HEADER_LEN = 6 + 1 + SALT_LEN + 4 + PREFIX_LEN + 8 + 4

    const val KDF_ITERS = 310_000
    const val CHUNK_SIZE = 1 shl 20
    const val MAX_PAYLOAD_BYTES = 4L * 1024 * 1024 * 1024
    const val MAX_NAME_LEN = 64

    private val rng = SecureRandom()

    fun payloadLengthOf(sources: List<BackupSource>): Long =
        sources.sumOf { 2L + it.name.encodeToByteArray().size + 8L + it.length }

    fun write(out: OutputStream, passphrase: CharArray, sources: List<BackupSource>) {
        val payloadLength = payloadLengthOf(sources)
        require(payloadLength <= MAX_PAYLOAD_BYTES) { "backup too large" }

        val salt = ByteArray(SALT_LEN).also { rng.nextBytes(it) }
        val prefix = ByteArray(PREFIX_LEN).also { rng.nextBytes(it) }
        val header = ByteBuffer.allocate(HEADER_LEN).apply {
            put(MAGIC)
            put(VERSION)
            put(salt)
            putInt(KDF_ITERS)
            put(prefix)
            putLong(payloadLength)
            putInt(CHUNK_SIZE)
        }.array()
        out.write(header)

        val key = deriveKey(passphrase, salt, KDF_ITERS)
        try {
            val payload = PayloadReader(sources)
            val buffer = ByteArray(CHUNK_SIZE)
            var remaining = payloadLength
            var index = 0
            while (remaining > 0) {
                val want = minOf(remaining, CHUNK_SIZE.toLong()).toInt()
                payload.readFully(buffer, want)
                val sealed = seal(key, prefix, index, header, buffer, want)
                out.write(sealed)
                remaining -= want
                index++
            }
            payload.close()
        } finally {
            key.fill(0)
        }
        out.flush()
    }

    fun read(input: InputStream, passphrase: CharArray, scratch: File): BackupReadOutcome {
        val header = ByteArray(HEADER_LEN)
        if (!readFully(input, header, HEADER_LEN)) return BackupReadOutcome.NotABackup
        val buf = ByteBuffer.wrap(header)
        val magic = ByteArray(6).also { buf.get(it) }
        if (!magic.contentEquals(MAGIC)) return BackupReadOutcome.NotABackup
        if (buf.get() != VERSION) return BackupReadOutcome.NotABackup
        val salt = ByteArray(SALT_LEN).also { buf.get(it) }
        val iterations = buf.int
        val prefix = ByteArray(PREFIX_LEN).also { buf.get(it) }
        val payloadLength = buf.long
        val chunkSize = buf.int
        if (iterations !in 1..2_000_000) return BackupReadOutcome.NotABackup
        if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_BYTES) return BackupReadOutcome.NotABackup
        if (chunkSize !in 1..(1 shl 26)) return BackupReadOutcome.NotABackup

        val key = deriveKey(passphrase, salt, iterations)
        val cipherBuf = ByteArray(chunkSize + TAG_LEN)
        var authFailed = false
        try {
            scratch.outputStream().use { sink ->
                var remaining = payloadLength
                var index = 0
                while (remaining > 0) {
                    val want = minOf(remaining, chunkSize.toLong()).toInt()
                    val sealedLen = want + TAG_LEN
                    if (!readFully(input, cipherBuf, sealedLen)) return BackupReadOutcome.Damaged
                    val plain = try {
                        open(key, prefix, index, header, cipherBuf, sealedLen)
                    } catch (_: AEADBadTagException) {
                        authFailed = true
                        return@use
                    }
                    sink.write(plain)
                    remaining -= want
                    index++
                }
            }
        } catch (_: Exception) {
            return BackupReadOutcome.Damaged
        } finally {
            key.fill(0)
        }
        if (authFailed) {
            runCatching { scratch.delete() }
            return BackupReadOutcome.WrongPassphrase
        }

        val entries = runCatching { indexEntries(scratch, payloadLength) }.getOrNull()
            ?: run {
                runCatching { scratch.delete() }
                return BackupReadOutcome.Damaged
            }
        return BackupReadOutcome.Ok(scratch, entries)
    }

    private fun indexEntries(payload: File, payloadLength: Long): List<BackupEntry> {
        val entries = mutableListOf<BackupEntry>()
        DataInputStream(payload.inputStream().buffered()).use { stream ->
            var offset = 0L
            while (offset < payloadLength) {
                val nameLen = stream.readUnsignedShort()
                require(nameLen in 1..MAX_NAME_LEN) { "bad entry name" }
                val nameBytes = ByteArray(nameLen)
                stream.readFully(nameBytes)
                val length = stream.readLong()
                require(length >= 0 && length <= payloadLength) { "bad entry length" }
                offset += 2L + nameLen + 8L
                entries.add(BackupEntry(nameBytes.decodeToString(), length, offset))
                var skipped = 0L
                while (skipped < length) {
                    val n = stream.skip(length - skipped)
                    if (n <= 0) throw EOFException("truncated entry")
                    skipped += n
                }
                offset += length
            }
            require(offset == payloadLength) { "payload length mismatch" }
        }
        return entries
    }

    private class PayloadReader(sources: List<BackupSource>) {
        private val queue = ArrayDeque(sources)
        private var current: InputStream? = null
        private var pending: ByteArray = ByteArray(0)
        private var pendingPos = 0
        private var currentRemaining = 0L

        fun readFully(target: ByteArray, count: Int) {
            var written = 0
            while (written < count) {
                if (pendingPos < pending.size) {
                    val n = minOf(count - written, pending.size - pendingPos)
                    pending.copyInto(target, written, pendingPos, pendingPos + n)
                    pendingPos += n
                    written += n
                    continue
                }
                val stream = current
                if (stream != null && currentRemaining > 0) {
                    val n = stream.read(target, written, minOf(count - written, currentRemaining.toInt()))
                    if (n <= 0) throw EOFException("backup source ended early")
                    currentRemaining -= n
                    written += n
                    continue
                }
                if (stream != null) {
                    stream.close()
                    current = null
                }
                val next = queue.removeFirstOrNull() ?: throw EOFException("backup sources exhausted")
                val nameBytes = next.name.encodeToByteArray()
                pending = ByteBuffer.allocate(2 + nameBytes.size + 8).apply {
                    putShort(nameBytes.size.toShort())
                    put(nameBytes)
                    putLong(next.length)
                }.array()
                pendingPos = 0
                current = next.open()
                currentRemaining = next.length
            }
        }

        fun close() {
            runCatching { current?.close() }
            current = null
        }
    }

    private fun seal(
        key: ByteArray,
        prefix: ByteArray,
        index: Int,
        header: ByteArray,
        plaintext: ByteArray,
        length: Int
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce(prefix, index))
        )
        cipher.updateAAD(aad(header, index))
        return cipher.doFinal(plaintext, 0, length)
    }

    private fun open(
        key: ByteArray,
        prefix: ByteArray,
        index: Int,
        header: ByteArray,
        ciphertext: ByteArray,
        length: Int
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce(prefix, index))
        )
        cipher.updateAAD(aad(header, index))
        return cipher.doFinal(ciphertext, 0, length)
    }

    private fun nonce(prefix: ByteArray, index: Int): ByteArray =
        ByteBuffer.allocate(12).put(prefix).putInt(index).array()

    private fun aad(header: ByteArray, index: Int): ByteArray =
        ByteBuffer.allocate(header.size + 4).put(header).putInt(index).array()

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val encoded = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return encoded
    }

    private fun readFully(input: InputStream, target: ByteArray, count: Int): Boolean {
        var read = 0
        while (read < count) {
            val n = input.read(target, read, count - read)
            if (n < 0) return false
            read += n
        }
        return true
    }
}
