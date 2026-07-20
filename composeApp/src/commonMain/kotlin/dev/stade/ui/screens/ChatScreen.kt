package dev.stade.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.stade.AppContainer
import dev.stade.audio.RecordedClip
import dev.stade.audio.rememberAudioPermissionState
import dev.stade.audio.rememberAudioPlayer
import dev.stade.audio.rememberAudioRecorder
import dev.stade.identity.LocalIdentity
import dev.stade.message.IMAGE_BODY_PREFIX
import dev.stade.message.Message
import dev.stade.message.MessageDirection
import dev.stade.message.MessageType
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
import dev.stade.ui.rememberMultiImagePickerLauncher
import dev.stade.ui.saveImageToGallery
import dev.stade.ui.theme.StadeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class NotificationKind { Success, Error, Info }
private data class NotificationData(val message: String, val kind: NotificationKind)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    container: AppContainer,
    owner: LocalIdentity,
    contactId: String,
    onBack: (() -> Unit)?,
    onVerify: () -> Unit,
    onContactDeleted: (() -> Unit)? = null
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val contact = remember(contactId) { container.contacts.get(contactId) }
    val messages by container.messages.observeMessages(contactId).collectAsState(initial = emptyList())
    val connected by container.sync.connectedContacts.collectAsState()
    val isOnline by remember(contactId) { derivedStateOf { connected.contains(contactId) } }
    val diagnostics by container.connections.diagnostics.collectAsState()
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var diagnosticsExpanded by remember(contactId) { mutableStateOf(false) }

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
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            if (prevMessageCount == 0) {
                listState.scrollToItem(messages.lastIndex)
            } else {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
        prevMessageCount = messages.size
    }

    val MAX_IMAGE_BYTES = 3 * 1024 * 1024 // 3 MB

    var pendingImages by remember { mutableStateOf<List<ByteArray>>(emptyList()) }

    val imagePicker = rememberMultiImagePickerLauncher { imagesList ->
        val accepted = imagesList.filter { it.size <= MAX_IMAGE_BYTES }
        if (accepted.size != imagesList.size) {
            showNotification(strings.photoTooBig, NotificationKind.Error)
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
                                clipboard.setText(AnnotatedString(singleSelectedTextMsg.body))
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                        IconButton(onClick = onVerify) {
                            Icon(Icons.Default.Verified, contentDescription = strings.verifyAction)
                        }
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
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
        ) {
            Column(modifier = Modifier.fillMaxSize().onSizeChanged { size ->
                if (size.height < prevColumnHeight && messages.isNotEmpty()) {
                    scope.launch { listState.scrollToItem(messages.lastIndex) }
                }
                prevColumnHeight = size.height
            }) {
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
                        onClear = {
                            scope.launch {
                                runCatching { container.contacts.setAddresses(contact.id, emptyList()) }
                                showNotification(strings.addressesCleared, NotificationKind.Info)
                            }
                        }
                    )
                }

                if (messages.isEmpty()) {
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
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        itemsIndexed(messages, key = { _, msg -> msg.id }) { idx, msg ->
                            val prev = messages.getOrNull(idx - 1)
                            val tight = prev != null &&
                                    prev.direction == msg.direction &&
                                    (msg.timestamp - prev.timestamp) < 60_000L
                            val isSelected = selectedMessageIds.contains(msg.id)
                            if (msg.type == MessageType.IMAGE) {
                                ImageBubble(
                                    msg = msg,
                                    tightWithPrev = tight,
                                    selected = isSelected,
                                    inSelectionMode = inSelectionMode,
                                    onShortClick = { if (inSelectionMode) toggleSelection(msg.id) },
                                    onLongClick = { toggleSelection(msg.id) },
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
                                    inSelectionMode = inSelectionMode,
                                    onShortClick = { if (inSelectionMode) toggleSelection(msg.id) },
                                    onLongClick = { toggleSelection(msg.id) }
                                )
                            } else {
                                Bubble(
                                    msg = msg,
                                    tightWithPrev = tight,
                                    selected = isSelected,
                                    inSelectionMode = inSelectionMode,
                                    onShortClick = { if (inSelectionMode) toggleSelection(msg.id) },
                                    onLongClick = { toggleSelection(msg.id) }
                                )
                            }
                        }
                    }
                }

                Composer(
                    draft = draft,
                    pendingImages = pendingImages,
                    pendingVoiceClip = pendingVoiceClip,
                    isRecording = isRecording,
                    onChange = { draft = it },
                    onRemoveImage = { idx ->
                        pendingImages = pendingImages.toMutableList().also { it.removeAt(idx) }
                    },
                    onRemoveVoiceClip = { pendingVoiceClip = null },
                    onSend = {
                        val c = contact ?: return@Composer
                        val text = draft.text.trim()
                        val images = pendingImages
                        val voiceClip = pendingVoiceClip
                        if (text.isEmpty() && images.isEmpty() && voiceClip == null) return@Composer
                        draft = TextFieldValue("")
                        pendingImages = emptyList()
                        pendingVoiceClip = null
                        scope.launch {
                            if (text.isNotEmpty()) {
                                runCatching { container.chat.send(owner, c, text) }
                                    .onFailure { showNotification(strings.sendFailed(it.message ?: ""), NotificationKind.Error) }
                            }
                            images.forEach { imageBytes ->
                                runCatching { container.chat.sendImage(owner, c, imageBytes) }
                                    .onFailure { showNotification(strings.photoSendFailed, NotificationKind.Error) }
                            }
                            if (voiceClip != null) {
                                runCatching { container.chat.sendVoice(owner, c, voiceClip.opusBytes, voiceClip.durationMs) }
                                    .onFailure { showNotification(strings.voiceSendFailed, NotificationKind.Error) }
                            }
                        }
                    },
                    onPickImage = { imagePicker.launch() },
                    onToggleRecording = { toggleRecording() }
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
    pendingVoiceClip: RecordedClip?,
    isRecording: Boolean,
    onChange: (TextFieldValue) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onRemoveVoiceClip: () -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    onToggleRecording: () -> Unit
) {
    val strings = LocalStrings.current
    val canSend = draft.text.isNotBlank() || pendingImages.isNotEmpty() || pendingVoiceClip != null

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
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
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

            IconButton(
                onClick = onToggleRecording,
                modifier = Modifier.size(54.dp)
            ) {
                Icon(
                    if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) strings.stopRecording else strings.recordVoice,
                    tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }

            FilledIconButton(
                enabled = canSend,
                onClick = onSend,
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = strings.sendButton,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}




@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    msg: Message,
    tightWithPrev: Boolean,
    selected: Boolean,
    inSelectionMode: Boolean,
    onShortClick: () -> Unit,
    onLongClick: () -> Unit
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

    val selectionTint = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(selectionTint)
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
                .combinedClickable(
                    onClick = onShortClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Column {
                Text(msg.body, color = fg, style = MaterialTheme.typography.bodyMedium)
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageBubble(
    msg: Message,
    tightWithPrev: Boolean,
    selected: Boolean,
    inSelectionMode: Boolean,
    onShortClick: () -> Unit,
    onLongClick: () -> Unit,
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

    val selectionTint = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(selectionTint)
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
                .combinedClickable(
                    onClick = {
                        if (inSelectionMode) onShortClick()
                        else if (bitmap != null) showFullscreen = true
                    },
                    onLongClick = onLongClick
                )
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
    inSelectionMode: Boolean,
    onShortClick: () -> Unit,
    onLongClick: () -> Unit
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

    val selectionTint = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(selectionTint)
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
                .combinedClickable(
                    onClick = onShortClick,
                    onLongClick = onLongClick
                )
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
    }
}

