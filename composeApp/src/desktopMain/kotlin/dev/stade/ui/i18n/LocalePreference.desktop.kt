package dev.stade.ui.i18n

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.util.prefs.Preferences

private val javaPrefs = Preferences.userRoot().node("dev/stade")

actual fun getSystemLocale(): AppLocale =
    AppLocale.entries.firstOrNull { it.code == java.util.Locale.getDefault().language } ?: AppLocale.English

private val _localePreference by lazy {
    val stored = javaPrefs.get("locale", null)
    val resolved = stored?.let { code -> AppLocale.entries.firstOrNull { it.code == code } } ?: getSystemLocale()
    if (stored == null) {
        javaPrefs.put("locale", resolved.code)
        runCatching { javaPrefs.flush() }
    }
    mutableStateOf(resolved)
}

actual fun getLocalePreference(): State<AppLocale> = _localePreference

actual fun setLocalePreference(locale: AppLocale) {
    _localePreference.value = locale
    javaPrefs.put("locale", locale.code)
    runCatching { javaPrefs.flush() }
}

