package dev.stade.security

import androidx.compose.runtime.State

expect val isLockOnShutdownSupported: Boolean

expect fun getLockOnShutdownEnabled(): State<Boolean>

expect fun setLockOnShutdownEnabled(value: Boolean)
