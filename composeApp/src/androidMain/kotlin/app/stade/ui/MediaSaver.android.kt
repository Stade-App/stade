package app.stade.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.stade.StadeApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

actual suspend fun saveImageToGallery(bytes: ByteArray, suggestedName: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val context = StadeApplication.instance.applicationContext
            val displayName = "stade_" + System.currentTimeMillis() + ".jpg"
            val mime = "image/jpeg"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Stade")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext false
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return@withContext false
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val targetDir = File(picturesDir, "Stade").apply { mkdirs() }
                val file = File(targetDir, displayName)
                FileOutputStream(file).use { it.write(bytes) }
                true
            }
        }.getOrDefault(false)
    }

actual suspend fun copyImageToClipboard(bytes: ByteArray): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val context = StadeApplication.instance.applicationContext
            val displayName = "stade_clipboard_" + System.currentTimeMillis() + ".jpg"
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Stade")
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext false
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return@withContext false
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newUri(resolver, "image", uri)
            clipboard.setPrimaryClip(clip)
            true
        }.getOrDefault(false)
    }

