package dev.stade.audio

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

actual class AudioPermissionState internal constructor(
    private val grantedState: MutableState<Boolean>,
    private val launcher: ActivityResultLauncher<String>
) {
    actual val granted: Boolean get() = grantedState.value
    actual fun request() {
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}

@Composable
actual fun rememberAudioPermissionState(): AudioPermissionState {
    val context = LocalContext.current
    val grantedState = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> grantedState.value = isGranted }
    return remember(launcher) { AudioPermissionState(grantedState, launcher) }
}
