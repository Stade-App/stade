package dev.stade.security

import androidx.compose.runtime.Composable

enum class BiometricAvailability { Unsupported, NotEnrolled, Ready }

sealed interface BiometricOutcome {
    data class Unlocked(val pin: String) : BiometricOutcome
    data object FellBackToPin : BiometricOutcome
    data object Unavailable : BiometricOutcome
    data object Reset : BiometricOutcome
    data class Failed(val message: String?) : BiometricOutcome
}

interface BiometricGate {
    val availability: BiometricAvailability
    val enrolled: Boolean

    fun authenticate(
        title: String,
        subtitle: String,
        pinFallbackLabel: String,
        onOutcome: (BiometricOutcome) -> Unit
    )

    fun enable(
        pin: String,
        title: String,
        subtitle: String,
        cancelLabel: String,
        onOutcome: (BiometricOutcome) -> Unit
    )

    fun disable()
}

expect fun biometricAvailability(): BiometricAvailability

expect fun isBiometricUnlockEnrolled(): Boolean

expect fun clearBiometricUnlock()

@Composable
expect fun rememberBiometricGate(): BiometricGate

val biometricSupported: Boolean
    get() = biometricAvailability() != BiometricAvailability.Unsupported

fun biometricUnlockReady(): Boolean =
    isBiometricUnlockEnrolled() && biometricAvailability() == BiometricAvailability.Ready
