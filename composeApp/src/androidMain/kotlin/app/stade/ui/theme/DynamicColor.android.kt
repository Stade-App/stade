package app.stade.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import app.stade.StadeApplication

actual val isDynamicColorSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S  // API 31+

private val _dynamicColorEnabled by lazy {
    val prefs = StadeApplication.instance
        .getSharedPreferences("stade_ui", Context.MODE_PRIVATE)
    mutableStateOf(prefs.getBoolean("dynamic_color", true))
}

actual fun getDynamicColorEnabled(): State<Boolean> = _dynamicColorEnabled

actual fun setDynamicColorEnabled(value: Boolean) {
    _dynamicColorEnabled.value = value
    StadeApplication.instance
        .getSharedPreferences("stade_ui", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("dynamic_color", value)
        .apply()
}

@Composable
actual fun resolveDynamicColorScheme(dark: Boolean): ColorScheme? {
    val enabled = getDynamicColorEnabled()
    if (!enabled.value || !isDynamicColorSupported) return null
    val context = LocalContext.current
    return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

