package dev.stade.ui.i18n

import androidx.compose.runtime.State
import androidx.compose.ui.unit.LayoutDirection

enum class AppLocale(val code: String, val rightToLeft: Boolean = false) {
    English("en"),
    Turkish("tr"),
    Korean("ko"),
    Persian("fa", rightToLeft = true),
    Arabic("ar", rightToLeft = true)
}

expect fun getLocalePreference(): State<AppLocale>
expect fun setLocalePreference(locale: AppLocale)
expect fun getSystemLocale(): AppLocale

fun localeToStrings(locale: AppLocale): AppStrings = when (locale) {
    AppLocale.English -> EnglishStrings
    AppLocale.Turkish -> TurkishStrings
    AppLocale.Korean -> KoreanStrings
    AppLocale.Persian -> PersianStrings
    AppLocale.Arabic -> ArabicStrings
}

fun localeToLayoutDirection(locale: AppLocale): LayoutDirection =
    if (locale.rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr

fun localeDisplayName(locale: AppLocale): String = when (locale) {
    AppLocale.English -> "English"
    AppLocale.Turkish -> "Türkçe"
    AppLocale.Korean -> "한국어"
    AppLocale.Persian -> "فارسی"
    AppLocale.Arabic -> "العربية"
}
