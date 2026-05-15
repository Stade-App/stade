package app.stade.ui.i18n

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import app.stade.StadeApplication

private val _localePreference by lazy {
    val prefs = StadeApplication.instance
        .getSharedPreferences("stade_ui", Context.MODE_PRIVATE)
    val code = prefs.getString("locale", AppLocale.English.code) ?: AppLocale.English.code
    mutableStateOf(AppLocale.entries.firstOrNull { it.code == code } ?: AppLocale.English)
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

