package dev.stade.security

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

private val state = mutableStateOf(true)

actual val isLockOnShutdownSupported: Boolean = false

actual fun getLockOnShutdownEnabled(): State<Boolean> = state

actual fun setLockOnShutdownEnabled(value: Boolean) {
    state.value = value
}
