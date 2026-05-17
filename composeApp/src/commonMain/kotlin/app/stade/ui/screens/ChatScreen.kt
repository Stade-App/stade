package app.stade.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.stade.AppContainer
import app.stade.identity.LocalIdentity
import app.stade.message.Message
import app.stade.message.MessageDirection
import app.stade.message.MessageType
import app.stade.notification.cancelMessagesNotification
import app.stade.notification.clearAllMessageNotifications
import app.stade.sync.SyncEngine
import app.stade.transport.DialAttempt
import app.stade.ui.ImagePickerLauncher
import app.stade.ui.components.Avatar
import app.stade.ui.components.formatChatTime
import app.stade.ui.components.maskAddress
import app.stade.ui.decodeToImageBitmap
import app.stade.ui.i18n.LocalStrings
import app.stade.ui.rememberImagePickerLauncher
import app.stade.ui.theme.StadeColors
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
    val contact = remember(contactId) { container.contacts.get(contactId) }
    val messages by container.messages.observeMessages(contactId).collectAsState(initial = emptyList())
    val connected by container.sync.connectedContacts.collectAsState()
    val isOnline = connected.contains(contactId)
    val diagnostics by container.connections.diagnostics.collectAsState()
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var diagnosticsExpanded by remember(contactId) { mutableStateOf(false) }

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
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    val MAX_IMAGE_BYTES = 3 * 1024 * 1024 // 3 MB

    val imagePicker = rememberImagePickerLauncher { bytes ->
        val c = contact ?: return@rememberImagePickerLauncher
        if (bytes.size > MAX_IMAGE_BYTES) {
            showNotification(strings.photoTooBig, NotificationKind.Error)
            return@rememberImagePickerLauncher
        }
        scope.launch {
            runCatching { container.chat.sendImage(owner, c, bytes) }
                .onFailure { showNotification(strings.photoSendFailed, NotificationKind.Error) }
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

    Scaffold(
        topBar = {
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(padding)
                .imePadding()
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
                        items(messages, key = { it.id }) { msg ->
                            val idx = messages.indexOf(msg)
                            val prev = messages.getOrNull(idx - 1)
                            val tight = prev != null &&
                                    prev.direction == msg.direction &&
                                    (msg.timestamp - prev.timestamp) < 60_000L
                            if (msg.type == MessageType.IMAGE) {
                                ImageBubble(msg, tightWithPrev = tight)
                            } else {
                                Bubble(msg, tightWithPrev = tight)
                            }
                        }
                    }
                }

                Composer(
                    draft = draft,
                    onChange = { draft = it },
                    onSend = {
                        val c = contact ?: return@Composer
                        val text = draft.text.trim()
                        if (text.isEmpty()) return@Composer
                        draft = TextFieldValue("")
                        scope.launch { container.chat.send(owner, c, text) }
                    },
                    onPickImage = { imagePicker.launch() }
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


@Composable
private fun Composer(
    draft: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = onPickImage,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                Icons.Default.AddPhotoAlternate,
                contentDescription = strings.attachPhoto,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        TextField(
            value = draft,
            onValueChange = onChange,
            modifier = Modifier
                .weight(1f)
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
            placeholder = { Text(strings.typeMessagePlaceholder) },
            maxLines = 5,
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
        FilledIconButton(
            enabled = draft.text.isNotBlank(),
            onClick = onSend,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.sendButton)
        }
    }
}


@Composable
private fun Bubble(msg: Message, tightWithPrev: Boolean) {
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

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = if (tightWithPrev) 1.dp else 6.dp),
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

@Composable
private fun ImageBubble(msg: Message, tightWithPrev: Boolean) {
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

    val bitmap: ImageBitmap? = remember(msg.id) {
        msg.imageBytes()?.decodeToImageBitmap()
    }
    var showFullscreen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = if (tightWithPrev) 1.dp else 6.dp),
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
                .clickable(enabled = bitmap != null) { showFullscreen = true }
                .padding(4.dp)
        ) {
            Column {
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap,
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

    if (showFullscreen && bitmap != null) {
        Dialog(onDismissRequest = { showFullscreen = false }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { showFullscreen = false },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = strings.photoMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { showFullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
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

