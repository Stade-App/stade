package dev.stade.share

import android.content.Intent
import android.net.Uri
import dev.stade.StadeApplication

actual fun openExternalUri(uri: String): Boolean = runCatching {
    val context = StadeApplication.instance
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}.isSuccess
