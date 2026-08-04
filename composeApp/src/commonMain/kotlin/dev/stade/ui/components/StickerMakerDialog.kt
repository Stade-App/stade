package dev.stade.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.stade.media.ForceFullScreenDialogWindow
import dev.stade.media.encodeStickerPng
import dev.stade.media.isBackgroundRemovalSupported
import dev.stade.media.removeImageBackground
import dev.stade.media.rememberNavigationBarHeight
import dev.stade.ui.decodeToImageBitmap
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.rememberMediaPickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class StickerMakerStep { Picking, Processing, Preview }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerMakerDialog(
    onSave: (ByteArray) -> Unit,
    onCancel: () -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(StickerMakerStep.Picking) }
    var originalBytes by remember { mutableStateOf<ByteArray?>(null) }
    var removedBgBytes by remember { mutableStateOf<ByteArray?>(null) }
    var useRemovedBg by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val picker = rememberMediaPickerLauncher(
        onImages = { images ->
            val bytes = images.firstOrNull()
            if (bytes == null) {
                onCancel()
                return@rememberMediaPickerLauncher
            }
            originalBytes = bytes
            step = StickerMakerStep.Processing
            scope.launch {
                val removed = if (isBackgroundRemovalSupported) {
                    withContext(Dispatchers.Default) { removeImageBackground(bytes) }
                } else null
                removedBgBytes = removed
                useRemovedBg = removed != null
                step = StickerMakerStep.Preview
            }
        },
        onVideo = {},
        imagesOnly = true
    )

    LaunchedEffect(Unit) { picker.launch() }

    when (step) {
        StickerMakerStep.Picking, StickerMakerStep.Processing -> {
            Dialog(onDismissRequest = onCancel) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onCancel, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Default.Close, contentDescription = strings.cancel)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        if (step == StickerMakerStep.Processing) {
                            Spacer(Modifier.height(10.dp))
                            Text(strings.removingBackgroundLabel, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        StickerMakerStep.Preview -> {
            val original = originalBytes
            if (original == null) {
                onCancel()
                return
            }
            val removed = removedBgBytes
            val shownBytes = if (useRemovedBg && removed != null) removed else original
            val bitmap = remember(shownBytes) { runCatching { shownBytes.decodeToImageBitmap() }.getOrNull() }

            Dialog(
                onDismissRequest = { if (!saving) onCancel() },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                ForceFullScreenDialogWindow()
                val navBarHeight = rememberNavigationBarHeight()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .windowInsetsPadding(WindowInsets.systemBars)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { if (!saving) onCancel() }) {
                            Icon(Icons.Default.Close, contentDescription = strings.cancel, tint = Color.White)
                        }
                        Text(strings.stickerMakerTitle, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        IconButton(
                            enabled = !saving,
                            onClick = {
                                saving = true
                                scope.launch {
                                    val encoded = withContext(Dispatchers.Default) {
                                        runCatching { encodeStickerPng(shownBytes) }.getOrDefault(shownBytes)
                                    }
                                    onSave(encoded)
                                }
                            }
                        ) {
                            if (saving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Check, contentDescription = strings.saveStickerAction, tint = Color.White)
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    if (removed != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = navBarHeight)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilterChip(
                                selected = useRemovedBg,
                                onClick = { useRemovedBg = true },
                                label = { Text(strings.removeBackgroundOption) }
                            )
                            FilterChip(
                                selected = !useRemovedBg,
                                onClick = { useRemovedBg = false },
                                label = { Text(strings.keepOriginalBackgroundOption) }
                            )
                        }
                    }
                }
            }
        }
    }
}
