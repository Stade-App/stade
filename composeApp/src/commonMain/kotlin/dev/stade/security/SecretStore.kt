package dev.stade.security

import dev.stade.crypto.CryptoApi
import dev.stade.db.StadeDb

class SecretStore(
    private val db: StadeDb,
    private val crypto: CryptoApi,
    private val vault: Vault,
    private val onScreenshotSettingChanged: () -> Unit = {}
) {
    fun isLockEnabled(): Boolean = vault.isInitialized()

    fun verifyPassphrase(passphrase: String): Boolean {
        return when (vault.unlock(passphrase)) {
            is UnlockOutcome.Success -> true
            else -> false
        }
    }

    fun changePassphrase(current: String, new: String): Boolean =
        vault.changePassword(current, new)

    fun verifyPin(pin: String): Boolean = verifyPassphrase(pin)
    fun changePin(current: String, new: String): Boolean = changePassphrase(current, new)

    fun isScrambleKeypadEnabled(): Boolean = vault.isScrambleKeypadEnabled()

    fun setScrambleKeypadEnabled(enabled: Boolean) =
        vault.setScrambleKeypadEnabled(enabled)

    fun sessionTimeoutSeconds(): Int = vault.sessionTimeoutSeconds()

    fun setSessionTimeoutSeconds(value: Int) = vault.setSessionTimeoutSeconds(value)

    fun isScreenshotBlockingEnabled(): Boolean = vault.isScreenshotBlockingEnabled()

    fun setScreenshotBlockingEnabled(enabled: Boolean) {
        vault.setScreenshotBlockingEnabled(enabled)
        onScreenshotSettingChanged()
    }

    fun failedAttempts(): Int = vault.failedAttempts()

    fun lockoutUntilMillis(): Long = vault.lockoutUntilMillis()
}
