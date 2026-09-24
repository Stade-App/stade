package dev.stade.security

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import dev.stade.StadeApplication

private const val PREFS_NAME = "stade_security"
private const val KEY_LOCK_ON_SHUTDOWN = "lock_on_shutdown"

private val prefs get() = StadeApplication.instance
    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

private val state by lazy { mutableStateOf(prefs.getBoolean(KEY_LOCK_ON_SHUTDOWN, true)) }

actual val isLockOnShutdownSupported: Boolean = true

actual fun getLockOnShutdownEnabled(): State<Boolean> = state

actual fun setLockOnShutdownEnabled(value: Boolean) {
    state.value = value
    prefs.edit().putBoolean(KEY_LOCK_ON_SHUTDOWN, value).apply()
}
