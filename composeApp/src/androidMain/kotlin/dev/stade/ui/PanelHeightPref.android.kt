package dev.stade.ui

import android.content.Context
import dev.stade.StadeApplication

private const val PREFS_NAME = "stade_ui"
private const val KEY_PANEL_HEIGHT = "panel_height_dp"

private val prefs get() = StadeApplication.instance
    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

actual fun loadPanelHeightDp(): Int =
    runCatching { prefs.getInt(KEY_PANEL_HEIGHT, 0) }.getOrDefault(0)

actual fun savePanelHeightDp(value: Int) {
    runCatching { prefs.edit().putInt(KEY_PANEL_HEIGHT, value).apply() }
}
