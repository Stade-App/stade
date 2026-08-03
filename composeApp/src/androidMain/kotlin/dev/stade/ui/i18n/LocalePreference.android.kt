package dev.stade.ui.i18n

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import dev.stade.StadeApplication

actual fun getSystemLocale(): AppLocale =
    AppLocale.entries.firstOrNull { it.code == java.util.Locale.getDefault().language } ?: AppLocale.English

private val _localePreference by lazy {
    val prefs = StadeApplication.instance
        .getSharedPreferences("stade_ui", Context.MODE_PRIVATE)
    val stored = prefs.getString("locale", null)
    val resolved = stored?.let { code -> AppLocale.entries.firstOrNull { it.code == code } } ?: getSystemLocale()
    if (stored == null) {
        prefs.edit().putString("locale", resolved.code).apply()
    }
    mutableStateOf(resolved)
}

actual fun getLocalePreference(): State<AppLocale> = _localePreference

actual fun setLocalePreference(locale: AppLocale) {
    _localePreference.value = locale
    StadeApplication.instance
        .getSharedPreferences("stade_ui", Context.MODE_PRIVATE)
        .edit()
        .putString("locale", locale.code)
        .apply()
}

