package dev.stade.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

actual val isDynamicColorSupported: Boolean = false

private val _disabled = mutableStateOf(false)

actual fun getDynamicColorEnabled(): State<Boolean> = _disabled

actual fun setDynamicColorEnabled(value: Boolean) { }

@Composable
actual fun resolveDynamicColorScheme(dark: Boolean): ColorScheme? = null

