package dev.stade.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.stade.StadeApplication
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEY_ALIAS = "stade.biometric.v1"
private const val KEYSTORE = "AndroidKeyStore"
private const val TRANSFORM = "AES/GCM/NoPadding"
private const val GCM_TAG_BITS = 128
private const val PREFS = "stade_biometric"
private const val KEY_IV = "iv"
private const val KEY_CT = "ct"

private const val STRONG = BiometricManager.Authenticators.BIOMETRIC_STRONG

private fun prefs() = StadeApplication.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

private fun existingKey(): SecretKey? = runCatching {
    keyStore().getKey(KEY_ALIAS, null) as? SecretKey
}.getOrNull()

private fun dropKey() {
    runCatching { keyStore().deleteEntry(KEY_ALIAS) }
}

private fun generateKey(strongBox: Boolean): SecretKey {
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
    val spec = KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setUserAuthenticationRequired(true)
        .setInvalidatedByBiometricEnrollment(true)
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            }
            if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setIsStrongBoxBacked(true)
            }
        }
        .build()
    generator.init(spec)
    return generator.generateKey()
}

private fun freshKey(): SecretKey {
    dropKey()
    return runCatching { generateKey(strongBox = true) }.getOrElse { generateKey(strongBox = false) }
}

private fun storedBlob(): Pair<ByteArray, ByteArray>? {
    val p = prefs()
    val iv = p.getString(KEY_IV, null) ?: return null
    val ct = p.getString(KEY_CT, null) ?: return null
    return runCatching {
        android.util.Base64.decode(iv, android.util.Base64.NO_WRAP) to
            android.util.Base64.decode(ct, android.util.Base64.NO_WRAP)
    }.getOrNull()
}

private fun storeBlob(iv: ByteArray, ct: ByteArray) {
    prefs().edit()
        .putString(KEY_IV, android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP))
        .putString(KEY_CT, android.util.Base64.encodeToString(ct, android.util.Base64.NO_WRAP))
        .apply()
}

actual fun biometricAvailability(): BiometricAvailability {
    val manager = BiometricManager.from(StadeApplication.instance)
    return when (manager.canAuthenticate(STRONG)) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Ready
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NotEnrolled
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.NotEnrolled
        else -> BiometricAvailability.Unsupported
    }
}

actual fun isBiometricUnlockEnrolled(): Boolean = storedBlob() != null

actual fun clearBiometricUnlock() {
    dropKey()
    prefs().edit().remove(KEY_IV).remove(KEY_CT).apply()
}

private class AndroidBiometricGate(private val activity: FragmentActivity?) : BiometricGate {

    override val availability: BiometricAvailability get() = biometricAvailability()

    override val enrolled: Boolean get() = isBiometricUnlockEnrolled()

    private fun prompt(
        title: String,
        subtitle: String,
        negative: String,
        cipher: Cipher,
        onError: (Int, CharSequence) -> Unit,
        onSuccess: (Cipher) -> Unit
    ) {
        val host = activity ?: return onError(-1, "")
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negative)
            .setAllowedAuthenticators(STRONG)
            .setConfirmationRequired(false)
            .build()
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(code: Int, message: CharSequence) {
                onError(code, message)
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authorized = result.cryptoObject?.cipher
                if (authorized == null) {
                    onError(-1, "")
                    return
                }
                onSuccess(authorized)
            }
        }
        val executor = ContextCompat.getMainExecutor(host)
        BiometricPrompt(host, executor, callback)
            .authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun fellBack(code: Int): Boolean =
        code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
            code == BiometricPrompt.ERROR_USER_CANCELED ||
            code == BiometricPrompt.ERROR_CANCELED

    override fun authenticate(
        title: String,
        subtitle: String,
        pinFallbackLabel: String,
        onOutcome: (BiometricOutcome) -> Unit
    ) {
        if (activity == null || availability != BiometricAvailability.Ready) {
            onOutcome(BiometricOutcome.Unavailable)
            return
        }
        val blob = storedBlob() ?: return onOutcome(BiometricOutcome.Unavailable)
        val key = existingKey()
        if (key == null) {
            clearBiometricUnlock()
            onOutcome(BiometricOutcome.Reset)
            return
        }
        val cipher = try {
            Cipher.getInstance(TRANSFORM).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, blob.first))
            }
        } catch (_: KeyPermanentlyInvalidatedException) {
            clearBiometricUnlock()
            onOutcome(BiometricOutcome.Reset)
            return
        } catch (t: Throwable) {
            onOutcome(BiometricOutcome.Failed(t.message))
            return
        }
        prompt(
            title = title,
            subtitle = subtitle,
            negative = pinFallbackLabel,
            cipher = cipher,
            onError = { code, message ->
                if (fellBack(code)) onOutcome(BiometricOutcome.FellBackToPin)
                else onOutcome(BiometricOutcome.Failed(message.toString().ifBlank { null }))
            },
            onSuccess = { authorized ->
                val pin = runCatching { authorized.doFinal(blob.second).decodeToString() }.getOrNull()
                if (pin.isNullOrEmpty()) {
                    clearBiometricUnlock()
                    onOutcome(BiometricOutcome.Reset)
                } else {
                    onOutcome(BiometricOutcome.Unlocked(pin))
                }
            }
        )
    }

    override fun enable(
        pin: String,
        title: String,
        subtitle: String,
        cancelLabel: String,
        onOutcome: (BiometricOutcome) -> Unit
    ) {
        if (activity == null || availability != BiometricAvailability.Ready) {
            onOutcome(BiometricOutcome.Unavailable)
            return
        }
        val cipher = try {
            Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, freshKey()) }
        } catch (t: Throwable) {
            onOutcome(BiometricOutcome.Failed(t.message))
            return
        }
        prompt(
            title = title,
            subtitle = subtitle,
            negative = cancelLabel,
            cipher = cipher,
            onError = { code, message ->
                clearBiometricUnlock()
                if (fellBack(code)) onOutcome(BiometricOutcome.FellBackToPin)
                else onOutcome(BiometricOutcome.Failed(message.toString().ifBlank { null }))
            },
            onSuccess = { authorized ->
                val sealed = runCatching { authorized.doFinal(pin.encodeToByteArray()) }.getOrNull()
                val iv = authorized.iv
                if (sealed == null || iv == null) {
                    clearBiometricUnlock()
                    onOutcome(BiometricOutcome.Failed(null))
                } else {
                    storeBlob(iv, sealed)
                    onOutcome(BiometricOutcome.Unlocked(pin))
                }
            }
        )
    }

    override fun disable() = clearBiometricUnlock()
}

@Composable
actual fun rememberBiometricGate(): BiometricGate {
    val context = LocalContext.current
    return remember(context) { AndroidBiometricGate(context as? FragmentActivity) }
}
