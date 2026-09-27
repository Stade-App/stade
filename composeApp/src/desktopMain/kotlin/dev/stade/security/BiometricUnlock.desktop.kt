package dev.stade.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual fun biometricAvailability(): BiometricAvailability = BiometricAvailability.Unsupported

actual fun isBiometricUnlockEnrolled(): Boolean = false

actual fun clearBiometricUnlock() {}

private object DesktopBiometricGate : BiometricGate {
    override val availability = BiometricAvailability.Unsupported
    override val enrolled = false

    override fun authenticate(
        title: String,
        subtitle: String,
        pinFallbackLabel: String,
        onOutcome: (BiometricOutcome) -> Unit
    ) = onOutcome(BiometricOutcome.Unavailable)

    override fun enable(
        pin: String,
        title: String,
        subtitle: String,
        cancelLabel: String,
        onOutcome: (BiometricOutcome) -> Unit
    ) = onOutcome(BiometricOutcome.Unavailable)

    override fun disable() {}
}

@Composable
actual fun rememberBiometricGate(): BiometricGate = remember { DesktopBiometricGate }
