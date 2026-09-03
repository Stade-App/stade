package dev.stade.radar

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import dev.stade.StadeApplication

private const val KEY_INTRO_SUPPRESSED = "radar_intro_suppressed"
private const val KEY_ANONYMOUS = "radar_anonymous"
private const val KEY_INVISIBLE = "radar_invisible"

private val prefs get() = StadeApplication.instance
    .getSharedPreferences("stade_ui", Context.MODE_PRIVATE)

private val _introSuppressed by lazy {
    mutableStateOf(prefs.getBoolean(KEY_INTRO_SUPPRESSED, false))
}

private val _anonymous by lazy {
    mutableStateOf(prefs.getBoolean(KEY_ANONYMOUS, false))
}

private val _invisible by lazy {
    mutableStateOf(prefs.getBoolean(KEY_INVISIBLE, false))
}

actual fun getRadarIntroSuppressed(): State<Boolean> = _introSuppressed

actual fun setRadarIntroSuppressed(value: Boolean) {
    _introSuppressed.value = value
    prefs.edit().putBoolean(KEY_INTRO_SUPPRESSED, value).apply()
}

actual fun getRadarAnonymous(): State<Boolean> = _anonymous

actual fun setRadarAnonymous(value: Boolean) {
    _anonymous.value = value
    prefs.edit().putBoolean(KEY_ANONYMOUS, value).apply()
}

actual fun getRadarInvisible(): State<Boolean> = _invisible

actual fun setRadarInvisible(value: Boolean) {
    _invisible.value = value
    prefs.edit().putBoolean(KEY_INVISIBLE, value).apply()
}
