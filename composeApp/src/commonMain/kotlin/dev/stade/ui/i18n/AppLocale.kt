package dev.stade.ui.i18n

import androidx.compose.runtime.State

enum class AppLocale(val code: String) {
    English("en"),
    Turkish("tr")
}

expect fun getLocalePreference(): State<AppLocale>
expect fun setLocalePreference(locale: AppLocale)
expect fun getSystemLocale(): AppLocale

fun localeToStrings(locale: AppLocale): AppStrings = when (locale) {
    AppLocale.English -> EnglishStrings
    AppLocale.Turkish -> TurkishStrings
}

