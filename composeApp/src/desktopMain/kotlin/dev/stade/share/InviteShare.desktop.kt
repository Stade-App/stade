package dev.stade.share

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual object InviteShare {
    actual fun share(invite: String, ownerNickname: String): String {
        val strings = dev.stade.ui.i18n.I18n.current
        val safeNick = ownerNickname.filter { it.isLetterOrDigit() }.take(16).ifBlank { "stade" }
        val suggested = "stade-$safeNick.stadeid"
        return runCatching {
            val dialog = FileDialog(null as Frame?, strings.shareSaveDialogTitle, FileDialog.SAVE).apply {
                file = suggested
                setFilenameFilter { _, name -> name.endsWith(".stadeid", ignoreCase = true) }
            }
            dialog.isVisible = true
            val dir = dialog.directory
            val name = dialog.file ?: return@runCatching strings.shareCancelled
            val finalName = if (name.endsWith(".stadeid", ignoreCase = true)) name else "$name.stadeid"
            val target = if (dir != null) File(dir, finalName) else File(finalName)
            target.writeText(invite)
            strings.shareSaved(target.absolutePath)
        }.getOrElse { strings.shareWriteFailed(it.message ?: "") }
    }
}
