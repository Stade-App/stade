package dev.stade.share

import java.awt.Desktop
import java.net.URI

actual fun openExternalUri(uri: String): Boolean = runCatching {
    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        error("browse unsupported")
    }
    Desktop.getDesktop().browse(URI(uri))
}.isSuccess
