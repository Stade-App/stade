package dev.stade.ui.screens

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.util.prefs.Preferences

private val javaPrefs = Preferences.userRoot().node("dev/stade")

private val _stadeyVisible by lazy {
    mutableStateOf(javaPrefs.getBoolean("stadey_visible", true))
}

actual fun getStadeyVisible(): State<Boolean> = _stadeyVisible

actual fun setStadeyVisible(value: Boolean) {
    _stadeyVisible.value = value
    javaPrefs.putBoolean("stadey_visible", value)
    runCatching { javaPrefs.flush() }
}
