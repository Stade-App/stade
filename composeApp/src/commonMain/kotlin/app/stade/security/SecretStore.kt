package app.stade.security

import app.stade.crypto.CryptoApi
import app.stade.db.StadeDb

class SecretStore(
    private val db: StadeDb,
    private val crypto: CryptoApi
) {
    private val passphraseKey = "passphrase.verifier"
    private val saltKey = "passphrase.salt"

    fun isLockEnabled(): Boolean =
        db.stadeDbQueries.getKv(passphraseKey).executeAsOneOrNull() != null

    fun setupPassphrase(passphrase: String) {
        val salt = crypto.randomBytes(16)
        val derived = derive(passphrase, salt)
        db.stadeDbQueries.putKv(saltKey, salt)
        db.stadeDbQueries.putKv(passphraseKey, derived)
    }

    fun verifyPassphrase(passphrase: String): Boolean {
        val salt = db.stadeDbQueries.getKv(saltKey).executeAsOneOrNull() ?: return false
        val expected = db.stadeDbQueries.getKv(passphraseKey).executeAsOneOrNull() ?: return false
        val derived = derive(passphrase, salt)
        return derived.contentEquals(expected)
    }

    fun clearPassphrase() {
        db.stadeDbQueries.deleteKv(passphraseKey)
        db.stadeDbQueries.deleteKv(saltKey)
    }

    fun setupPin(pin: String) = setupPassphrase(pin)
    fun verifyPin(pin: String): Boolean = verifyPassphrase(pin)
    fun clearPin() = clearPassphrase()

    private fun derive(passphrase: String, salt: ByteArray): ByteArray {
        var current = passphrase.encodeToByteArray() + salt
        repeat(20_000) { current = crypto.hash(current) }
        return current
    }
}
