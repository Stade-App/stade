package dev.stade.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import dev.stade.sticker.MAX_IMPORT_ARCHIVE_BYTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

actual class StickerImportLauncher(private val onPick: () -> Unit) {
    actual fun pickFiles() = onPick()
}

@Composable
actual fun rememberStickerImportLauncher(
    onPicked: (List<PickedStickerFile>) -> Unit
): StickerImportLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onPicked)

    val open = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) {
            callback(emptyList())
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> readPicked(context.contentResolver, uri) }
            }
            callback(files)
        }
    }

    return remember(open) {
        StickerImportLauncher { open.launch(arrayOf("*/*")) }
    }
}

private fun readPicked(
    resolver: android.content.ContentResolver,
    uri: Uri
): PickedStickerFile? = runCatching {
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment ?: "sticker"

    val bytes = resolver.openInputStream(uri)?.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (out.size() + read > MAX_IMPORT_ARCHIVE_BYTES) return@use null
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    } ?: return null

    PickedStickerFile(name, bytes)
}.getOrNull()
