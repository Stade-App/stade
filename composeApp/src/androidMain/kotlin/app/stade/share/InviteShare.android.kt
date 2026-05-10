package app.stade.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.stade.StadeApplication
import java.io.File

actual object InviteShare {
    actual fun share(invite: String, ownerNickname: String): String {
        return runCatching {
            val ctx: Context = StadeApplication.instance
            val safeNick = ownerNickname.filter { it.isLetterOrDigit() }.take(16).ifBlank { "stade" }
            val dir = File(ctx.cacheDir, "invites").apply { mkdirs() }
            val file = File(dir, "stade-invite-$safeNick.txt")
            file.writeText(invite)
            val uri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, invite)
                putExtra(Intent.EXTRA_SUBJECT, "Stade davet kodu")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Davet kodunu paylaş")
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ctx.startActivity(chooser)
            "Paylaşım açıldı"
        }.getOrElse { "Paylaşım açılamadı: ${it.message}" }
    }
}

