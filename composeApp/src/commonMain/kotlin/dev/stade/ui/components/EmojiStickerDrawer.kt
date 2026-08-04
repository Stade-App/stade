package dev.stade.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.stade.sticker.Sticker
import dev.stade.emoji.CustomEmojiCatalog
import dev.stade.ui.decodeToImageBitmap
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.rememberResourceEnvironment

private enum class EmojiStickerTab { Emoji, Stickers }

private val DRAWER_GRID_HEIGHT = 320.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun EmojiStickerDrawer(
    stickers: List<Sticker>,
    onDismiss: () -> Unit,
    onSend: (ByteArray) -> Unit,
    onCreateSticker: () -> Unit,
    onDeleteSticker: (String) -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val environment = rememberResourceEnvironment()
    var tab by remember { mutableStateOf(EmojiStickerTab.Emoji) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TabRow(selectedTabIndex = tab.ordinal) {
                Tab(
                    selected = tab == EmojiStickerTab.Emoji,
                    onClick = { tab = EmojiStickerTab.Emoji },
                    text = { Text(strings.emojiTabLabel) }
                )
                Tab(
                    selected = tab == EmojiStickerTab.Stickers,
                    onClick = { tab = EmojiStickerTab.Stickers },
                    text = { Text(strings.stickersTabLabel) }
                )
            }

            when (tab) {
                EmojiStickerTab.Emoji -> {
                    if (CustomEmojiCatalog.all.isEmpty()) {
                        DrawerEmptyState(strings.noCustomEmojiYet)
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            contentPadding = PaddingValues(12.dp),
                            modifier = Modifier.fillMaxWidth().height(DRAWER_GRID_HEIGHT)
                        ) {
                            items(CustomEmojiCatalog.all, key = { it.key }) { emoji ->
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            scope.launch {
                                                val bytes = runCatching {
                                                    getDrawableResourceBytes(environment, emoji.drawable)
                                                }.getOrNull()
                                                if (bytes != null) {
                                                    onSend(bytes)
                                                    onDismiss()
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(emoji.drawable),
                                        contentDescription = emoji.key,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                EmojiStickerTab.Stickers -> {
                    if (stickers.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().height(DRAWER_GRID_HEIGHT),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                strings.noStickersYet,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(12.dp))
                            FilledTonalButton(onClick = onCreateSticker) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.createStickerAction)
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            contentPadding = PaddingValues(12.dp),
                            modifier = Modifier.fillMaxWidth().height(DRAWER_GRID_HEIGHT)
                        ) {
                            item(key = "create") {
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                                        .clickable(onClick = onCreateSticker),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = strings.createStickerAction,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            items(stickers, key = { it.id }) { sticker ->
                                val bitmap = remember(sticker.id) {
                                    runCatching { sticker.imageBytes.decodeToImageBitmap() }.getOrNull()
                                }
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .aspectRatio(1f)
                                        .pointerInput(sticker.id) {
                                            detectTapGestures(
                                                onTap = {
                                                    onSend(sticker.imageBytes)
                                                    onDismiss()
                                                },
                                                onLongPress = { pendingDeleteId = sticker.id }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    val deleteId = pendingDeleteId
    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(strings.deleteStickerConfirmTitle) },
            text = { Text(strings.deleteStickerConfirmBody) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSticker(deleteId)
                    pendingDeleteId = null
                }) { Text(strings.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
private fun DrawerEmptyState(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(DRAWER_GRID_HEIGHT),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}
