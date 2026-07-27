package dev.stade.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

actual class VideoPickerLauncher(private val doLaunch: () -> Unit) {
    actual fun launch() = doLaunch()
}

@Composable
actual fun rememberVideoPickerLauncher(onVideo: (ByteArray) -> Unit): VideoPickerLauncher {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@runCatching
                onVideo(bytes)
            }
        }
    }
    return VideoPickerLauncher { launcher.launch("video/mp4") }
}
