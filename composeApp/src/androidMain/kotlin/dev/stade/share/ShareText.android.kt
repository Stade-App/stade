package dev.stade.share

import android.content.Intent
import dev.stade.StadeApplication

actual val isShareSheetSupported: Boolean = true

actual suspend fun shareText(text: String, title: String) {
    val context = StadeApplication.instance
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(sendIntent, title).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
