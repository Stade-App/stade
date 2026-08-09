package dev.stade.share

import android.content.Intent
import androidx.core.content.FileProvider
import dev.stade.StadeApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

actual suspend fun shareFile(bytes: ByteArray, filename: String, mimeType: String, title: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val context = StadeApplication.instance
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            dir.listFiles()?.forEach { runCatching { it.delete() } }
            val file = File(dir, filename)
            FileOutputStream(file).use { it.write(bytes) }
            val uri = FileProvider.getUriForFile(context, "dev.stade.fileprovider", file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }
