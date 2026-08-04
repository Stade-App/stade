package dev.stade.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import dev.stade.group.GroupMessage
import dev.stade.identity.LocalIdentity
import dev.stade.link.LinkPreview
import dev.stade.link.extractFirstUrl
import dev.stade.link.fetchLinkPreview
import dev.stade.link.getLinkPreviewsEnabled
import dev.stade.media.MediaEditorDialog
import dev.stade.message.MAX_ATTACHMENT_BYTES
import dev.stade.message.MessageType
import dev.stade.message.previewBody
import dev.stade.ui.PlatformBackHandler
import dev.stade.ui.components.Avatar
import dev.stade.ui.components.ChatComposerBar
import dev.stade.ui.components.ChatComposerReplyPreview
import dev.stade.ui.components.EmojiStickerDrawer
import dev.stade.ui.components.StickerMakerDialog
import dev.stade.ui.components.formatChatTime
import dev.stade.ui.components.formatVoiceDuration
import dev.stade.ui.copyImageToClipboard
import dev.stade.ui.decodeToImageBitmap
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.rememberMediaPickerLauncher
import dev.stade.ui.saveImageToGallery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class GroupBannerKind { Success, Error, Info }
private data class GroupBannerData(val message: String, val kind: GroupBannerKind)
private const val DEFAULT_REACTION_EMOJI = "❤️"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    container: AppContainer,
    owner: LocalIdentity,
    groupId: String,
    highlightMessageId: String? = null,
    onBack: () -> Unit,
    onOpenMembers: (() -> Unit)? = null
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    val group = remember(groupId) { container.groups.getGroup(groupId) }
    val memberIds by remember(groupId) { container.groups.observeMembers(groupId) }
        .collectAsState(initial = group?.memberIds ?: emptyList())
    val rawMessages by remember(groupId) { container.groups.observeMessages(groupId) }.collectAsState(initial = null)
    val messages = rawMessages ?: emptyList()
    val contacts by remember(owner.id) { container.contacts.observeContacts(owner.id) }.collectAsState(initial = emptyList())
    val listState = rememberLazyListState()
    val linkPreviewsEnabled = remember { getLinkPreviewsEnabled(container.db) }
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var menuOpen by remember { mutableStateOf(false) }

    val isOwner = remember(group?.creatorStadeId) {
        group != null && (group.creatorStadeId == owner.stadeId || group.creatorStadeId.isEmpty())
    }

    LaunchedEffect(groupId) {
        container.sync.events.collect { event ->
            if (event is dev.stade.sync.SyncEngine.SyncEvent.RemovedFromGroup && event.groupId == groupId) {
                onBack()
            }
        }
    }

    var showAddMembersDialog by remember { mutableStateOf(false) }
    var showDeleteGroupDialog by remember { mutableStateOf(false) }
    var showLeaveGroupDialog by remember { mutableStateOf(false) }
    var showEmojiDrawer by remember { mutableStateOf(false) }
    var showStickerMaker by remember { mutableStateOf(false) }
    val stickers by remember(owner.id) { container.stickers.observeStickers(owner.id) }.collectAsState(initial = emptyList())

    var selectedMessageIds by remember(groupId) { mutableStateOf<Set<String>>(emptySet()) }
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
        val g = group ?: return
        scope.launch {
            withContext(Dispatchers.Default) {
                if (mine) {
                    container.messages.deleteReaction(targetId, owner.id)
                    runCatching { container.groupChat.sendReaction(owner, g, targetId, false, DEFAULT_REACTION_EMOJI) }
                } else {
                    container.messages.upsertReaction(targetId, owner.id, DEFAULT_REACTION_EMOJI)
                    runCatching { container.groupChat.sendReaction(owner, g, targetId, true, DEFAULT_REACTION_EMOJI) }
                }
            }
        }
    }

    var banner by remember { mutableStateOf<GroupBannerData?>(null) }
    var bannerKey by remember { mutableStateOf(0) }
    LaunchedEffect(bannerKey) {
        if (bannerKey > 0) {
            delay(3500L)
            banner = null
        }
    }
    fun notify(message: String, kind: GroupBannerKind = GroupBannerKind.Info) {
        banner = GroupBannerData(message, kind)
        bannerKey++
    }

    DisposableEffect(groupId) {
        container.groups.markRead(groupId)
        container.activeContactId = groupId
        onDispose {
            container.groups.markRead(groupId)
            container.activeContactId = null
        }
    }

    LaunchedEffect(groupId, messages.size) {
        container.groups.markRead(groupId)
    }

    var prevMessageCount by remember { mutableStateOf(0) }
    var scrollReady by remember(groupId) { mutableStateOf(false) }
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
    var pendingVideo by remember { mutableStateOf<ByteArray?>(null) }

    val mediaPicker = rememberMediaPickerLauncher(
        onImages = { imagesList ->
            val accepted = imagesList.filter { it.size <= MAX_ATTACHMENT_BYTES }
            if (accepted.size != imagesList.size) {
                notify(strings.photoTooBig, GroupBannerKind.Error)
            }
            if (accepted.isNotEmpty()) {
                pendingImages = pendingImages + accepted
            }
        },
        onVideo = { bytes ->
            if (bytes.size <= MAX_ATTACHMENT_BYTES) {
                pendingVideo = bytes
            } else {
                notify(strings.videoTooBig, GroupBannerKind.Error)
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
            notify(strings.voiceMaxDurationReached, GroupBannerKind.Info)
        } else {
            notify(strings.voiceSendFailed, GroupBannerKind.Error)
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
                    notify(strings.voiceSendFailed, GroupBannerKind.Error)
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

    var replyTarget by remember { mutableStateOf<GroupMessage?>(null) }

    var prevColumnHeight by remember { mutableStateOf(Int.MAX_VALUE) }

    if (showAddMembersDialog && group != null) {
        val currentMembers = remember(showAddMembersDialog) {
            container.groups.getMembers(groupId).toSet()
        }
        val candidates = remember(contacts, currentMembers) {
            contacts.filter { it.id !in currentMembers }
        }
        var selectedContactIds by remember { mutableStateOf<Set<String>>(emptySet()) }
        AlertDialog(
            onDismissRequest = { showAddMembersDialog = false },
            icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
            title = { Text(strings.addMembersTitle) },
            text = {
                if (candidates.isEmpty()) {
                    Text(strings.noContactsToAdd, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(strings.addMembersHint, style = MaterialTheme.typography.labelMedium)
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            itemsIndexed(candidates, key = { _, c -> c.id }) { _, contact ->
                                val checked = selectedContactIds.contains(contact.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            selectedContactIds = if (checked)
                                                selectedContactIds - contact.id
                                            else
                                                selectedContactIds + contact.id
                                        }
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box {
                                        Avatar(name = contact.nickname, size = 36.dp)
                                        if (checked) {
                                            Box(
                                                Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .padding(2.dp)
                                            ) {
                                                Box(
                                                    Modifier
                                                        .fillMaxSize()
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(contact.nickname, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedContactIds.isNotEmpty(),
                    onClick = {
                        val toInvite = selectedContactIds.toList()
                        showAddMembersDialog = false
                        scope.launch {
                            withContext(Dispatchers.Default) {
                                val inviteCode = container.groups.generateInviteLink(
                                    groupId = group.id,
                                    groupName = group.name,
                                    inviteToken = group.inviteToken,
                                    creatorStadeId = group.creatorStadeId.ifEmpty { owner.stadeId }
                                )
                                toInvite.forEach { cid ->
                                    runCatching {
                                        container.groupChat.sendGroupInviteToContact(owner, cid, inviteCode)
                                    }
                                }
                            }
                            notify(strings.membersAdded(toInvite.size), GroupBannerKind.Success)
                        }
                    }
                ) { Text(strings.addMembersAction) }
            },
            dismissButton = {
                TextButton(onClick = { showAddMembersDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showDeleteGroupDialog && group != null) {
        AlertDialog(
            onDismissRequest = { showDeleteGroupDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(strings.deleteGroupTitle) },
            text = { Text(strings.deleteGroupBody, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteGroupDialog = false
                    scope.launch {
                        withContext(Dispatchers.Default) {
                            runCatching { container.groups.deleteGroup(groupId) }
                        }
                        onBack()
                    }
                }) { Text(strings.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGroupDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showLeaveGroupDialog && group != null) {
        AlertDialog(
            onDismissRequest = { showLeaveGroupDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(strings.leaveGroupTitle) },
            text = { Text(strings.leaveGroupBody, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveGroupDialog = false
                    scope.launch {
                        withContext(Dispatchers.Default) {
                            runCatching { container.groupChat.leaveGroup(owner, group) }
                        }
                        onBack()
                    }
                }) { Text(strings.leaveAction, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveGroupDialog = false }) { Text(strings.cancel) }
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
                            runCatching { container.groups.deleteGroupMessages(toDelete) }
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

    if (showEmojiDrawer) {
        EmojiStickerDrawer(
            stickers = stickers,
            onDismiss = { showEmojiDrawer = false },
            onSend = { bytes -> scope.launch { container.groupChat.sendSticker(owner, groupId, bytes) } },
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
                    .onFailure { notify(strings.stickerCreationFailed, GroupBannerKind.Error) }
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
                                notify(strings.messageCopied, GroupBannerKind.Success)
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
                                        .onSuccess { notify(strings.stickerSavedToPack, GroupBannerKind.Success) }
                                        .onFailure { notify(strings.stickerCreationFailed, GroupBannerKind.Error) }
                                }
                            }) {
                                Icon(Icons.Default.Download, contentDescription = strings.saveStickerToPackAction)
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
                            modifier = if (onOpenMembers != null && group != null) {
                                Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { onOpenMembers() }
                                    .padding(vertical = 6.dp, horizontal = 8.dp)
                            } else Modifier
                        ) {
                            Avatar(name = group?.name ?: groupId, size = 36.dp, icon = Icons.Default.Group)
                            Spacer(Modifier.size(10.dp))
                            Column {
                                Text(
                                    group?.name ?: groupId,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (group != null) {
                                    Text(
                                        strings.groupMemberCount(memberIds.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = null)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (onOpenMembers != null) {
                                    DropdownMenuItem(
                                        text = { Text(strings.viewMembersAction) },
                                        leadingIcon = { Icon(Icons.Default.Group, null) },
                                        onClick = {
                                            menuOpen = false
                                            onOpenMembers()
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(strings.addMembersTitle) },
                                    leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                                    onClick = {
                                        menuOpen = false
                                        showAddMembersDialog = true
                                    }
                                )
                                if (isOwner) {
                                    DropdownMenuItem(
                                        text = { Text(strings.deleteGroupTitle, color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            menuOpen = false
                                            showDeleteGroupDialog = true
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(strings.leaveGroupAction, color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            menuOpen = false
                                            showLeaveGroupDialog = true
                                        }
                                    )
                                }
                            }
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
                if (rawMessages == null) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth())
                } else if (messages.isEmpty()) {
                    Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
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
                                    prev.senderId == msg.senderId &&
                                    (msg.timestamp - prev.timestamp) < 60_000L
                            val senderName = remember(msg.senderId) {
                                if (msg.isOwn) owner.nickname
                                else container.contacts.get(msg.senderId)?.nickname ?: msg.senderId.takeLast(6)
                            }
                            val isSelected by remember(msg.id) { derivedStateOf { selectedMessageIds.contains(msg.id) } }
                            val isHighlighted = flashedMessageId == msg.id
                            val reactions by remember(msg.id) { container.messages.observeReactionsForMessage(msg.id) }.collectAsState(initial = emptyList())
                            val quotedMsg = remember(msg.id, msg.replyToId, messagesById) {
                                msg.replyToId?.let { rid -> messagesById[rid] }
                            }
                            val quoted = remember(msg.id, quotedMsg) {
                                when {
                                    msg.replyToId == null -> null
                                    quotedMsg != null -> {
                                        val quotedSenderName = if (quotedMsg.isOwn) owner.nickname
                                            else container.contacts.get(quotedMsg.senderId)?.nickname ?: quotedMsg.senderId.takeLast(6)
                                        GroupReplyQuoteInfo(
                                            senderLabel = quotedSenderName,
                                            snippet = previewBody(quotedMsg.displayBody, strings.photoMessage, strings.voiceMessage, strings.videoMessage, strings.stickerMessage)
                                        ) {
                                            val target = messages.indexOfFirst { it.id == quotedMsg.id }
                                            if (target >= 0) scope.launch { listState.animateScrollToItem(target) }
                                        }
                                    }
                                    else -> GroupReplyQuoteInfo(
                                        senderLabel = "",
                                        snippet = strings.originalMessageUnavailable,
                                        onClick = {}
                                    )
                                }
                            }
                            GroupSwipeToReplyRow(
                                enabled = !inSelectionMode,
                                onReply = { replyTarget = msg }
                            ) {
                                if (msg.type == MessageType.IMAGE) {
                                    GroupImageBubble(
                                        msg = msg,
                                        senderName = senderName,
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
                                                notify(
                                                    if (ok) strings.imageSaved else strings.imageSaveFailed,
                                                    if (ok) GroupBannerKind.Success else GroupBannerKind.Error
                                                )
                                            }
                                        },
                                        onCopyImage = { bytes ->
                                            scope.launch {
                                                val ok = copyImageToClipboard(bytes)
                                                notify(
                                                    if (ok) strings.imageCopied else strings.imageCopyFailed,
                                                    if (ok) GroupBannerKind.Success else GroupBannerKind.Error
                                                )
                                            }
                                        }
                                    )
                                } else if (msg.type == MessageType.VOICE) {
                                    GroupVoiceBubble(
                                        msg = msg,
                                        senderName = senderName,
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
                                    GroupVideoBubble(
                                        msg = msg,
                                        senderName = senderName,
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
                                } else if (msg.type == MessageType.STICKER) {
                                    GroupStickerBubble(
                                        msg = msg,
                                        senderName = senderName,
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
                                } else {
                                    GroupTextBubble(
                                        msg = msg,
                                        senderName = senderName,
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

                ChatComposerBar(
                    draft = draft,
                    pendingImages = pendingImages,
                    pendingVideo = pendingVideo,
                    pendingVoiceClip = pendingVoiceClip,
                    isRecording = isRecording,
                    replyPreview = replyTarget?.let { target ->
                        val targetSenderName = if (target.isOwn) owner.nickname
                            else container.contacts.get(target.senderId)?.nickname ?: target.senderId.takeLast(6)
                        ChatComposerReplyPreview(
                            senderLabel = targetSenderName,
                            snippet = previewBody(target.displayBody, strings.photoMessage, strings.voiceMessage, strings.videoMessage, strings.stickerMessage)
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
                        val text = draft.text.trim()
                        val images = pendingImages
                        val video = pendingVideo
                        val voiceClip = pendingVoiceClip
                        val replyId = replyTarget?.id
                        if (text.isEmpty() && images.isEmpty() && video == null && voiceClip == null) return@ChatComposerBar
                        draft = TextFieldValue("")
                        pendingImages = emptyList()
                        pendingVideo = null
                        pendingVoiceClip = null
                        replyTarget = null
                        val hasMedia = images.isNotEmpty() || video != null
                        scope.launch {
                            if (!hasMedia && text.isNotEmpty()) {
                                runCatching { container.groupChat.sendMessage(owner, groupId, text, replyId) }
                                    .onFailure { notify(strings.sendFailed(it.message ?: ""), GroupBannerKind.Error) }
                            }
                            images.forEachIndexed { idx, imageBytes ->
                                runCatching { container.groupChat.sendImage(owner, groupId, imageBytes, replyId, if (idx == 0) text else "") }
                                    .onFailure { notify(strings.photoSendFailed, GroupBannerKind.Error) }
                            }
                            if (video != null) {
                                runCatching { container.groupChat.sendVideo(owner, groupId, video, replyId, if (images.isEmpty()) text else "") }
                                    .onFailure { notify(strings.videoSendFailed, GroupBannerKind.Error) }
                            }
                            if (voiceClip != null) {
                                runCatching { container.groupChat.sendVoice(owner, groupId, voiceClip.opusBytes, voiceClip.durationMs, replyId) }
                                    .onFailure { notify(strings.voiceSendFailed, GroupBannerKind.Error) }
                            }
                        }
                    },
                    onPickMedia = { mediaPicker.launch() },
                    onToggleRecording = { toggleRecording() },
                    onOpenEmojiPicker = { showEmojiDrawer = true }
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

            GroupTopBanner(
                data = banner,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun GroupTopBanner(
    data: GroupBannerData?,
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
            GroupBannerKind.Success -> Triple(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
                Icons.Default.CheckCircle
            )
            GroupBannerKind.Error -> Triple(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                Icons.Default.Error
            )
            GroupBannerKind.Info -> Triple(
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
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
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

private data class GroupReplyQuoteInfo(
    val senderLabel: String,
    val snippet: String,
    val onClick: () -> Unit
)

@Composable
private fun GroupSwipeToReplyRow(
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
private fun GroupReplyQuoteChip(
    info: GroupReplyQuoteInfo,
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

@Composable
private fun GroupReactionPill(reactions: List<dev.stade.db.MessageReaction>) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupTextBubble(
    msg: GroupMessage,
    senderName: String,
    tightWithPrev: Boolean,
    selected: Boolean,
    highlighted: Boolean,
    inSelectionMode: Boolean,
    quoted: GroupReplyQuoteInfo?,
    reactions: List<dev.stade.db.MessageReaction>,
    onShortClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleTap: () -> Unit,
    container: AppContainer,
    linkPreviewsEnabled: Boolean
) {
    val outgoing = msg.isOwn
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
                if (!outgoing && !tightWithPrev) {
                    Text(
                        senderName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                }
                if (quoted != null) {
                    GroupReplyQuoteChip(info = quoted, outgoing = outgoing, modifier = Modifier.padding(bottom = 5.dp))
                }
                Text(msg.displayBody, color = fg, style = MaterialTheme.typography.bodyMedium)
                val currentPreview = preview
                if (currentPreview != null) {
                    GroupLinkPreviewCard(currentPreview, outgoing, Modifier.padding(top = 6.dp))
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
        GroupReactionPill(reactions)
    }
}

@Composable
private fun GroupStickerBubble(
    msg: GroupMessage,
    senderName: String,
    tightWithPrev: Boolean,
    selected: Boolean,
    highlighted: Boolean,
    inSelectionMode: Boolean,
    quoted: GroupReplyQuoteInfo?,
    reactions: List<dev.stade.db.MessageReaction>,
    onShortClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleTap: () -> Unit
) {
    val outgoing = msg.isOwn
    val align = if (outgoing) Alignment.End else Alignment.Start

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
        if (!outgoing && !tightWithPrev) {
            Text(
                senderName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(2.dp))
        }
        if (quoted != null) {
            GroupReplyQuoteChip(info = quoted, outgoing = outgoing, modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 2.dp))
        }
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else if (decodeDone) {
                Icon(Icons.Default.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            }
        }
        GroupReactionPill(reactions)
    }
}

@Composable
private fun GroupLinkPreviewCard(preview: LinkPreview, outgoing: Boolean, modifier: Modifier = Modifier) {
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
private fun GroupImageBubble(
    msg: GroupMessage,
    senderName: String,
    tightWithPrev: Boolean,
    selected: Boolean,
    highlighted: Boolean,
    inSelectionMode: Boolean,
    quoted: GroupReplyQuoteInfo?,
    reactions: List<dev.stade.db.MessageReaction>,
    onShortClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleTap: () -> Unit,
    onSaveImage: (ByteArray) -> Unit,
    onCopyImage: (ByteArray) -> Unit
) {
    val strings = LocalStrings.current
    val outgoing = msg.isOwn
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
                if (!outgoing && !tightWithPrev) {
                    Text(
                        senderName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 4.dp)
                    )
                }
                if (quoted != null) {
                    GroupReplyQuoteChip(info = quoted, outgoing = outgoing, modifier = Modifier.padding(bottom = 4.dp))
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
                }
            }
        }
        GroupReactionPill(reactions)
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
private fun GroupVoiceBubble(
    msg: GroupMessage,
    senderName: String,
    tightWithPrev: Boolean,
    selected: Boolean,
    highlighted: Boolean,
    inSelectionMode: Boolean,
    quoted: GroupReplyQuoteInfo?,
    reactions: List<dev.stade.db.MessageReaction>,
    onShortClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleTap: () -> Unit
) {
    val outgoing = msg.isOwn
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
                if (!outgoing && !tightWithPrev) {
                    Text(
                        senderName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                }
                if (quoted != null) {
                    GroupReplyQuoteChip(info = quoted, outgoing = outgoing, modifier = Modifier.padding(bottom = 4.dp))
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
                Text(
                    formatChatTime(msg.timestamp),
                    color = sub,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
        GroupReactionPill(reactions)
    }
}

@Composable
private fun GroupVideoBubble(
    msg: GroupMessage,
    senderName: String,
    tightWithPrev: Boolean,
    selected: Boolean,
    highlighted: Boolean,
    inSelectionMode: Boolean,
    quoted: GroupReplyQuoteInfo?,
    reactions: List<dev.stade.db.MessageReaction>,
    onShortClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleTap: () -> Unit
) {
    val strings = LocalStrings.current
    val outgoing = msg.isOwn
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
    var expanded by remember(msg.id) { mutableStateOf(false) }
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
                if (!outgoing && !tightWithPrev) {
                    Text(
                        senderName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                }
                if (quoted != null) {
                    GroupReplyQuoteChip(info = quoted, outgoing = outgoing, modifier = Modifier.padding(bottom = 4.dp))
                }
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
                            .clickable(enabled = currentBytes != null) { expanded = true }
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
        GroupReactionPill(reactions)
    }
}

