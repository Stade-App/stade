package app.stade.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

expect val isDynamicColorSupported: Boolean

expect fun getDynamicColorEnabled(): State<Boolean>

expect fun setDynamicColorEnabled(value: Boolean)

@Composable
expect fun resolveDynamicColorScheme(dark: Boolean): ColorScheme?

