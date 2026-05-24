package app.stade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.group.GroupMessage
import app.stade.identity.LocalIdentity
import app.stade.ui.components.formatChatTime
import app.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var menuOpen by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showDeleteGroupDialog by remember { mutableStateOf(false) }
    var inviteLink by remember { mutableStateOf("") }

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

    if (showInviteDialog && group != null) {
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            icon = { Icon(Icons.Default.Link, contentDescription = null) },
            title = { Text(strings.groupInviteTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.groupInviteBody, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        inviteLink,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(inviteLink))
                    showInviteDialog = false
                }) { Text(strings.copyInviteLink) }
            },
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }) { Text(strings.cancel) }
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

    Scaffold(
        topBar = {
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
                                text = { Text(strings.groupGenerateInvite) },
                                leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                                onClick = {
                                    menuOpen = false
                                    if (group != null) {
                                        inviteLink = container.groups.generateInviteLink(
                                            group.id, group.name, group.inviteToken, owner.stadeId
                                        )
                                        showInviteDialog = true
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.deleteGroupTitle, color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuOpen = false
                                    showDeleteGroupDialog = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            if (messages.isEmpty()) {
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            strings.noMessagesYet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
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
                        GroupBubble(msg = msg, senderName = senderName, tightWithPrev = tight)
                    }
                }
            }

            GroupComposer(
                draft = draft,
                onChange = { draft = it },
                onSend = {
                    val body = draft.text.trim()
                    if (body.isEmpty()) return@GroupComposer
                    draft = TextFieldValue("")
                    scope.launch {
                        withContext(Dispatchers.Default) {
                            runCatching { container.groupChat.sendMessage(owner, groupId, body) }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun GroupBubble(msg: GroupMessage, senderName: String, tightWithPrev: Boolean) {
    val outgoing = msg.isOwn
    val bg = if (outgoing) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (outgoing) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    val sub = fg.copy(alpha = if (outgoing) 0.75f else 0.55f)
    val shape = RoundedCornerShape(
        topStart = if (outgoing) 18.dp else if (tightWithPrev) 6.dp else 18.dp,
        topEnd = if (outgoing) (if (tightWithPrev) 6.dp else 18.dp) else 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 18.dp
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (tightWithPrev) 1.dp else 4.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = shape,
            color = bg,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!outgoing && !tightWithPrev) {
                    Text(
                        senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(msg.body, style = MaterialTheme.typography.bodyMedium, color = fg)
                Spacer(Modifier.height(2.dp))
                Text(
                    formatChatTime(msg.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = sub,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun GroupComposer(
    draft: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
                    } else false
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

