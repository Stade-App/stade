package dev.stade.ui.screens

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import dev.stade.StadeApplication

private val prefs get() = StadeApplication.instance
    .getSharedPreferences("stade_ui", Context.MODE_PRIVATE)

private val _stadeyVisible by lazy {
    mutableStateOf(prefs.getBoolean("stadey_visible", true))
}

actual fun getStadeyVisible(): State<Boolean> = _stadeyVisible

actual fun setStadeyVisible(value: Boolean) {
    _stadeyVisible.value = value
    prefs.edit().putBoolean("stadey_visible", value).apply()
}
