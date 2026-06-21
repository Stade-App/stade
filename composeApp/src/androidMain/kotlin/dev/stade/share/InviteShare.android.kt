package dev.stade.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.stade.StadeApplication
import java.io.File

actual object InviteShare {
    actual fun share(invite: String, ownerNickname: String): String {
        val strings = dev.stade.ui.i18n.I18n.current
        return runCatching {
            val ctx: Context = StadeApplication.instance
            val safeNick = ownerNickname.filter { it.isLetterOrDigit() }.take(16).ifBlank { "stade" }
            val dir = File(ctx.cacheDir, "invites").apply { mkdirs() }
            val file = File(dir, "stade-$safeNick.stadeid")
            file.writeText(invite)
            val uri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, strings.shareInviteSubject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, strings.shareChooserTitle)
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ctx.startActivity(chooser)
            strings.shareOpened("stade-$safeNick.stadeid")
        }.getOrElse { strings.shareFailed(it.message ?: "") }
    }
}
