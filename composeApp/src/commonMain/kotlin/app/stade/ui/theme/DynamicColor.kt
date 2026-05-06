package app.stade.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/** Cihazın Material You / dinamik renk özelliğini destekleyip desteklemediği. */
expect val isDynamicColorSupported: Boolean

/** Kullanıcının dinamik renk tercihini reaktif olarak döner (OkuYaz). */
expect fun getDynamicColorEnabled(): State<Boolean>

/** Dinamik renk tercihini kalıcı olarak kaydeder. */
expect fun setDynamicColorEnabled(value: Boolean)

/**
 * Dinamik renk şemasını döner.
 * Desteklenmiyorsa veya kapalıysa `null` döner; tema varsayılan renkleri kullanır.
 */
@Composable
expect fun resolveDynamicColorScheme(dark: Boolean): ColorScheme?

