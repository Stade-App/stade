package dev.stade.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.stade.AppContainer
import dev.stade.audio.RecordedClip
import dev.stade.audio.rememberAudioPermissionState
import dev.stade.audio.rememberAudioPlayer
import dev.stade.audio.rememberAudioRecorder
import dev.stade.identity.LocalIdentity
import dev.stade.link.LinkPreview
import dev.stade.link.extractFirstUrl
import dev.stade.link.fetchLinkPreview
import dev.stade.link.getLinkPreviewsEnabled
import dev.stade.media.MediaEditorDialog
import dev.stade.message.IMAGE_BODY_PREFIX
import dev.stade.message.MAX_ATTACHMENT_BYTES
import dev.stade.message.Message
import dev.stade.message.MessageDirection
import dev.stade.message.MessageType
import dev.stade.message.previewBody
import dev.stade.notification.cancelMessagesNotification
import dev.stade.notification.clearAllMessageNotifications
import dev.stade.sync.SyncEngine
import dev.stade.transport.DialAttempt
import dev.stade.ui.PlatformBackHandler
import dev.stade.ui.components.Avatar
import dev.stade.ui.components.formatChatTime
import dev.stade.ui.components.formatVoiceDuration
import dev.stade.ui.components.maskAddress
import dev.stade.ui.copyImageToClipboard
import dev.stade.ui.decodeToImageBitmap
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.openVideoExternally
import dev.stade.ui.rememberMultiImagePickerLauncher
import dev.stade.ui.rememberVideoPickerLauncher
import dev.stade.ui.saveImageToGallery
import dev.stade.ui.theme.StadeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class NotificationKind { Success, Error, Info }
private data class NotificationData(val message: String, val kind: NotificationKind)
private const val DEFAULT_REACTION_EMOJI = "❤️"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    container: AppContainer,
    owner: LocalIdentity,
    contactId: String,
    highlightMessageId: String? = null,
    onBack: (() -> Unit)?,
    onOpenProfile: () -> Unit,
    onContactDeleted: (() -> Unit)? = null
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val contact = remember(contactId) { container.contacts.get(contactId) }
    val rawMessages by remember(contactId) { container.messages.observeMessages(contactId) }.collectAsState(initial = null)
    val messages = rawMessages ?: emptyList()
    val connected by container.sync.connectedContacts.collectAsState()
    val isOnline by remember(contactId) { derivedStateOf { connected.contains(contactId) } }
    val diagnostics by container.connections.diagnostics.collectAsState()
    val listState = rememberLazyListState()
    val linkPreviewsEnabled = remember { getLinkPreviewsEnabled(container.db) }
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var diagnosticsExpanded by remember(contactId) { mutableStateOf(false) }
    var showClearAddressesDialog by remember { mutableStateOf(false) }

    var selectedMessageIds by remember(contactId) { mutableStateOf<Set<String>>(emptySet()) }
    val inSelectionMode by remember { derivedStateOf { selectedMessageIds.isNotEmpty() } }
    var showSelectionDeleteDialog by remember { mutableStateOf(false) }

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

    fun toggleReaction(targetId: String, currentReactions: List<dev.stade.db.MessageReaction>) {
        val mine = currentReactions.any { it.fromId == owner.id }
        val c = contact ?: return
        scope.launch {
            withContext(Dispatchers.Default) {
                if (mine) {
                    container.messages.deleteReaction(targetId, owner.id)
                    runCatching { container.chat.sendReaction(owner, c, targetId, false, DEFAULT_REACTION_EMOJI) }
                } else {
                    container.messages.upsertReaction(targetId, owner.id, DEFAULT_REACTION_EMOJI)
                    runCatching { container.chat.sendReaction(owner, c, targetId, true, DEFAULT_REACTION_EMOJI) }
                }
            }
        }
    }

    var notification by remember { mutableStateOf<NotificationData?>(null) }
    var notificationKey by remember { mutableStateOf(0) }

    LaunchedEffect(notificationKey) {
        if (notificationKey > 0) {
            delay(3500L)
            notification = null
        }
    }

    fun showNotification(message: String, kind: NotificationKind = NotificationKind.Info) {
        notification = NotificationData(message, kind)
        notificationKey++
    }

    LaunchedEffect(contactId) {
        container.sync.events.collect { ev ->
            when (ev) {
                is SyncEngine.SyncEvent.HandshakeRejected ->
                    showNotification(strings.handshakeRejected(ev.reason), NotificationKind.Error)
                is SyncEngine.SyncEvent.ContactConnected ->
                    if (ev.contactId == contactId)
                        showNotification(strings.contactConnected, NotificationKind.Success)
                is SyncEngine.SyncEvent.DecryptFailed ->
                    if (ev.contactId == contactId)
                        showNotification(strings.decryptFailed, NotificationKind.Error)
                is SyncEngine.SyncEvent.SendFailed ->
                    if (ev.contactId == contactId)
                        showNotification(strings.sendFailed(ev.reason), NotificationKind.Error)
                else -> {}
            }
        }
    }

    DisposableEffect(contactId) {
        container.activeContactId = contactId
        cancelMessagesNotification(contactId)
        clearAllMessageNotifications()
        onDispose { container.activeContactId = null }
    }

    LaunchedEffect(contactId, messages.size) { container.messages.markRead(contactId) }

    var prevMessageCount by remember { mutableStateOf(0) }
    var scrollReady by remember(contactId) { mutableStateOf(false) }
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

    var flashedMessageId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(highlightMessageId, messages.size) {
        val target = highlightMessageId ?: return@LaunchedEffect
        val index = messages.indexOfFirst { it.id == target }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            flashedMessageId = target
            delay(1500L)
            flashedMessageId = null
        }
    }

    var pendingImages by remember { mutableStateOf<List<ByteArray>>(emptyList()) }
    var editingImageIndex by remember { mutableStateOf<Int?>(null) }

    val imagePicker = rememberMultiImagePickerLauncher { imagesList ->
        val accepted = imagesList.filter { it.size <= MAX_ATTACHMENT_BYTES }
        if (accepted.size != imagesList.size) {
            showNotification(strings.photoTooBig, NotificationKind.Error)
        }
        if (accepted.isNotEmpty()) {
            pendingImages = pendingImages + accepted
        }
    }

    var pendingVideo by remember { mutableStateOf<ByteArray?>(null) }
    val videoPicker = rememberVideoPickerLauncher { bytes ->
        if (bytes.size <= MAX_ATTACHMENT_BYTES) {
            pendingVideo = bytes
        } else {
            showNotification(strings.videoTooBig, NotificationKind.Error)
        }
    }

    var pendingVoiceClip by remember { mutableStateOf<RecordedClip?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    val micPermission = rememberAudioPermissionState()
    val recorder = rememberAudioRecorder(onMaxDurationReached = { clip ->
        isRecording = false
        if (clip != null) {
            pendingVoiceClip = clip
            showNotification(strings.voiceMaxDurationReached, NotificationKind.Info)
        } else {
            showNotification(strings.voiceSendFailed, NotificationKind.Error)
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
                    showNotification(strings.voiceSendFailed, NotificationKind.Error)
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

    var replyTarget by remember { mutableStateOf<Message?>(null) }

    var prevColumnHeight by remember { mutableStateOf(Int.MAX_VALUE) }

    if (showDeleteDialog && contact != null) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteDialog = false },
            title = { Text(strings.deleteContactDialogTitle) },
            text = {
                Text(strings.deleteContactDialogBody(contact.nickname))
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        scope.launch {
                            withContext(Dispatchers.Default) {
                                runCatching {
                                    container.sync.forgetContact(contact.id)
                                    container.contacts.purge(contact.id)
                                }
                            }
                            showDeleteDialog = false
                            deleting = false
                            (onContactDeleted ?: onBack)?.invoke()
                        }
                    }
                ) { Text(strings.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = { showDeleteDialog = false }
                ) { Text(strings.cancel) }
            }
        )
    }

    if (showSelectionDeleteDialog && selectedMessageIds.isNotEmpty()) {
        val toDelete = selectedMessageIds
        AlertDialog(
            onDismissRequest = { showSelectionDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(strings.deleteMessagesForMe) },
            text = { Text(strings.selectedCount(toDelete.size)) },
            confirmButton = {
                TextButton(onClick = {
                    showSelectionDeleteDialog = false
                    scope.launch {
                        withContext(Dispatchers.Default) {
                            runCatching { container.messages.deleteMessages(toDelete) }
                        }
                        clearSelection()
                    }
                }) { Text(strings.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showSelectionDeleteDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showClearAddressesDialog && contact != null) {
        AlertDialog(
            onDismissRequest = { showClearAddressesDialog = false },
            title = { Text(strings.clearAddressesConfirmTitle) },
            text = { Text(strings.clearAddressesConfirmBody) },
            confirmButton = {
                TextButton(onClick = {
                    showClearAddressesDialog = false
                    scope.launch {
                        runCatching { container.contacts.setAddresses(contact.id, emptyList()) }
                        showNotification(strings.addressesCleared, NotificationKind.Info)
                    }
                }) { Text(strings.clearAddresses, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAddressesDialog = false }) { Text(strings.cancel) }
            }
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
                                showNotification(strings.messageCopied, NotificationKind.Success)
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = strings.copyMessage)
                            }
                        }
                        IconButton(onClick = { showSelectionDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = strings.deleteMessagesForMe,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onOpenProfile() }
                        ) {
                            Avatar(name = contact?.nickname ?: "?", size = 36.dp)
                            Spacer(Modifier.size(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        contact?.nickname ?: "",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    if (contact?.verified == true) {
                                        Spacer(Modifier.size(6.dp))
                                        Icon(
                                            Icons.Default.Verified,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(7.dp).clip(CircleShape).background(
                                            if (isOnline) StadeColors.online else StadeColors.offline
                                        )
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text(
                                        if (isOnline) strings.online else strings.offline,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            enabled = !deleting
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = strings.deleteContactIconDescription,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(padding)
        ) {
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
                if (!isOnline && contact != null) {
                    DiagnosticsCard(
                        addresses = contact.addresses,
                        perAddr = diagnostics[contactId].orEmpty(),
                        expanded = diagnosticsExpanded,
                        onToggleExpanded = { diagnosticsExpanded = !diagnosticsExpanded },
                        onApplyInvite = { code ->
                            scope.launch {
                                try {
                                    val parsed = container.handshake.parseInvite(code.trim())
                                    when {
                                        parsed == null ->
                                            showNotification(strings.invalidInvite, NotificationKind.Error)
                                        !parsed.signingPublicKey.contentEquals(contact.publicSigningKey) ->
                                            showNotification(strings.inviteBelongsToDifferent, NotificationKind.Error)
                                        parsed.addresses.isEmpty() ->
                                            showNotification(strings.noConnectionInInvite, NotificationKind.Error)
                                        else -> {
                                            container.contacts.setAddresses(contact.id, parsed.addresses)
                                            container.connections.queueDial(parsed.addresses)
                                            showNotification(strings.connectionInfoUpdated, NotificationKind.Success)
                                        }
                                    }
                                } catch (e: Exception) {
                                    showNotification(strings.diagnosticError(e.message ?: ""), NotificationKind.Error)
                                }
                            }
                        },
                        onRetry = {
                            container.connections.retryContact(contact.id)
                            showNotification(strings.retryingConnection, NotificationKind.Info)
                        },
                        onClear = { showClearAddressesDialog = true }
                    )
                }

                if (rawMessages == null) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth())
                } else if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Avatar(name = contact?.nickname ?: "?", size = 64.dp)
                            Text(
                                strings.noMessagesYet,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                strings.sendFirstMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    val messagesById = remember(messages) { messages.associateBy { it.id } }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .alpha(if (scrollReady) 1f else 0f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        itemsIndexed(messages, key = { _, msg -> msg.id }) { idx, msg ->
                            val prev = messages.getOrNull(idx - 1)
                            val tight = prev != null &&
                                    prev.direction == msg.direction &&
                                    (msg.timestamp - prev.timestamp) < 60_000L
                            val isSelected = selectedMessageIds.contains(msg.id)
                            val isHighlighted = flashedMessageId == msg.id
                            val reactions by remember(msg.id) { container.messages.observeReactionsForMessage(msg.id) }.collectAsState(initial = emptyList())
                            val quotedMsg = remember(msg.id, msg.replyToId, messagesById) {
                                msg.replyToId?.let { rid -> messagesById[rid] }
                            }
                            val quoted = remember(msg.id, quotedMsg) {
                                when {
                                    msg.replyToId == null -> null
                                    quotedMsg != null -> ReplyQuoteInfo(
                                        senderLabel = if (quotedMsg.direction == MessageDirection.OUT) strings.youLabel else (contact?.nickname ?: ""),
                                        snippet = previewBody(quotedMsg.displayBody, strings.photoMessage, strings.voiceMessage, strings.videoMessage)
                                    ) {
                                        val target = messages.indexOfFirst { it.id == quotedMsg.id }
                                        if (target >= 0) scope.launch { listState.animateScrollToItem(target) }
                                    }
                                    else -> ReplyQuoteInfo(
                                        senderLabel = "",
                                        snippet = strings.originalMessageUnavailable,
                                        onClick = {}
                                    )
                                }
                            }
                            SwipeToReplyRow(
                                enabled = !inSelectionMode,
                                onReply = { replyTarget = msg }
                            ) {
                                if (msg.type == MessageType.IMAGE) {
                                    ImageBubble(
                                        msg = msg,
                                        tightWithPrev = tight,
                                        selected = isSelected,
                                        highlighted = isHighlighted,
                                        inSelectionMode = inSelectionMode,
                                        quoted = quoted,
                                        reactions = reactions,
                                        onShortClick = { if (inSelectionMode) toggleSelection(msg.id) },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            toggleSelection(msg.id)
                                        },
                                        onDoubleTap = { toggleReaction(msg.id, reactions) },
                                        onSaveImage = { bytes ->
                                            scope.launch {
                                                val ok = saveImageToGallery(bytes, "stade_${msg.id}.jpg")
                                                showNotification(
                                                    if (ok) strings.imageSaved else strings.imageSaveFailed,
                                                    if (ok) NotificationKind.Success else NotificationKind.Error
                                                )
                                            }
                                        },
                                        onCopyImage = { bytes ->
                                            scope.launch {
                                                val ok = copyImageToClipboard(bytes)
                                                showNotification(
                                                    if (ok) strings.imageCopied else strings.imageCopyFailed,
                                                    if (ok) NotificationKind.Success else NotificationKind.Error
                                                )
                                            }
                                        }
                                    )
                                } else if (msg.type == MessageType.VOICE) {
                                    VoiceBubble(
                                        msg = msg,
                                        tightWithPrev = tight,
                                        selected = isSelected,
                                        highlighted = isHighlighted,
                                        inSelectionMode = inSelectionMode,
                                        quoted = quoted,
                                        reactions = reactions,
                                        onShortClick = { if (inSelectionMode) toggleSelection(msg.id) },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            toggleSelection(msg.id)
                                        },
                                        onDoubleTap = { toggleReaction(msg.id, reactions) }
                                    )
                                } else if (msg.type == MessageType.VIDEO) {
                                    VideoBubble(
                                        msg = msg,
                                        tightWithPrev = tight,
                                        selected = isSelected,
                                        highlighted = isHighlighted,
                                        inSelectionMode = inSelectionMode,
                                        quoted = quoted,
                                        reactions = reactions,
                                        onShortClick = { if (inSelectionMode) toggleSelection(msg.id) },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            toggleSelection(msg.id)
                                        },
                                        onDoubleTap = { toggleReaction(msg.id, reactions) },
                                        onPlayVideo = { bytes ->
                                            scope.launch {
                                                val ok = openVideoExternally(bytes)
                                                if (!ok) showNotification(strings.videoOpenFailed, NotificationKind.Error)
                                            }
                                        }
                                    )
                                } else {
                                    Bubble(
                                        msg = msg,
                                        tightWithPrev = tight,
                                        selected = isSelected,
                                        highlighted = isHighlighted,
                                        inSelectionMode = inSelectionMode,
                                        quoted = quoted,
                                        reactions = reactions,
                                        onShortClick = { if (inSelectionMode) toggleSelection(msg.id) },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            toggleSelection(msg.id)
                                        },
                                        onDoubleTap = { toggleReaction(msg.id, reactions) },
                                        container = container,
                                        linkPreviewsEnabled = linkPreviewsEnabled
                                    )
                                }
                            }
                        }
                    }
                }

                Composer(
                    draft = draft,
                    pendingImages = pendingImages,
                    pendingVideo = pendingVideo,
                    pendingVoiceClip = pendingVoiceClip,
                    isRecording = isRecording,
                    replyPreview = replyTarget?.let { target ->
                        ReplyQuoteInfo(
                            senderLabel = if (target.direction == MessageDirection.OUT) strings.youLabel else (contact?.nickname ?: ""),
                            snippet = previewBody(target.displayBody, strings.photoMessage, strings.voiceMessage, strings.videoMessage),
                            onClick = {}
                        )
                    },
                    onChange = { draft = it },
                    onRemoveImage = { idx ->
                        pendingImages = pendingImages.toMutableList().also { it.removeAt(idx) }
                    },
                    onEditImage = { idx -> editingImageIndex = idx },
                    onRemoveVideo = { pendingVideo = null },
                    onRemoveVoiceClip = { pendingVoiceClip = null },
                    onCancelReply = { replyTarget = null },
                    onSend = {
                        val c = contact ?: return@Composer
                        val text = draft.text.trim()
                        val images = pendingImages
                        val video = pendingVideo
                        val voiceClip = pendingVoiceClip
                        val replyId = replyTarget?.id
                        if (text.isEmpty() && images.isEmpty() && video == null && voiceClip == null) return@Composer
                        draft = TextFieldValue("")
                        pendingImages = emptyList()
                        pendingVideo = null
                        pendingVoiceClip = null
                        replyTarget = null
                        val hasMedia = images.isNotEmpty() || video != null
                        scope.launch {
                            if (!hasMedia && text.isNotEmpty()) {
                                runCatching { container.chat.send(owner, c, text, replyId) }
                                    .onFailure { showNotification(strings.sendFailed(it.message ?: ""), NotificationKind.Error) }
                            }
                            images.forEachIndexed { idx, imageBytes ->
                                runCatching { container.chat.sendImage(owner, c, imageBytes, replyId, if (idx == 0) text else "") }
                                    .onFailure { showNotification(strings.photoSendFailed, NotificationKind.Error) }
                            }
                            if (video != null) {
                                runCatching { container.chat.sendVideo(owner, c, video, replyId, if (images.isEmpty()) text else "") }
                                    .onFailure { showNotification(strings.videoSendFailed, NotificationKind.Error) }
                            }
                            if (voiceClip != null) {
                                runCatching { container.chat.sendVoice(owner, c, voiceClip.opusBytes, voiceClip.durationMs, replyId) }
                                    .onFailure { showNotification(strings.voiceSendFailed, NotificationKind.Error) }
                            }
                        }
                    },
                    onPickImage = { imagePicker.launch() },
                    onPickVideo = { videoPicker.launch() },
                    onToggleRecording = { toggleRecording() }
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
                    .background(MaterialTheme.colorScheme.surface)
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

            TopNotificationBanner(
                data = notification,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}


@Composable
private fun TopNotificationBanner(
    data: NotificationData?,
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
            NotificationKind.Success -> Triple(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
                Icons.Default.CheckCircle
            )
            NotificationKind.Error -> Triple(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                Icons.Default.Error
            )
            NotificationKind.Info -> Triple(
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
                Icon(
                    icon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = data.message,
                    color = fg,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}


@Composable
private fun DiagnosticsCard(
    addresses: List<String>,
    perAddr: Map<String, DialAttempt>,
    onApplyInvite: (String) -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    val strings = LocalStrings.current
    var refreshLink by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpanded() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
            )
            Text(
                strings.connectionFailed,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) strings.collapseAction else strings.expandAction,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (addresses.isEmpty()) {
                    Text(
                        strings.noConnectionInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        strings.connectionChannels,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    addresses.forEachIndexed { idx, addr ->
                        val a = perAddr[addr]
                        val (icon, label, color) = when (a?.status) {
                            DialAttempt.Status.TRYING ->
                                Triple("…", strings.trying, MaterialTheme.colorScheme.onSurfaceVariant)
                            DialAttempt.Status.CONNECT_OK ->
                                Triple("•", strings.channelReadyVerifying, MaterialTheme.colorScheme.tertiary)
                            DialAttempt.Status.HANDSHAKE_OK ->
                                Triple("✓", strings.connectedLabel, StadeColors.online)
                            DialAttempt.Status.CONNECT_FAIL ->
                                Triple("✗", strings.unreachable, MaterialTheme.colorScheme.error)
                            DialAttempt.Status.HANDSHAKE_FAIL ->
                                Triple("✗", strings.handshakeFailed, MaterialTheme.colorScheme.error)
                            null ->
                                Triple("·", strings.notYetTried, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(icon, color = color, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    strings.channelLabel(idx + 1, maskAddress(addr)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(label, style = MaterialTheme.typography.labelSmall, color = color)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        strings.connectionDelayNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(strings.retryConnection) }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = refreshLink,
                    onValueChange = { refreshLink = it },
                    label = { Text(strings.newInviteCodeLabel) },
                    placeholder = { Text("STADE2-…") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        enabled = refreshLink.isNotBlank(),
                        onClick = { onApplyInvite(refreshLink); refreshLink = "" },
                        modifier = Modifier.weight(1f)
                    ) { Text(strings.applyInviteCode) }
                    OutlinedButton(
                        enabled = addresses.isNotEmpty(),
                        onClick = onClear
                    ) { Text(strings.clearAddresses) }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Composer(
    draft: TextFieldValue,
    pendingImages: List<ByteArray>,
    pendingVideo: ByteArray?,
    pendingVoiceClip: RecordedClip?,
    isRecording: Boolean,
    replyPreview: ReplyQuoteInfo?,
    onChange: (TextFieldValue) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onEditImage: (Int) -> Unit,
    onRemoveVideo: () -> Unit,
    onRemoveVoiceClip: () -> Unit,
    onCancelReply: () -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onToggleRecording: () -> Unit
) {
    val strings = LocalStrings.current
    val canSend = draft.text.isNotBlank() || pendingImages.isNotEmpty() || pendingVideo != null || pendingVoiceClip != null

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
            androidx.compose.foundation.text.BasicTextField(
                value = draft,
                onValueChange = onChange,
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
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
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
                            .padding(start = 18.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                            onClick = onPickVideo,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Videocam,
                                contentDescription = strings.attachVideo,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(
                            onClick = onPickImage,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Attachment,
                                contentDescription = strings.attachPhoto,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(26.dp)
                                    .rotate(270f)
                            )
                        }
                    }
                }
            )

            val voiceButtonMode = when {
                isRecording -> VoiceButtonMode.STOP
                canSend -> VoiceButtonMode.SEND
                else -> VoiceButtonMode.MIC
            }
            val buttonContainerColor by animateColorAsState(
                targetValue = if (voiceButtonMode == VoiceButtonMode.SEND) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = tween(220),
                label = "voiceSendContainer"
            )
            val buttonContentColor by animateColorAsState(
                targetValue = when (voiceButtonMode) {
                    VoiceButtonMode.SEND -> MaterialTheme.colorScheme.onPrimary
                    VoiceButtonMode.STOP -> MaterialTheme.colorScheme.error
                    VoiceButtonMode.MIC -> MaterialTheme.colorScheme.primary
                },
                animationSpec = tween(220),
                label = "voiceSendContent"
            )
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(buttonContainerColor)
                    .clickable {
                        if (voiceButtonMode == VoiceButtonMode.SEND) onSend() else onToggleRecording()
                    },
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
                            VoiceButtonMode.SEND -> Icons.AutoMirrored.Filled.Send
                            VoiceButtonMode.STOP -> Icons.Default.Stop
                            VoiceButtonMode.MIC -> Icons.Default.Mic
                        },
                        contentDescription = when (mode) {
                            VoiceButtonMode.SEND -> strings.sendButton
                            VoiceButtonMode.STOP -> strings.stopRecording
                            VoiceButtonMode.MIC -> strings.recordVoice
                        },
                        tint = buttonContentColor,
                        modifier = Modifier.size(if (mode == VoiceButtonMode.SEND) 24.dp else 26.dp)
                    )
                }
            }
        }
    }
}

private enum class VoiceButtonMode { MIC, STOP, SEND }

private data class ReplyQuoteInfo(
    val senderLabel: String,
    val snippet: String,
    val onClick: () -> Unit
)

@Composable
private fun SwipeToReplyRow(
    enabled: Boolean,
    onReply: () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val maxSwipePx = with(LocalDensity.current) { 64.dp.toPx() }
    val thresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    val iconProgress = (offsetX.value / thresholdPx).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxWidth()) {
        if (iconProgress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f * iconProgress)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = iconProgress),
                    modifier = Modifier.size(16.dp + 4.dp * iconProgress)
                )
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .then(
                    if (enabled) {
                        Modifier.pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val triggered = offsetX.value > thresholdPx
                                    scope.launch { offsetX.animateTo(0f, animationSpec = tween(180)) }
                                    if (triggered) onReply()
                                },
                                onDragCancel = {
                                    scope.launch { offsetX.animateTo(0f, animationSpec = tween(180)) }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch {
                                        offsetX.snapTo((offsetX.value + dragAmount).coerceIn(0f, maxSwipePx))
                                    }
                                }
                            )
                        }
                    } else Modifier
                )
        ) {
            content()
        }
    }
}

@Composable
private fun ReplyQuoteChip(
    info: ReplyQuoteInfo,
    outgoing: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val onOutgoing = MaterialTheme.colorScheme.onPrimary
    val bg = if (outgoing) onOutgoing.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (outgoing) onOutgoing else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { info.onClick() }
            .padding(vertical = 4.dp, horizontal = 6.dp)
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(if (outgoing) onOutgoing else accent)
        )
        Column(modifier = Modifier.padding(start = 6.dp)) {
            Text(
                info.senderLabel,
                color = if (outgoing) onOutgoing else accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                info.snippet,
                color = textColor.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    msg: Message,
    tightWithPrev: Boolean,
    selected: Boolean,
    highlighted: Boolean,
    inSelectionMode: Boolean,
    quoted: ReplyQuoteInfo?,
    reactions: List<dev.stade.db.MessageReaction>,
    onShortClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleTap: () -> Unit,
    container: AppContainer,
    linkPreviewsEnabled: Boolean
) {
    val outgoing = msg.direction == MessageDirection.OUT
    val align = if (outgoing) Alignment.End else Alignment.Start
    val bg = if (outgoing) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (outgoing) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    val sub = fg.copy(alpha = if (outgoing) 0.75f else 0.55f)

    val cornerTop = if (tightWithPrev) 6.dp else 18.dp
    val cornerSelf = 18.dp
    val cornerTail = if (tightWithPrev) 18.dp else 4.dp

    val tintTarget = when {
        highlighted -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    val tint by animateColorAsState(tintTarget)

    val currentOnShortClick by rememberUpdatedState(onShortClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)

    var preview by remember(msg.id) { mutableStateOf<LinkPreview?>(null) }
    LaunchedEffect(msg.id, linkPreviewsEnabled) {
        preview = null
        if (!linkPreviewsEnabled) return@LaunchedEffect
        val url = extractFirstUrl(msg.displayBody) ?: return@LaunchedEffect
        val cacheKey = "linkpreview:$url"
        val cached = withContext(Dispatchers.Default) {
            runCatching { container.db.stadeDbQueries.getKv(cacheKey).executeAsOneOrNull() }.getOrNull()
        }
        if (cached != null) {
            val parts = cached.decodeToString().split(Char(0x1f).toString(), limit = 2)
            preview = LinkPreview(url, parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" })
            return@LaunchedEffect
        }
        val fetched = withContext(Dispatchers.Default) {
            runCatching { fetchLinkPreview(url, container) }.getOrNull()
        }
        if (fetched != null) {
            preview = fetched
            withContext(Dispatchers.Default) {
                runCatching {
                    val delim = Char(0x1f).toString()
                    container.db.stadeDbQueries.putKv(cacheKey, (fetched.title + delim + fetched.description).encodeToByteArray())
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnShortClick() },
                    onLongPress = { currentOnLongClick() },
                    onDoubleTap = { currentOnDoubleTap() }
                )
            }
            .padding(top = if (tightWithPrev) 1.dp else 6.dp),
        horizontalAlignment = align
    ) {
        Box(
            Modifier.widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = if (outgoing) cornerSelf else cornerTop,
                        topEnd = if (outgoing) cornerTop else cornerSelf,
                        bottomStart = if (outgoing) cornerSelf else cornerTail,
                        bottomEnd = if (outgoing) cornerTail else cornerSelf
                    )
                )
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Column {
                if (quoted != null) {
                    ReplyQuoteChip(info = quoted, outgoing = outgoing, modifier = Modifier.padding(bottom = 5.dp))
                }
                Text(msg.displayBody, color = fg, style = MaterialTheme.typography.bodyMedium)
                val currentPreview = preview
                if (currentPreview != null) {
                    LinkPreviewCard(currentPreview, outgoing, Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatChatTime(msg.timestamp),
                        color = sub,
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (outgoing) {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            if (msg.delivered) "✓✓" else "·",
                            color = sub,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
        ReactionPill(reactions)
    }
}

@Composable
private fun ReactionPill(reactions: List<dev.stade.db.MessageReaction>) {
    if (reactions.isEmpty()) return
    val emoji = reactions.first().emoji
    Box(
        Modifier
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            if (reactions.size > 1) "$emoji ${reactions.size}" else emoji,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun LinkPreviewCard(preview: LinkPreview, outgoing: Boolean, modifier: Modifier = Modifier) {
    val bg = if (outgoing) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (outgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val domain = preview.url.removePrefix("https://").removePrefix("http://").substringBefore("/").substringBefore("?")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            preview.title,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (preview.description.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                preview.description,
                color = fg.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            domain,
            color = fg.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageBubble(
    msg: Message,
    tightWithPrev: Boolean,
    selected: Boolean,
    highlighted: Boolean,
    inSelectionMode: Boolean,
    quoted: ReplyQuoteInfo?,
    reactions: List<dev.stade.db.MessageReaction>,
    onShortClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleTap: () -> Unit,
    onSaveImage: (ByteArray) -> Unit,
    onCopyImage: (ByteArray) -> Unit
) {
    val strings = LocalStrings.current
    val outgoing = msg.direction == MessageDirection.OUT
    val align = if (outgoing) Alignment.End else Alignment.Start
    val bg = if (outgoing) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (outgoing) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    val sub = fg.copy(alpha = if (outgoing) 0.75f else 0.55f)

    val cornerTop = if (tightWithPrev) 6.dp else 18.dp
    val cornerSelf = 18.dp
    val cornerTail = if (tightWithPrev) 18.dp else 4.dp

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

    val tintTarget = when {
        highlighted -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    val tint by animateColorAsState(tintTarget)

    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnTap by rememberUpdatedState {
        if (inSelectionMode) onShortClick()
        else if (bitmap != null) showFullscreen = true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onLongPress = { currentOnLongClick() },
                    onDoubleTap = { currentOnDoubleTap() }
                )
            }
            .padding(top = if (tightWithPrev) 1.dp else 6.dp),
        horizontalAlignment = align
    ) {
        Box(
            Modifier.widthIn(max = 240.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = if (outgoing) cornerSelf else cornerTop,
                        topEnd = if (outgoing) cornerTop else cornerSelf,
                        bottomStart = if (outgoing) cornerSelf else cornerTail,
                        bottomEnd = if (outgoing) cornerTail else cornerSelf
                    )
                )
                .background(bg)
                .padding(4.dp)
        ) {
            Column {
                if (quoted != null) {
                    ReplyQuoteChip(info = quoted, outgoing = outgoing, modifier = Modifier.padding(bottom = 4.dp))
                }
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
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.BrokenImage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    strings.photoSendFailed,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (msg.caption.isNotEmpty()) {
                    Text(
                        msg.caption,
                        color = fg,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatChatTime(msg.timestamp),
                        color = sub,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (outgoing) {
                        Spacer(Modifier.size(4.dp))
                        Text(
                            if (msg.delivered) "✓✓" else "·",
                            color = sub,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
        ReactionPill(reactions)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = { onSaveImage(currentBytes) }) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = strings.saveImageAction,
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { onCopyImage(currentBytes) }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = strings.copyImageAction,
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { showFullscreen = false }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = strings.closePhoto,
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VoiceBubble(
    msg: Message,
    tightWithPrev: Boolean,
    selected: Boolean,
    highlighted: Boolean,
    inSelectionMode: Boolean,
    quoted: ReplyQuoteInfo?,
    reactions: List<dev.stade.db.MessageReaction>,
    onShortClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleTap: () -> Unit
) {
    val outgoing = msg.direction == MessageDirection.OUT
    val align = if (outgoing) Alignment.End else Alignment.Start
    val bg = if (outgoing) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (outgoing) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    val sub = fg.copy(alpha = if (outgoing) 0.75f else 0.55f)

    val cornerTop = if (tightWithPrev) 6.dp else 18.dp
    val cornerSelf = 18.dp
    val cornerTail = if (tightWithPrev) 18.dp else 4.dp

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

    val tintTarget = when {
        highlighted -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    val tint by animateColorAsState(tintTarget)

    val currentOnShortClick by rememberUpdatedState(onShortClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnShortClick() },
                    onLongPress = { currentOnLongClick() },
                    onDoubleTap = { currentOnDoubleTap() }
                )
            }
            .padding(top = if (tightWithPrev) 1.dp else 6.dp),
        horizontalAlignment = align
    ) {
        Box(
            Modifier.widthIn(max = 260.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = if (outgoing) cornerSelf else cornerTop,
                        topEnd = if (outgoing) cornerTop else cornerSelf,
                        bottomStart = if (outgoing) cornerSelf else cornerTail,
                        bottomEnd = if (outgoing) cornerTail else cornerSelf
                    )
                )
                .background(bg)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column {
                if (quoted != null) {
                    ReplyQuoteChip(info = quoted, outgoing = outgoing, modifier = Modifier.padding(bottom = 4.dp))
                }
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
                            Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(sub.copy(alpha = 0.3f))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(progress)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(fg)
                            )
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatChatTime(msg.timestamp),
                        color = sub,
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (outgoing) {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            if (msg.delivered) "✓✓" else "·",
                            color = sub,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
        ReactionPill(reactions)
    }
}

@Composable
private fun VideoBubble(
    msg: Message,
    tightWithPrev: Boolean,
    selected: Boolean,
    highlighted: Boolean,
    inSelectionMode: Boolean,
    quoted: ReplyQuoteInfo?,
    reactions: List<dev.stade.db.MessageReaction>,
    onShortClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleTap: () -> Unit,
    onPlayVideo: (ByteArray) -> Unit
) {
    val strings = LocalStrings.current
    val outgoing = msg.direction == MessageDirection.OUT
    val align = if (outgoing) Alignment.End else Alignment.Start
    val bg = if (outgoing) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (outgoing) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    val sub = fg.copy(alpha = if (outgoing) 0.75f else 0.55f)

    val cornerTop = if (tightWithPrev) 6.dp else 18.dp
    val cornerSelf = 18.dp
    val cornerTail = if (tightWithPrev) 18.dp else 4.dp

    var videoBytes by remember(msg.id) { mutableStateOf<ByteArray?>(null) }
    var decodeDone by remember(msg.id) { mutableStateOf(false) }
    LaunchedEffect(msg.id) {
        val bytes = withContext(Dispatchers.Default) { runCatching { msg.videoBytes() }.getOrNull() }
        videoBytes = bytes
        decodeDone = true
    }
    val currentBytes = videoBytes

    val tintTarget = when {
        highlighted -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    val tint by animateColorAsState(tintTarget)

    val currentOnShortClick by rememberUpdatedState(onShortClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnShortClick() },
                    onLongPress = { currentOnLongClick() },
                    onDoubleTap = { currentOnDoubleTap() }
                )
            }
            .padding(top = if (tightWithPrev) 1.dp else 6.dp),
        horizontalAlignment = align
    ) {
        Box(
            Modifier.widthIn(max = 260.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = if (outgoing) cornerSelf else cornerTop,
                        topEnd = if (outgoing) cornerTop else cornerSelf,
                        bottomStart = if (outgoing) cornerSelf else cornerTail,
                        bottomEnd = if (outgoing) cornerTail else cornerSelf
                    )
                )
                .background(bg)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column {
                if (quoted != null) {
                    ReplyQuoteChip(info = quoted, outgoing = outgoing, modifier = Modifier.padding(bottom = 4.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = currentBytes != null) { currentBytes?.let(onPlayVideo) }
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
                if (msg.caption.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(msg.caption, color = fg, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatChatTime(msg.timestamp),
                        color = sub,
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (outgoing) {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            if (msg.delivered) "✓✓" else "·",
                            color = sub,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
        ReactionPill(reactions)
    }
}

