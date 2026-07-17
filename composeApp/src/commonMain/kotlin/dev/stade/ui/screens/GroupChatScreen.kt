package dev.stade.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.stade.AppContainer
import dev.stade.group.GroupMessage
import dev.stade.identity.LocalIdentity
import dev.stade.message.MessageType
import dev.stade.ui.PlatformBackHandler
import dev.stade.ui.components.formatChatTime
import dev.stade.ui.copyImageToClipboard
import dev.stade.ui.decodeToImageBitmap
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.rememberMultiImagePickerLauncher
import dev.stade.ui.saveImageToGallery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class GroupBannerKind { Success, Error, Info }
private data class GroupBannerData(val message: String, val kind: GroupBannerKind)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    container: AppContainer,
    owner: LocalIdentity,
    groupId: String,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val group = remember(groupId) { container.groups.getGroup(groupId) }
    val messages by container.groups.observeMessages(groupId).collectAsState(initial = emptyList())
    val contacts by container.contacts.observeContacts(owner.id).collectAsState(initial = emptyList())
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var menuOpen by remember { mutableStateOf(false) }

    val isOwner = remember(group?.creatorStadeId) {
        group != null && (group.creatorStadeId == owner.stadeId || group.creatorStadeId.isEmpty())
    }

    var showAddMembersDialog by remember { mutableStateOf(false) }
    var showDeleteGroupDialog by remember { mutableStateOf(false) }
    var showLeaveGroupDialog by remember { mutableStateOf(false) }

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
        onDispose { container.groups.markRead(groupId) }
    }

    LaunchedEffect(groupId, messages.size) {
        container.groups.markRead(groupId)
    }

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

    val MAX_IMAGE_BYTES = 3 * 1024 * 1024

    var pendingImages by remember { mutableStateOf<List<ByteArray>>(emptyList()) }

    val imagePicker = rememberMultiImagePickerLauncher { imagesList ->
        val accepted = imagesList.filter { it.size <= MAX_IMAGE_BYTES }
        if (accepted.size != imagesList.size) {
            notify(strings.photoTooBig, GroupBannerKind.Error)
        }
        if (accepted.isNotEmpty()) {
            pendingImages = pendingImages + accepted
        }
    }

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
                                    Box(
                                        Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (checked) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.secondaryContainer
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (checked) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Text(
                                                contact.nickname.firstOrNull()?.uppercase() ?: "?",
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                style = MaterialTheme.typography.titleSmall
                                            )
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
                            runCatching { container.groups.leaveGroupLocally(groupId) }
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
                                notify(strings.messageCopied, GroupBannerKind.Success)
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
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.size(10.dp))
                            Column {
                                Text(
                                    group?.name ?: groupId,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (group != null) {
                                    Text(
                                        strings.groupMemberCount(group.memberIds.size),
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
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
        ) {
            Column(modifier = Modifier.fillMaxSize().onSizeChanged { size ->
                if (size.height < prevColumnHeight && messages.isNotEmpty()) {
                    scope.launch { listState.scrollToItem(messages.lastIndex) }
                }
                prevColumnHeight = size.height
            }) {
                if (messages.isEmpty()) {
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
                                    prev.senderId == msg.senderId &&
                                    (msg.timestamp - prev.timestamp) < 60_000L
                            val senderName = remember(msg.senderId) {
                                if (msg.isOwn) owner.nickname
                                else container.contacts.get(msg.senderId)?.nickname ?: msg.senderId.takeLast(6)
                            }
                            val isSelected = selectedMessageIds.contains(msg.id)
                            if (msg.type == MessageType.IMAGE) {
                                GroupImageBubble(
                                    msg = msg,
                                    senderName = senderName,
                                    tightWithPrev = tight,
                                    selected = isSelected,
                                    inSelectionMode = inSelectionMode,
                                    onShortClick = { if (inSelectionMode) toggleSelection(msg.id) },
                                    onLongClick = { toggleSelection(msg.id) },
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
                            } else {
                                GroupTextBubble(
                                    msg = msg,
                                    senderName = senderName,
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

                GroupComposer(
                    draft = draft,
                    pendingImages = pendingImages,
                    onChange = { draft = it },
                    onRemoveImage = { idx ->
                        pendingImages = pendingImages.toMutableList().also { it.removeAt(idx) }
                    },
                    onSend = {
                        val text = draft.text.trim()
                        val images = pendingImages
                        if (text.isEmpty() && images.isEmpty()) return@GroupComposer
                        draft = TextFieldValue("")
                        pendingImages = emptyList()
                        scope.launch {
                            if (text.isNotEmpty()) {
                                runCatching { container.groupChat.sendMessage(owner, groupId, text) }
                                    .onFailure { notify(strings.sendFailed(it.message ?: ""), GroupBannerKind.Error) }
                            }
                            images.forEach { imageBytes ->
                                runCatching { container.groupChat.sendImage(owner, groupId, imageBytes) }
                                    .onFailure { notify(strings.photoSendFailed, GroupBannerKind.Error) }
                            }
                        }
                    },
                    onPickImage = { imagePicker.launch() }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupTextBubble(
    msg: GroupMessage,
    senderName: String,
    tightWithPrev: Boolean,
    selected: Boolean,
    inSelectionMode: Boolean,
    onShortClick: () -> Unit,
    onLongClick: () -> Unit
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
                if (!outgoing && !tightWithPrev) {
                    Text(
                        senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(msg.body, color = fg, style = MaterialTheme.typography.bodyMedium)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupImageBubble(
    msg: GroupMessage,
    senderName: String,
    tightWithPrev: Boolean,
    selected: Boolean,
    inSelectionMode: Boolean,
    onShortClick: () -> Unit,
    onLongClick: () -> Unit,
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
                if (!outgoing && !tightWithPrev) {
                    Text(
                        senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 4.dp)
                    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupComposer(
    draft: TextFieldValue,
    pendingImages: List<ByteArray>,
    onChange: (TextFieldValue) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit
) {
    val strings = LocalStrings.current
    val canSend = draft.text.isNotBlank() || pendingImages.isNotEmpty()

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

