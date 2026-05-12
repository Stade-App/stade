package app.stade.security

import app.stade.crypto.CryptoApi
import app.stade.db.StadeDb

class SecretStore(
    private val db: StadeDb,
    private val crypto: CryptoApi
) {
    private val passphraseKey = "passphrase.verifier"
    private val saltKey = "passphrase.salt"
    private val iterationsKey = "passphrase.iterations"

    // Yeni PIN kurulumlarında kullanılan iterasyon sayısı.
    // Eski PIN'ler (20_000) hâlâ doğrulanabilir; iterationsKey yoksa 20_000 varsayılır.
    private val currentIterations = 8_000

    fun isLockEnabled(): Boolean =
        db.stadeDbQueries.getKv(passphraseKey).executeAsOneOrNull() != null

    fun setupPassphrase(passphrase: String) {
        val salt = crypto.randomBytes(16)
        val derived = derive(passphrase, salt, currentIterations)
        db.stadeDbQueries.putKv(saltKey, salt)
        db.stadeDbQueries.putKv(passphraseKey, derived)
        db.stadeDbQueries.putKv(iterationsKey, currentIterations.toString().encodeToByteArray())
    }

    fun verifyPassphrase(passphrase: String): Boolean {
        val salt = db.stadeDbQueries.getKv(saltKey).executeAsOneOrNull() ?: return false
        val expected = db.stadeDbQueries.getKv(passphraseKey).executeAsOneOrNull() ?: return false
        val iters = db.stadeDbQueries.getKv(iterationsKey).executeAsOneOrNull()
            ?.let { runCatching { it.toString(Charsets.UTF_8).toInt() }.getOrNull() }
            ?: 20_000 // eski PIN'ler için geriye dönük uyum
        val derived = derive(passphrase, salt, iters)
        return derived.contentEquals(expected)
    }

    fun clearPassphrase() {
        db.stadeDbQueries.deleteKv(passphraseKey)
        db.stadeDbQueries.deleteKv(saltKey)
    }

    fun setupPin(pin: String) = setupPassphrase(pin)
    fun verifyPin(pin: String): Boolean = verifyPassphrase(pin)
    fun clearPin() = clearPassphrase()

    // ── Scramble keypad preference ──────────────────────────────────────────
    private val scrambleKeypadKey = "ui.scramble_keypad"

    fun isScrambleKeypadEnabled(): Boolean =
        db.stadeDbQueries.getKv(scrambleKeypadKey).executeAsOneOrNull()
            ?.let { it.isNotEmpty() && it[0] != 0.toByte() } ?: false

    fun setScrambleKeypadEnabled(enabled: Boolean) {
        db.stadeDbQueries.putKv(scrambleKeypadKey, byteArrayOf(if (enabled) 1 else 0))
    }

    private fun derive(passphrase: String, salt: ByteArray, iterations: Int): ByteArray {
        var current = passphrase.encodeToByteArray() + salt
        repeat(iterations) { current = crypto.hash(current) }
        return current
    }
}
