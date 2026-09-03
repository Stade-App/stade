package dev.stade.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.stade.audio.RecordedClip
import dev.stade.audio.rememberAudioPlayer
import dev.stade.ui.decodeToImageBitmap
import dev.stade.ui.i18n.LocalStrings

data class ChatComposerReplyPreview(val senderLabel: String, val snippet: String)

private enum class VoiceSendMode { MIC, STOP, SEND }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatComposerBar(
    draft: TextFieldValue,
    pendingImages: List<ByteArray>,
    pendingVideo: ByteArray?,
    pendingVoiceClip: RecordedClip?,
    isRecording: Boolean,
    replyPreview: ChatComposerReplyPreview? = null,
    onChange: (TextFieldValue) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onEditImage: (Int) -> Unit,
    onRemoveVideo: () -> Unit,
    onRemoveVoiceClip: () -> Unit,
    onCancelReply: () -> Unit = {},
    onSend: () -> Unit,
    onLongPressSend: (() -> Unit)? = null,
    onPickMedia: () -> Unit,
    onToggleRecording: () -> Unit,
    onOpenEmojiPicker: () -> Unit = {}
) {
    val strings = LocalStrings.current
    val canSend = draft.text.isNotBlank() || pendingImages.isNotEmpty() || pendingVideo != null || pendingVoiceClip != null
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(220),
        label = "composerBorder"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        AnimatedVisibility(
            visible = replyPreview != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            if (replyPreview != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            strings.replyingToLabel(replyPreview.senderLabel),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            replyPreview.snippet,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onCancelReply) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = strings.cancelReply,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = pendingImages.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(pendingImages) { idx, bytes ->
                    val bitmap = remember(bytes) {
                        runCatching { bytes.decodeToImageBitmap() }.getOrNull()
                    }
                    Box(modifier = Modifier.size(72.dp)) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { onEditImage(idx) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (bitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.BrokenImage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .clickable { onRemoveImage(idx) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = pendingVideo != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            val video = pendingVideo
            if (video != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        strings.videoAttached,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onRemoveVideo) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = strings.removeAttachment,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = pendingVoiceClip != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            val clip = pendingVoiceClip
            if (clip != null) {
                val player = rememberAudioPlayer()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(onClick = {
                        if (player.isPlaying) player.pause() else player.play(clip.opusBytes)
                    }) {
                        Icon(
                            if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        formatVoiceDuration(clip.durationMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        player.stop()
                        onRemoveVoiceClip()
                    }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = strings.removeAttachment,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value = draft,
                onValueChange = onChange,
                interactionSource = interactionSource,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                            if (keyEvent.isShiftPressed) {
                                val cursor = draft.selection.end
                                val newText = draft.text.substring(0, cursor) + "\n" + draft.text.substring(cursor)
                                onChange(TextFieldValue(text = newText, selection = TextRange(cursor + 1)))
                            } else {
                                onSend()
                            }
                            true
                        } else {
                            false
                        }
                    },
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                maxLines = 5,
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(54.dp)
                            )
                            .border(1.5.dp, borderColor, RoundedCornerShape(54.dp))
                            .padding(start = 4.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onOpenEmojiPicker,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.InsertEmoticon,
                                contentDescription = strings.emojiPickerAction,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            if (draft.text.isEmpty()) {
                                Text(
                                    text = strings.typeMessagePlaceholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                        IconButton(
                            onClick = onPickMedia,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = strings.attachMediaAction,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            )

            val voiceButtonMode = when {
                isRecording -> VoiceSendMode.STOP
                canSend -> VoiceSendMode.SEND
                else -> VoiceSendMode.MIC
            }
            val buttonContainerColor by animateColorAsState(
                targetValue = if (voiceButtonMode == VoiceSendMode.SEND) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = tween(220),
                label = "voiceSendContainer"
            )
            val buttonContentColor by animateColorAsState(
                targetValue = when (voiceButtonMode) {
                    VoiceSendMode.SEND -> MaterialTheme.colorScheme.onPrimary
                    VoiceSendMode.STOP -> MaterialTheme.colorScheme.error
                    VoiceSendMode.MIC -> MaterialTheme.colorScheme.primary
                },
                animationSpec = tween(220),
                label = "voiceSendContent"
            )
            val haptic = LocalHapticFeedback.current
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(buttonContainerColor)
                    .combinedClickable(
                        onClick = {
                            if (voiceButtonMode == VoiceSendMode.SEND) onSend() else onToggleRecording()
                        },
                        onLongClick = if (onLongPressSend != null) {
                            {
                                if (voiceButtonMode == VoiceSendMode.SEND) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onLongPressSend()
                                }
                            }
                        } else null
                    ),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = voiceButtonMode,
                    transitionSpec = {
                        (scaleIn(initialScale = 0.5f) + fadeIn(tween(150))) togetherWith
                            (scaleOut(targetScale = 0.5f) + fadeOut(tween(150)))
                    },
                    label = "voiceSendIcon"
                ) { mode ->
                    Icon(
                        imageVector = when (mode) {
                            VoiceSendMode.SEND -> Icons.AutoMirrored.Filled.Send
                            VoiceSendMode.STOP -> Icons.Default.Stop
                            VoiceSendMode.MIC -> Icons.Default.Mic
                        },
                        contentDescription = when (mode) {
                            VoiceSendMode.SEND -> strings.sendButton
                            VoiceSendMode.STOP -> strings.stopRecording
                            VoiceSendMode.MIC -> strings.recordVoice
                        },
                        tint = buttonContentColor,
                        modifier = Modifier.size(if (mode == VoiceSendMode.SEND) 24.dp else 26.dp)
                    )
                }
            }
        }
    }
}
