package dev.stade.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.stade.AppContainer
import dev.stade.pad.PadAsset
import dev.stade.pad.PadConfig
import dev.stade.pad.PadLoadState
import dev.stade.pad.fetchPadAsset
import dev.stade.pad.fetchPadCatalog
import dev.stade.pad.padNetworkReady
import dev.stade.pad.preparePadSound
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PadMode { SOUNDS, MEMES }

private enum class PadProblem { DOWNLOAD, FORMAT }

@Composable
fun PadPanel(
    container: AppContainer,
    mode: PadMode,
    onDismiss: () -> Unit,
    onSend: (PadAsset, ByteArray) -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<PadLoadState>(PadLoadState.Loading) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var problem by remember { mutableStateOf<PadProblem?>(null) }

    LaunchedEffect(reloadKey) {
        state = PadLoadState.Loading
        if (!padNetworkReady(container)) {
            state = PadLoadState.TorNotReady
            return@LaunchedEffect
        }
        val catalog = fetchPadCatalog(container, force = reloadKey > 0)
        state = if (catalog == null) PadLoadState.Unavailable else PadLoadState.Ready(catalog)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = strings.cancel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                if (mode == PadMode.SOUNDS) Icons.Default.GraphicEq else Icons.Default.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    if (mode == PadMode.SOUNDS) strings.padPaddyTitle else strings.padMemepadTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (mode == PadMode.SOUNDS) strings.padPaddySubtitle else strings.padMemepadSubtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            if (state is PadLoadState.Ready) {
                TextButton(onClick = { problem = null; reloadKey++ }) { Text(strings.padRefresh) }
            }
        }
        problem?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                when (it) {
                    PadProblem.DOWNLOAD -> strings.padDownloadFailed
                    PadProblem.FORMAT -> strings.padSoundUnsupported
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(16.dp))

        when (val current = state) {
            is PadLoadState.Loading -> PadMessage(strings.loading, showSpinner = true)

            is PadLoadState.TorNotReady -> PadMessage(strings.padTorNotReady) {
                TextButton(onClick = { reloadKey++ }) { Text(strings.padRetry) }
            }

            is PadLoadState.Unavailable -> PadMessage(strings.padUnavailable) {
                TextButton(onClick = { reloadKey++ }) { Text(strings.padRetry) }
            }

            is PadLoadState.Ready -> {
                val items = if (mode == PadMode.SOUNDS) current.catalog.sounds else current.catalog.memes
                if (items.isEmpty()) {
                    PadMessage(strings.padEmpty) {
                        TextButton(onClick = { reloadKey++ }) { Text(strings.padRetry) }
                    }
                } else {
                    val pick: (PadAsset) -> Unit = { asset ->
                        if (busyId == null) {
                            busyId = asset.id
                            scope.launch {
                                val max = if (mode == PadMode.SOUNDS) {
                                    PadConfig.MAX_SOUND_BYTES
                                } else {
                                    PadConfig.MAX_MEME_BYTES
                                }
                                val bytes = fetchPadAsset(container, asset.url, asset.sha256, max)
                                val prepared = if (bytes != null && mode == PadMode.SOUNDS) {
                                    withContext(Dispatchers.Default) { preparePadSound(bytes) }
                                } else {
                                    null
                                }
                                busyId = null
                                when {
                                    bytes == null -> problem = PadProblem.DOWNLOAD
                                    mode != PadMode.SOUNDS -> onSend(asset, bytes)
                                    prepared != null -> {
                                        problem = null
                                        onSend(asset.copy(durationMs = prepared.durationMs), prepared.bytes)
                                    }
                                    else -> problem = PadProblem.FORMAT
                                }
                            }
                        }
                    }
                    if (mode == PadMode.SOUNDS) {
                        SoundGrid(items, busyId, pick)
                    } else {
                        MemeList(items, busyId, pick)
                    }
                }
            }
        }
    }
}

@Composable
private fun PadMessage(
    text: String,
    showSpinner: Boolean = false,
    action: @Composable (() -> Unit)? = null
) {
    Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
    horizontalAlignment = Alignment.CenterHorizontally
    ) {
    if (showSpinner) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        Spacer(Modifier.height(12.dp))
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    if (action != null) {
        Spacer(Modifier.height(4.dp))
        action()
    }
    }
}

@Composable
private fun SoundGrid(items: List<PadAsset>, busyId: String?, onPick: (PadAsset) -> Unit) {
    LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 104.dp),
    modifier = Modifier.heightIn(max = 420.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
    items(items, key = { it.id }) { asset ->
        Surface(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .clickable(enabled = busyId == null) { onPick(asset) },
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(18.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (busyId == asset.id) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        if (asset.emoji.isNotBlank()) {
                            Text(asset.emoji, style = MaterialTheme.typography.headlineSmall)
                        } else {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            asset.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun MemeList(items: List<PadAsset>, busyId: String?, onPick: (PadAsset) -> Unit) {
    LazyColumn(
    modifier = Modifier.heightIn(max = 420.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
    items(items, key = { it.id }) { asset ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = busyId == null) { onPick(asset) },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (busyId == asset.id) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        asset.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (asset.durationMs > 0) {
                        Text(
                            formatPadDuration(asset.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    }
}

fun formatPadDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (seconds < 10) "$minutes:0$seconds" else "$minutes:$seconds"
}
