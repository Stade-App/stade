package dev.stade.radar

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.util.prefs.Preferences

private const val KEY_INTRO_SUPPRESSED = "radar_intro_suppressed"
private const val KEY_ANONYMOUS = "radar_anonymous"
private const val KEY_INVISIBLE = "radar_invisible"

private val javaPrefs = Preferences.userRoot().node("dev/stade")

private val _introSuppressed by lazy {
    mutableStateOf(javaPrefs.getBoolean(KEY_INTRO_SUPPRESSED, false))
}

private val _anonymous by lazy {
    mutableStateOf(javaPrefs.getBoolean(KEY_ANONYMOUS, false))
}

private val _invisible by lazy {
    mutableStateOf(javaPrefs.getBoolean(KEY_INVISIBLE, false))
}

private fun store(key: String, value: Boolean) {
    javaPrefs.putBoolean(key, value)
    runCatching { javaPrefs.flush() }
}

actual fun getRadarIntroSuppressed(): State<Boolean> = _introSuppressed

actual fun setRadarIntroSuppressed(value: Boolean) {
    _introSuppressed.value = value
    store(KEY_INTRO_SUPPRESSED, value)
}

actual fun getRadarAnonymous(): State<Boolean> = _anonymous

actual fun setRadarAnonymous(value: Boolean) {
    _anonymous.value = value
    store(KEY_ANONYMOUS, value)
}

actual fun getRadarInvisible(): State<Boolean> = _invisible

actual fun setRadarInvisible(value: Boolean) {
    _invisible.value = value
    store(KEY_INVISIBLE, value)
}
