package app.stade.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFF2A6F97)
private val BrandDark = Color(0xFF013A63)
private val Accent = Color(0xFF89C2D9)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    secondary = BrandDark,
    onSecondary = Color.White,
    tertiary = Accent
)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF002A3A),
    secondary = Brand,
    onSecondary = Color.White,
    tertiary = BrandDark
)

@Composable
fun StadeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
