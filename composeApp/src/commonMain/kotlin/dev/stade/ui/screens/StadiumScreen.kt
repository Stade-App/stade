package dev.stade.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.stade.AppContainer
import dev.stade.audio.RecordedClip
import dev.stade.audio.rememberAudioPermissionState
import dev.stade.audio.rememberAudioPlayer
import dev.stade.audio.rememberAudioRecorder
import dev.stade.identity.LocalIdentity
import dev.stade.media.MediaEditorDialog
import dev.stade.message.MessageType
import dev.stade.stadium.StadiumMessage
import dev.stade.ui.copyImageToClipboard
import dev.stade.ui.decodeToImageBitmap
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.components.formatChatTime
import dev.stade.ui.components.formatVoiceDuration
import dev.stade.ui.rememberMultiImagePickerLauncher
import dev.stade.ui.saveImageToGallery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class StadiumBannerKind { Success, Error, Info }
private data class StadiumBannerData(val message: String, val kind: StadiumBannerKind)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StadiumScreen(
    container: AppContainer,
    owner: LocalIdentity,
    stadiumId: String,
    onBack: () -> Unit,
    onManage: () -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val stadiums by container.stadiums.observeStadiums(owner.id).collectAsState(initial = emptyList())
    val stadium = remember(stadiums, stadiumId) { stadiums.find { it.id == stadiumId } }
    val messages by container.stadiums.observeMessages(stadiumId).collectAsState(initial = emptyList())
    val listState = rememberLazyListState()

    var draft by remember { mutableStateOf("") }

    val MAX_IMAGE_BYTES = 3 * 1024 * 1024
    var pendingImages by remember { mutableStateOf<List<ByteArray>>(emptyList()) }
    var editingImageIndex by remember { mutableStateOf<Int?>(null) }

    var banner by remember { mutableStateOf<StadiumBannerData?>(null) }
    var bannerKey by remember { mutableStateOf(0) }
    LaunchedEffect(bannerKey) {
        if (bannerKey > 0) {
            delay(3500L)
            banner = null
        }
    }
    fun notify(message: String, kind: StadiumBannerKind = StadiumBannerKind.Info) {
        banner = StadiumBannerData(message, kind)
        bannerKey++
    }

    val imagePicker = rememberMultiImagePickerLauncher { imagesList ->
        val accepted = imagesList.filter { it.size <= MAX_IMAGE_BYTES }
        if (accepted.size != imagesList.size) {
            notify(strings.photoTooBig, StadiumBannerKind.Error)
        }
        if (accepted.isNotEmpty()) {
            pendingImages = pendingImages + accepted
        }
    }

    var pendingVoiceClip by remember { mutableStateOf<RecordedClip?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    val micPermission = rememberAudioPermissionState()
    val recorder = rememberAudioRecorder(onMaxDurationReached = { clip ->
        isRecording = false
        if (clip != null) {
            pendingVoiceClip = clip
            notify(strings.voiceMaxDurationReached, StadiumBannerKind.Info)
        } else {
            notify(strings.voiceSendFailed, StadiumBannerKind.Error)
        }
    })

    fun toggleRecording() {
        if (isRecording) {
            isRecording = false
            scope.launch(Dispatchers.Default) {
                val clip = recorder.stop()
                if (clip != null) {
                    pendingVoiceClip = clip
                } else {
                    notify(strings.voiceSendFailed, StadiumBannerKind.Error)
                }
            }
        } else {
            if (!micPermission.granted) {
                micPermission.request()
                return
            }
            pendingVoiceClip = null
            isRecording = true
            recorder.start()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val current = stadium
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(current?.name ?: "", style = MaterialTheme.typography.titleMedium)
                        Text(
                            strings.stadiumSubscriberCount(current?.memberCount ?: 0L),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    if (current?.isOwner == true) {
                        IconButton(onClick = onManage) {
                            Icon(Icons.Default.Settings, contentDescription = strings.manageStadiumTitle)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Podcasts,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            when (msg.type) {
                                MessageType.IMAGE -> StadiumImageBubble(
                                    msg = msg,
                                    onSaveImage = { bytes ->
                                        scope.launch {
                                            val ok = saveImageToGallery(bytes, "stade_${msg.id}.jpg")
                                            notify(
                                                if (ok) strings.imageSaved else strings.imageSaveFailed,
                                                if (ok) StadiumBannerKind.Success else StadiumBannerKind.Error
                                            )
                                        }
                                    },
                                    onCopyImage = { bytes ->
                                        scope.launch {
                                            val ok = copyImageToClipboard(bytes)
                                            notify(
                                                if (ok) strings.imageCopied else strings.imageCopyFailed,
                                                if (ok) StadiumBannerKind.Success else StadiumBannerKind.Error
                                            )
                                        }
                                    }
                                )
                                MessageType.VOICE -> StadiumVoiceBubble(msg = msg)
                                else -> StadiumTextBubble(msg = msg)
                            }
                        }
                    }
                    if (current.isOwner) {
                        StadiumComposer(
                            draft = draft,
                            pendingImages = pendingImages,
                            pendingVoiceClip = pendingVoiceClip,
                            isRecording = isRecording,
                            onChange = { draft = it },
                            onRemoveImage = { idx ->
                                pendingImages = pendingImages.toMutableList().also { it.removeAt(idx) }
                            },
                            onEditImage = { idx -> editingImageIndex = idx },
                            onRemoveVoiceClip = { pendingVoiceClip = null },
                            onSend = {
                                val text = draft.trim()
                                val images = pendingImages
                                val voiceClip = pendingVoiceClip
                                if (text.isEmpty() && images.isEmpty() && voiceClip == null) return@StadiumComposer
                                draft = ""
                                pendingImages = emptyList()
                                pendingVoiceClip = null
                                scope.launch {
                                    if (text.isNotEmpty()) {
                                        runCatching { container.stadiumChat.post(owner, current, text) }
                                            .onFailure { notify(strings.sendFailed(it.message ?: ""), StadiumBannerKind.Error) }
                                    }
                                    images.forEach { imageBytes ->
                                        runCatching { container.stadiumChat.postImage(owner, current, imageBytes) }
                                            .onFailure { notify(strings.photoSendFailed, StadiumBannerKind.Error) }
                                    }
                                    if (voiceClip != null) {
                                        runCatching { container.stadiumChat.postVoice(owner, current, voiceClip.opusBytes, voiceClip.durationMs) }
                                            .onFailure { notify(strings.voiceSendFailed, StadiumBannerKind.Error) }
                                    }
                                }
                            },
                            onPickImage = { imagePicker.launch() },
                            onToggleRecording = { toggleRecording() }
                        )
                    }
                }
                val editIdx = editingImageIndex
                if (editIdx != null && editIdx < pendingImages.size) {
                    MediaEditorDialog(
                        imageBytes = pendingImages[editIdx],
                        onSave = { edited ->
                            pendingImages = pendingImages.toMutableList().also { it[editIdx] = edited }
                            editingImageIndex = null
                        },
                        onCancel = { editingImageIndex = null }
                    )
                }
                StadiumTopBanner(
                    data = banner,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StadiumTextBubble(msg: StadiumMessage) {
    val bubbleColor = if (msg.isOwn) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (msg.isOwn) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface
    val alignment = if (msg.isOwn) Alignment.CenterEnd else Alignment.CenterStart
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Text(
                msg.displayBody,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun StadiumImageBubble(
    msg: StadiumMessage,
    onSaveImage: (ByteArray) -> Unit,
    onCopyImage: (ByteArray) -> Unit
) {
    val strings = LocalStrings.current
    val outgoing = msg.isOwn
    val align = if (outgoing) Alignment.End else Alignment.Start
    val bg = if (outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val sub = MaterialTheme.colorScheme.onSurfaceVariant

    var imageBytes by remember(msg.id) { mutableStateOf<ByteArray?>(null) }
    var bitmap by remember(msg.id) { mutableStateOf<ImageBitmap?>(null) }
    var decodeDone by remember(msg.id) { mutableStateOf(false) }
    LaunchedEffect(msg.id) {
        val (bytes, decoded) = withContext(Dispatchers.Default) {
            val b = runCatching { msg.imageBytes() }.getOrNull()
            b to runCatching { b?.decodeToImageBitmap() }.getOrNull()
        }
        imageBytes = bytes
        bitmap = decoded
        decodeDone = true
    }
    var showFullscreen by remember { mutableStateOf(false) }
    val currentBitmap = bitmap
    val currentBytes = imageBytes
    val currentOnTap by rememberUpdatedState { if (bitmap != null) showFullscreen = true }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalAlignment = align
    ) {
        Box(
            Modifier.widthIn(max = 240.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .clickable { currentOnTap() }
                .padding(4.dp)
        ) {
            Column {
                if (currentBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = currentBitmap,
                        contentDescription = strings.photoMessage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        if (decodeDone) {
                            Icon(
                                Icons.Default.BrokenImage,
                                contentDescription = null,
                                tint = sub,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    formatChatTime(msg.timestamp),
                    color = sub,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }

    if (showFullscreen && currentBitmap != null && currentBytes != null) {
        Dialog(onDismissRequest = { showFullscreen = false }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    bitmap = currentBitmap,
                    contentDescription = strings.photoMessage,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentScale = ContentScale.Fit
                )
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = { onSaveImage(currentBytes) }) {
                        Icon(Icons.Default.Download, contentDescription = strings.saveImageAction, tint = Color.White)
                    }
                    IconButton(onClick = { onCopyImage(currentBytes) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = strings.copyImageAction, tint = Color.White)
                    }
                    IconButton(onClick = { showFullscreen = false }) {
                        Icon(Icons.Default.Close, contentDescription = strings.closePhoto, tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun StadiumVoiceBubble(msg: StadiumMessage) {
    val outgoing = msg.isOwn
    val align = if (outgoing) Alignment.End else Alignment.Start
    val bg = if (outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val sub = fg.copy(alpha = 0.6f)

    var opusBytes by remember(msg.id) { mutableStateOf<ByteArray?>(null) }
    var voiceDurationMs by remember(msg.id) { mutableStateOf(0) }
    var decodeDone by remember(msg.id) { mutableStateOf(false) }
    LaunchedEffect(msg.id) {
        val (bytes, dur) = withContext(Dispatchers.Default) {
            val b = runCatching { msg.voiceOpusBytes() }.getOrNull()
            val d = runCatching { msg.voiceDurationMs() }.getOrNull() ?: 0
            b to d
        }
        opusBytes = bytes
        voiceDurationMs = dur
        decodeDone = true
    }
    val player = rememberAudioPlayer()
    val currentBytes = opusBytes

    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = align) {
        Box(
            Modifier.widthIn(max = 260.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val bytes = currentBytes
                            if (bytes != null) {
                                if (player.isPlaying) player.pause() else player.play(bytes)
                            }
                        },
                        enabled = currentBytes != null,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = fg
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        val positionMs = player.positionMs
                        val durationMs = if (player.durationMs > 0) player.durationMs else voiceDurationMs
                        val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                        Box(
                            Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(sub.copy(alpha = 0.3f))
                        ) {
                            Box(Modifier.fillMaxWidth(progress).height(3.dp).clip(RoundedCornerShape(2.dp)).background(fg))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (currentBytes == null && decodeDone) "" else formatVoiceDuration(durationMs),
                            color = sub,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    formatChatTime(msg.timestamp),
                    color = sub,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StadiumComposer(
    draft: String,
    pendingImages: List<ByteArray>,
    pendingVoiceClip: RecordedClip?,
    isRecording: Boolean,
    onChange: (String) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onEditImage: (Int) -> Unit,
    onRemoveVoiceClip: () -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    onToggleRecording: () -> Unit
) {
    val strings = LocalStrings.current
    val canSend = draft.isNotBlank() || pendingImages.isNotEmpty() || pendingVoiceClip != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        AnimatedVisibility(
            visible = pendingImages.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(pendingImages) { idx, bytes ->
                    val bitmap = remember(bytes) { runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
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
                                    contentScale = ContentScale.Crop
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
                    IconButton(onClick = { if (player.isPlaying) player.pause() else player.play(clip.opusBytes) }) {
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
                    IconButton(onClick = { player.stop(); onRemoveVoiceClip() }) {
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = draft,
                onValueChange = onChange,
                placeholder = { Text(strings.typeMessagePlaceholder) },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.large,
                trailingIcon = {
                    IconButton(onClick = onPickImage) {
                        Icon(
                            Icons.Default.Attachment,
                            contentDescription = strings.attachPhoto,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.rotate(270f)
                        )
                    }
                }
            )

            val voiceButtonMode = when {
                isRecording -> StadiumVoiceButtonMode.STOP
                canSend -> StadiumVoiceButtonMode.SEND
                else -> StadiumVoiceButtonMode.MIC
            }
            val buttonContainerColor by animateColorAsState(
                targetValue = if (voiceButtonMode == StadiumVoiceButtonMode.SEND) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            val buttonContentColor by animateColorAsState(
                targetValue = when (voiceButtonMode) {
                    StadiumVoiceButtonMode.SEND -> MaterialTheme.colorScheme.onPrimary
                    StadiumVoiceButtonMode.STOP -> MaterialTheme.colorScheme.error
                    StadiumVoiceButtonMode.MIC -> MaterialTheme.colorScheme.primary
                }
            )
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(buttonContainerColor)
                    .clickable {
                        if (voiceButtonMode == StadiumVoiceButtonMode.SEND) onSend() else onToggleRecording()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (voiceButtonMode) {
                        StadiumVoiceButtonMode.SEND -> Icons.AutoMirrored.Filled.Send
                        StadiumVoiceButtonMode.STOP -> Icons.Default.Stop
                        StadiumVoiceButtonMode.MIC -> Icons.Default.Mic
                    },
                    contentDescription = when (voiceButtonMode) {
                        StadiumVoiceButtonMode.SEND -> strings.sendButton
                        StadiumVoiceButtonMode.STOP -> strings.stopRecording
                        StadiumVoiceButtonMode.MIC -> strings.recordVoice
                    },
                    tint = buttonContentColor,
                    modifier = Modifier.size(if (voiceButtonMode == StadiumVoiceButtonMode.SEND) 24.dp else 26.dp)
                )
            }
        }
    }
}

private enum class StadiumVoiceButtonMode { MIC, STOP, SEND }

@Composable
private fun StadiumTopBanner(
    data: StadiumBannerData?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = data != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        if (data == null) return@AnimatedVisibility
        val (bg, fg, icon) = when (data.kind) {
            StadiumBannerKind.Success -> Triple(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
                Icons.Default.CheckCircle
            )
            StadiumBannerKind.Error -> Triple(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                Icons.Default.Error
            )
            StadiumBannerKind.Info -> Triple(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer,
                Icons.Default.Info
            )
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = bg,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(icon, contentDescription = null, tint = fg)
                Text(data.message, color = fg, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
