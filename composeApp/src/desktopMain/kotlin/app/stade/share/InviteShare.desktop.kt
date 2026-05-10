package app.stade.share

import java.awt.Desktop
import java.io.File

actual object InviteShare {
    actual fun share(invite: String, ownerNickname: String): String {
        return runCatching {
            val safeNick = ownerNickname.filter { it.isLetterOrDigit() }.take(16).ifBlank { "stade" }
            val home = System.getProperty("user.home") ?: "."
            val file = File(home, "stade-invite-$safeNick.txt")
            file.writeText(invite)
            runCatching {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(file.parentFile ?: file)
                }
            }
            "Davet kodu dosyaya kaydedildi: ${file.absolutePath}"
        }.getOrElse { "Dosya yazılamadı: ${it.message}" }
    }
}

