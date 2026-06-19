package dev.stade.ui.i18n

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.util.prefs.Preferences

private val javaPrefs = Preferences.userRoot().node("dev/stade")

private val _localePreference by lazy {
    val code = javaPrefs.get("locale", AppLocale.English.code)
    mutableStateOf(AppLocale.entries.firstOrNull { it.code == code } ?: AppLocale.English)
}

actual fun getLocalePreference(): State<AppLocale> = _localePreference

actual fun setLocalePreference(locale: AppLocale) {
    _localePreference.value = locale
    javaPrefs.put("locale", locale.code)
    runCatching { javaPrefs.flush() }
}

