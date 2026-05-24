package app.stade.share

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual object InviteShare {
    actual fun share(invite: String, ownerNickname: String): String {
        val safeNick = ownerNickname.filter { it.isLetterOrDigit() }.take(16).ifBlank { "stade" }
        val suggested = "stade-$safeNick.stadeid"
        return runCatching {
            val dialog = FileDialog(null as Frame?, "Davet dosyasını kaydet", FileDialog.SAVE).apply {
                file = suggested
                setFilenameFilter { _, name -> name.endsWith(".stadeid", ignoreCase = true) }
            }
            dialog.isVisible = true
            val dir = dialog.directory
            val name = dialog.file ?: return@runCatching "İptal edildi"
            val finalName = if (name.endsWith(".stadeid", ignoreCase = true)) name else "$name.stadeid"
            val target = if (dir != null) File(dir, finalName) else File(finalName)
            target.writeText(invite)
            "Davet dosyası kaydedildi: ${target.absolutePath}"
        }.getOrElse { "Dosya yazılamadı: ${it.message}" }
    }
}
