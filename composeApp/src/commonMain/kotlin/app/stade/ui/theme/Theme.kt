package app.stade.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Marka rengi: derin okyanus mavisi → akıcı camgöbeği ──
private val Primary     = Color(0xFF1E6091)
private val PrimaryDark = Color(0xFF0B3D62)
private val Accent      = Color(0xFF61A5C2)
private val AccentSoft  = Color(0xFFA9D6E5)
private val Success     = Color(0xFF22C55E)

private val LightColors = lightColorScheme(
    primary                 = Primary,
    onPrimary               = Color.White,
    primaryContainer        = Color(0xFFD7EAF8),
    onPrimaryContainer      = Color(0xFF062239),
    secondary               = PrimaryDark,
    onSecondary             = Color.White,
    secondaryContainer      = Color(0xFFC5E1F0),
    onSecondaryContainer    = Color(0xFF051E32),
    tertiary                = Accent,
    onTertiary              = Color.White,
    tertiaryContainer       = AccentSoft,
    onTertiaryContainer     = Color(0xFF052338),
    error                   = Color(0xFFB42318),
    onError                 = Color.White,
    errorContainer          = Color(0xFFFEE4E2),
    onErrorContainer        = Color(0xFF7A271A),
    background              = Color(0xFFF7F9FC),
    onBackground            = Color(0xFF101418),
    surface                 = Color(0xFFFFFFFF),
    onSurface               = Color(0xFF101418),
    surfaceVariant          = Color(0xFFE5ECF3),
    onSurfaceVariant        = Color(0xFF475467),
    surfaceContainerLowest  = Color(0xFFFFFFFF),
    surfaceContainerLow     = Color(0xFFF4F7FA),
    surfaceContainer        = Color(0xFFEEF2F7),
    surfaceContainerHigh    = Color(0xFFE7ECF2),
    surfaceContainerHighest = Color(0xFFDFE5ED),
    outline                 = Color(0xFFB6BFCC),
    outlineVariant          = Color(0xFFE0E5EC)
)

private val DarkColors = darkColorScheme(
    primary                 = AccentSoft,
    onPrimary               = Color(0xFF052338),
    primaryContainer        = Color(0xFF134A6F),
    onPrimaryContainer      = Color(0xFFD7EAF8),
    secondary               = Accent,
    onSecondary             = Color(0xFF052338),
    secondaryContainer      = Color(0xFF1B5E89),
    onSecondaryContainer    = Color(0xFFD7EAF8),
    tertiary                = Color(0xFF89C2D9),
    onTertiary              = Color(0xFF052338),
    tertiaryContainer       = Color(0xFF184361),
    onTertiaryContainer     = Color(0xFFD7EAF8),
    error                   = Color(0xFFFDA29B),
    onError                 = Color(0xFF7A271A),
    errorContainer          = Color(0xFF55160C),
    onErrorContainer        = Color(0xFFFEE4E2),
    background              = Color(0xFF0B0F14),
    onBackground            = Color(0xFFE6EAF0),
    surface                 = Color(0xFF11161D),
    onSurface               = Color(0xFFE6EAF0),
    surfaceVariant          = Color(0xFF1B2230),
    onSurfaceVariant        = Color(0xFFB6BFCC),
    surfaceContainerLowest  = Color(0xFF080B10),
    surfaceContainerLow     = Color(0xFF11161D),
    surfaceContainer        = Color(0xFF161C25),
    surfaceContainerHigh    = Color(0xFF1B2230),
    surfaceContainerHighest = Color(0xFF222A39),
    outline                 = Color(0xFF4C5566),
    outlineVariant          = Color(0xFF2A3142)
)

private val StadeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val StadeTypography = Typography(
    titleLarge   = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium  = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleSmall   = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    bodyLarge    = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium   = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall    = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelLarge   = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelMedium  = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall   = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
)

object StadeColors {
    val online: Color  = Success
    val offline: Color = Color(0xFF98A2B3)
}

@Composable
fun StadeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes      = StadeShapes,
        typography  = StadeTypography,
        content     = content
    )
}
