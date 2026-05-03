package app.stade.ui

import androidx.compose.runtime.Composable

/**
 * Platform bazlı geri butonu handler.
 * Android'de sistem geri butonunu yakalar, Desktop'ta no-op.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

