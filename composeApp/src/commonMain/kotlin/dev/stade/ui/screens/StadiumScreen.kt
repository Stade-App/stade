package dev.stade.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.windowInsetsBottomHeight
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.stade.AppContainer
import dev.stade.audio.MIN_VOICE_DURATION_MS
import dev.stade.audio.RecordedClip
import dev.stade.audio.rememberAudioPermissionState
import dev.stade.audio.rememberAudioPlayer
import dev.stade.audio.rememberAudioRecorder
import dev.stade.identity.LocalIdentity
import dev.stade.media.MediaEditorDialog
import dev.stade.message.MAX_ATTACHMENT_BYTES
import dev.stade.message.MessageType
import dev.stade.stadium.StadiumMessage
import dev.stade.stadium.isOfficial
import dev.stade.ui.copyImageToClipboard
import dev.stade.ui.decodeToImageBitmap
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.PlatformBackHandler
import dev.stade.ui.components.Avatar
import org.jetbrains.compose.resources.painterResource
import stade.composeapp.generated.resources.Res
import stade.composeapp.generated.resources.app_icon
import dev.stade.ui.components.ChatComposerBar
import dev.stade.ui.components.EmojiStickerDrawer
import dev.stade.ui.components.ScrollToBottomButton
import dev.stade.ui.components.StickerMakerDialog
import dev.stade.ui.components.formatChatTime
import dev.stade.ui.components.formatVoiceDuration
import dev.stade.ui.rememberMediaPickerLauncher
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
    val stadiums by remember(owner.id) { container.stadiums.observeStadiums(owner.id) }.collectAsState(initial = emptyList())
    val stadium = remember(stadiums, stadiumId) { stadiums.find { it.id == stadiumId } }
    val rawMessages by remember(stadiumId) { container.stadiums.observeMessages(stadiumId) }.collectAsState(initial = null)
    val messages = rawMessages ?: emptyList()
    val connected by container.sync.connectedContacts.collectAsState()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    var prevColumnHeight by remember { mutableStateOf(Int.MAX_VALUE) }

    LaunchedEffect(stadiumId) {
        val current = container.stadiums.getStadium(stadiumId) ?: return@LaunchedEffect
        if (!current.isOwner) {
            runCatching { container.stadiumChat.requestSubscriberCount(owner, current) }
        }
    }

    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showEmojiDrawer by remember { mutableStateOf(false) }
    var showStickerMaker by remember { mutableStateOf(false) }
    val stickers by remember(owner.id) { container.stickers.observeStickers(owner.id) }.collectAsState(initial = emptyList())
    var leaving by remember { mutableStateOf(false) }

    var selectedMessageIds by remember(stadiumId) { mutableStateOf<Set<String>>(emptySet()) }
    val inSelectionMode by remember { derivedStateOf { selectedMessageIds.isNotEmpty() } }

    fun clearSelection() {
        selectedMessageIds = emptySet()
    }

    fun toggleSelection(id: String) {
        selectedMessageIds = if (selectedMessageIds.contains(id)) {
            selectedMessageIds - id
        } else {
            selectedMessageIds + id
        }
    }

    var pendingImages by remember { mutableStateOf<List<ByteArray>>(emptyList()) }
    var editingImageIndex by remember { mutableStateOf<Int?>(null) }
    var pendingVideo by remember { mutableStateOf<ByteArray?>(null) }

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

    val mediaPicker = rememberMediaPickerLauncher(
        onImages = { imagesList ->
            val accepted = imagesList.filter { it.size <= MAX_ATTACHMENT_BYTES }
            if (accepted.size != imagesList.size) {
                notify(strings.photoTooBig, StadiumBannerKind.Error)
            }
            if (accepted.isNotEmpty()) {
                pendingImages = pendingImages + accepted
            }
        },
        onVideo = { bytes ->
            if (bytes.size <= MAX_ATTACHMENT_BYTES) {
                pendingVideo = bytes
            } else {
                notify(strings.videoTooBig, StadiumBannerKind.Error)
            }
        }
    )

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
                when {
                    clip == null -> notify(strings.voiceSendFailed, StadiumBannerKind.Error)
                    clip.durationMs < MIN_VOICE_DURATION_MS -> notify(strings.voiceTooShort, StadiumBannerKind.Error)
                    else -> pendingVoiceClip = clip
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

    DisposableEffect(stadiumId) {
        container.activeContactId = stadiumId
        onDispose { container.activeContactId = null }
    }

    LaunchedEffect(stadiumId) {
        container.sync.events.collect { event ->
            if (event is dev.stade.sync.SyncEngine.SyncEvent.StadiumDeleted && event.stadiumId == stadiumId) {
                onBack()
            }
        }
    }

    var prevMessageCount by remember(stadiumId) { mutableStateOf(0) }
    var scrollReady by remember(stadiumId) { mutableStateOf(false) }
    LaunchedEffect(rawMessages) {
        if (rawMessages == null) return@LaunchedEffect
        if (messages.isNotEmpty()) {
            if (prevMessageCount == 0) {
                listState.scrollToItem(messages.lastIndex)
            } else {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
        prevMessageCount = messages.size
        scrollReady = true
    }

    val current = stadium

    if (showLeaveDialog && current != null) {
        AlertDialog(
            onDismissRequest = { if (!leaving) showLeaveDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(strings.leaveStadiumConfirmTitle) },
            text = { Text(strings.leaveStadiumConfirmBody, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    enabled = !leaving,
                    onClick = {
                        leaving = true
                        scope.launch(Dispatchers.Default) {
                            runCatching { container.stadiumChat.leave(owner, current) }
                            withContext(Dispatchers.Main) {
                                leaving = false
                                showLeaveDialog = false
                                onBack()
                            }
                        }
                    }
                ) { Text(strings.leaveAction, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !leaving, onClick = { showLeaveDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showInviteDialog && current != null) {
        StadiumInviteDialog(
            container = container,
            owner = owner,
            stadium = current,
            onDismiss = { showInviteDialog = false }
        )
    }

    if (showEmojiDrawer && current != null) {
        EmojiStickerDrawer(
            stickers = stickers,
            onDismiss = { showEmojiDrawer = false },
            onSend = { bytes -> scope.launch { container.stadiumChat.postSticker(owner, current, bytes) } },
            onCreateSticker = {
                showEmojiDrawer = false
                showStickerMaker = true
            },
            onDeleteSticker = { id -> container.stickers.delete(id) }
        )
    }

    if (showStickerMaker) {
        StickerMakerDialog(
            onSave = { bytes ->
                runCatching { container.stickers.create(owner.id, bytes) }
                    .onFailure { notify(strings.stickerCreationFailed, StadiumBannerKind.Error) }
                showStickerMaker = false
            },
            onCancel = { showStickerMaker = false }
        )
    }

    PlatformBackHandler(enabled = inSelectionMode) { clearSelection() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (inSelectionMode) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    title = {
                        Text(
                            strings.selectedCount(selectedMessageIds.size),
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = strings.cancelSelection)
                        }
                    },
                    actions = {
                        val singleSelectedTextMsg = remember(selectedMessageIds, messages) {
                            if (selectedMessageIds.size != 1) null
                            else messages.firstOrNull { it.id in selectedMessageIds && it.type == MessageType.TEXT }
                        }
                        if (singleSelectedTextMsg != null) {
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(singleSelectedTextMsg.displayBody))
                                clearSelection()
                                notify(strings.messageCopied, StadiumBannerKind.Success)
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = strings.copyMessage)
                            }
                        }
                        val singleSelectedStickerMsg = remember(selectedMessageIds, messages) {
                            if (selectedMessageIds.size != 1) null
                            else messages.firstOrNull {
                                it.id in selectedMessageIds && it.type == MessageType.STICKER && !it.isOwn
                            }
                        }
                        if (singleSelectedStickerMsg != null) {
                            IconButton(onClick = {
                                val bytes = singleSelectedStickerMsg.stickerBytes()
                                clearSelection()
                                if (bytes != null) {
                                    runCatching { container.stickers.create(owner.id, bytes) }
                                        .onSuccess { notify(strings.stickerSavedToPack, StadiumBannerKind.Success) }
                                        .onFailure { notify(strings.stickerCreationFailed, StadiumBannerKind.Error) }
                                }
                            }) {
                                Icon(Icons.Default.Download, contentDescription = strings.saveStickerToPackAction)
                            }
                        }
                        if (current != null && current.isOwner) {
                            IconButton(onClick = {
                                val toDelete = selectedMessageIds
                                clearSelection()
                                scope.launch {
                                    toDelete.forEach { id ->
                                        runCatching { container.stadiumChat.deleteMessageAsOwner(owner, current, id) }
                                    }
                                }
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = strings.deleteMessageAction,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(
                                name = current?.name ?: "",
                                size = 36.dp,
                                icon = Icons.Default.Podcasts,
                                image = if (current?.isOfficial == true) painterResource(Res.drawable.app_icon) else null,
                                verified = current?.isOfficial == true
                            )
                            Spacer(Modifier.size(10.dp))
                            Column {
                                Text(current?.name ?: "", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    strings.stadiumSubscriberCount(current?.memberCount ?: 0L),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                        }
                    },
                    actions = {
                        if (current?.isOwner == true) {
                            IconButton(onClick = { showInviteDialog = true }) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.inviteAction)
                            }
                            IconButton(onClick = onManage) {
                                Icon(Icons.Default.Settings, contentDescription = strings.manageStadiumTitle)
                            }
                        } else if (current != null) {
                            IconButton(onClick = { showLeaveDialog = true }) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = strings.leaveStadiumAction)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
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
                        .onSizeChanged { size ->
                            if (size.height < prevColumnHeight && messages.isNotEmpty()) {
                                scope.launch { listState.scrollToItem(messages.lastIndex) }
                            }
                            prevColumnHeight = size.height
                        }
                ) {
                    if (!current.isOwner && !connected.contains(current.creatorStadeId)) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.WifiOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    strings.stadiumConnectionLost,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { container.connections.retryContact(current.creatorStadeId) }) {
                                    Text(strings.stadiumReconnectAction)
                                }
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().alpha(if (scrollReady) 1f else 0f),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                val isSelected by remember(msg.id) { derivedStateOf { selectedMessageIds.contains(msg.id) } }
                                val onShortClick: () -> Unit = { if (inSelectionMode) toggleSelection(msg.id) }
                                val onLongClick: () -> Unit = { toggleSelection(msg.id) }
                                when (msg.type) {
                                    MessageType.IMAGE -> StadiumImageBubble(
                                        msg = msg,
                                        selected = isSelected,
                                        inSelectionMode = inSelectionMode,
                                        onShortClick = onShortClick,
                                        onLongClick = onLongClick,
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
                                    MessageType.VOICE -> StadiumVoiceBubble(
                                        msg = msg,
                                        selected = isSelected,
                                        inSelectionMode = inSelectionMode,
                                        onShortClick = onShortClick,
                                        onLongClick = onLongClick
                                    )
                                    MessageType.VIDEO -> StadiumVideoBubble(
                                        msg = msg,
                                        selected = isSelected,
                                        inSelectionMode = inSelectionMode,
                                        onShortClick = onShortClick,
                                        onLongClick = onLongClick
                                    )
                                    MessageType.STICKER -> StadiumStickerBubble(
                                        msg = msg,
                                        selected = isSelected,
                                        inSelectionMode = inSelectionMode,
                                        onShortClick = onShortClick,
                                        onLongClick = onLongClick
                                    )
                                    else -> StadiumTextBubble(
                                        msg = msg,
                                        selected = isSelected,
                                        inSelectionMode = inSelectionMode,
                                        onShortClick = onShortClick,
                                        onLongClick = onLongClick
                                    )
                                }
                            }
                        }
                        ScrollToBottomButton(
                            listState = listState,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp)
                        )
                    }
                    if (current.isOwner) {
                        ChatComposerBar(
                            draft = draft,
                            pendingImages = pendingImages,
                            pendingVideo = pendingVideo,
                            pendingVoiceClip = pendingVoiceClip,
                            isRecording = isRecording,
                            onChange = { draft = it },
                            onRemoveImage = { idx ->
                                pendingImages = pendingImages.toMutableList().also { it.removeAt(idx) }
                            },
                            onEditImage = { idx -> editingImageIndex = idx },
                            onRemoveVideo = { pendingVideo = null },
                            onRemoveVoiceClip = { pendingVoiceClip = null },
                            onSend = {
                                val text = draft.text.trim()
                                val images = pendingImages
                                val video = pendingVideo
                                val voiceClip = pendingVoiceClip
                                if (text.isEmpty() && images.isEmpty() && video == null && voiceClip == null) return@ChatComposerBar
                                draft = TextFieldValue("")
                                pendingImages = emptyList()
                                pendingVideo = null
                                pendingVoiceClip = null
                                val hasMedia = images.isNotEmpty() || video != null
                                scope.launch {
                                    if (!hasMedia && text.isNotEmpty()) {
                                        runCatching { container.stadiumChat.post(owner, current, text) }
                                            .onFailure { notify(strings.sendFailed(it.message ?: ""), StadiumBannerKind.Error) }
                                    }
                                    images.forEachIndexed { idx, imageBytes ->
                                        runCatching { container.stadiumChat.postImage(owner, current, imageBytes, if (idx == 0) text else "") }
                                            .onFailure { notify(strings.photoSendFailed, StadiumBannerKind.Error) }
                                    }
                                    if (video != null) {
                                        runCatching { container.stadiumChat.postVideo(owner, current, video, if (images.isEmpty()) text else "") }
                                            .onFailure { notify(strings.videoSendFailed, StadiumBannerKind.Error) }
                                    }
                                    if (voiceClip != null) {
                                        runCatching { container.stadiumChat.postVoice(owner, current, voiceClip.opusBytes, voiceClip.durationMs) }
                                            .onFailure { notify(strings.voiceSendFailed, StadiumBannerKind.Error) }
                                    }
                                }
                            },
                            onPickMedia = { mediaPicker.launch() },
                            onToggleRecording = { toggleRecording() },
                            onOpenEmojiPicker = { showEmojiDrawer = true }
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsBottomHeight(WindowInsets.navigationBars)
                        .background(MaterialTheme.colorScheme.background)
                )
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
private fun StadiumTextBubble(
    msg: StadiumMessage,
    selected: Boolean,
    inSelectionMode: Boolean,
    onShortClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val bubbleColor = if (msg.isOwn) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (msg.isOwn) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface

    val tint by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
    val currentOnTap by rememberUpdatedState { if (inSelectionMode) onShortClick() }
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint)
            .pointerInput(msg.id) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onLongPress = { currentOnLongClick() }
                )
            }
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
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
    selected: Boolean,
    inSelectionMode: Boolean,
    onShortClick: () -> Unit,
    onLongClick: () -> Unit,
    onSaveImage: (ByteArray) -> Unit,
    onCopyImage: (ByteArray) -> Unit
) {
    val strings = LocalStrings.current
    val outgoing = msg.isOwn
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
    val tint by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
    val currentOnTap by rememberUpdatedState {
        if (inSelectionMode) onShortClick()
        else if (bitmap != null) showFullscreen = true
    }
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint)
            .pointerInput(msg.id) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onLongPress = { currentOnLongClick() }
                )
            }
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.widthIn(max = 240.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
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
                if (msg.caption.isNotEmpty()) {
                    Text(
                        msg.caption,
                        color = if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
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
private fun StadiumVoiceBubble(
    msg: StadiumMessage,
    selected: Boolean,
    inSelectionMode: Boolean,
    onShortClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val outgoing = msg.isOwn
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
    val tint by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
    val currentOnShortClick by rememberUpdatedState(onShortClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint)
            .pointerInput(msg.id) {
                detectTapGestures(
                    onTap = { currentOnShortClick() },
                    onLongPress = { currentOnLongClick() }
                )
            }
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

@Composable
private fun StadiumVideoBubble(
    msg: StadiumMessage,
    selected: Boolean,
    inSelectionMode: Boolean,
    onShortClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val strings = LocalStrings.current
    val outgoing = msg.isOwn
    val bg = if (outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val sub = fg.copy(alpha = 0.6f)

    var videoBytes by remember(msg.id) { mutableStateOf<ByteArray?>(null) }
    var decodeDone by remember(msg.id) { mutableStateOf(false) }
    var expanded by remember(msg.id) { mutableStateOf(false) }
    LaunchedEffect(msg.id) {
        val bytes = withContext(Dispatchers.Default) { runCatching { msg.videoBytes() }.getOrNull() }
        videoBytes = bytes
        decodeDone = true
    }
    val currentBytes = videoBytes
    val tint by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
    val currentOnShortClick by rememberUpdatedState(onShortClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint)
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.widthIn(max = 260.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column {
                if (expanded && currentBytes != null) {
                    dev.stade.ui.video.VideoPlayerView(
                        bytes = currentBytes,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 220.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .pointerInput(msg.id, currentBytes) {
                                detectTapGestures(
                                    onTap = {
                                        if (inSelectionMode) currentOnShortClick()
                                        else if (currentBytes != null) expanded = true
                                    },
                                    onLongPress = { currentOnLongClick() }
                                )
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(fg.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = strings.tapToPlayVideo, tint = fg)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(strings.videoMessage, color = fg, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (currentBytes == null && decodeDone) "" else strings.tapToPlayVideo,
                                color = sub,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                if (msg.caption.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(msg.caption, color = fg, style = MaterialTheme.typography.bodyMedium)
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

@Composable
private fun StadiumStickerBubble(
    msg: StadiumMessage,
    selected: Boolean,
    inSelectionMode: Boolean,
    onShortClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    var stickerBytes by remember(msg.id) { mutableStateOf<ByteArray?>(null) }
    var decodeDone by remember(msg.id) { mutableStateOf(false) }
    LaunchedEffect(msg.id) {
        val bytes = withContext(Dispatchers.Default) { runCatching { msg.stickerBytes() }.getOrNull() }
        stickerBytes = bytes
        decodeDone = true
    }
    val bitmap = remember(stickerBytes) {
        stickerBytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
    }
    val tint by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
    val currentOnTap by rememberUpdatedState { if (inSelectionMode) onShortClick() }
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint)
            .pointerInput(msg.id) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onLongPress = { currentOnLongClick() }
                )
            }
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else if (decodeDone) {
                Icon(Icons.Default.BrokenImage, contentDescription = null, tint = sub, modifier = Modifier.size(28.dp))
            }
        }
        Text(
            formatChatTime(msg.timestamp),
            color = sub,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

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
