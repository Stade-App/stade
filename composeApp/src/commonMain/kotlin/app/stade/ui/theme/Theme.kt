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
    primaryContainer = Color(0xFFD4E9F7),
    onPrimaryContainer = Color(0xFF001A2B),
    secondary = BrandDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF90CEDE),
    onSecondaryContainer = Color(0xFF001B2C),
    tertiary = Accent,
    onTertiary = Color(0xFF001B2C),
    tertiaryContainer = Color(0xFFD4E9F7),
    onTertiaryContainer = Color(0xFF002A3A),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFEFBFF),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFEFBFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFEBE7F0),
    onSurfaceVariant = Color(0xFF49454E),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC7D0)
)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF002A3A),
    primaryContainer = Color(0xFF144A62),
    onPrimaryContainer = Color(0xFFD4E9F7),
    secondary = Brand,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1D5672),
    onSecondaryContainer = Color(0xFFD4E9F7),
    tertiary = BrandDark,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF1D5672),
    onTertiaryContainer = Color(0xFFD4E9F7),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E6),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E6),
    surfaceVariant = Color(0xFF49454E),
    onSurfaceVariant = Color(0xFFCAC7D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454E)
)

@Composable
fun StadeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
