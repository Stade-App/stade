package dev.stade.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import dev.stade.ui.components.Avatar
import dev.stade.ui.components.ChatListFabMenu
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PushPin
import dev.stade.group.GroupInfo
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import dev.stade.ui.theme.StadeColors
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.stade.AppContainer
import dev.stade.contact.Contact
import dev.stade.identity.LocalIdentity
import dev.stade.message.SearchResult
import dev.stade.message.previewBody
import dev.stade.ui.PlatformBackHandler
import dev.stade.ui.components.formatChatTime
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine

private sealed class ChatListItem {
    data class ContactItem(val contact: Contact, val lastMessageTs: Long? = null, val pinnedAtValue: Long? = null) : ChatListItem()
    data class GroupItem(val group: GroupInfo, val lastMessageTs: Long? = null, val pinnedAtValue: Long? = null) : ChatListItem()
    data class StadiumItem(val stadium: dev.stade.stadium.StadiumInfo, val lastMessageTs: Long? = null, val pinnedAtValue: Long? = null) : ChatListItem()
    val displayName: String get() = when (this) {
        is ContactItem -> contact.nickname
        is GroupItem   -> group.name
        is StadiumItem -> stadium.name
    }
    val key: String get() = when (this) {
        is ContactItem -> contact.id
        is GroupItem   -> "grp_${group.id}"
        is StadiumItem -> "std_${stadium.id}"
    }
    val sortKey: Long get() = when (this) {
        is ContactItem -> lastMessageTs ?: 0L
        is GroupItem   -> lastMessageTs ?: 0L
        is StadiumItem -> lastMessageTs ?: stadium.createdAt
    }
    val pinnedAt: Long? get() = when (this) {
        is ContactItem -> pinnedAtValue
        is GroupItem   -> pinnedAtValue
        is StadiumItem -> pinnedAtValue
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    container: AppContainer,
    owner: LocalIdentity,
    onOpenChat: (String) -> Unit,
    onOpenGroupChat: (String) -> Unit,
    onOpenStadium: (String) -> Unit = {},
    onOpenSettings: () -> Unit,
    onAddContact: () -> Unit,
    onCreateGroup: () -> Unit,
    onCreateStadium: () -> Unit = {},
    onJoinStadium: () -> Unit = {},
    onLongPressVerify: (String) -> Unit,
    onOpenChatMessage: (String, String) -> Unit = { _, _ -> },
    onOpenGroupMessage: (String, String) -> Unit = { _, _ -> },
    onOpenStadiumMessage: (String, String) -> Unit = { _, _ -> }
) {
    val allContacts by remember(owner.id) { container.contacts.observeContacts(owner.id) }
        .collectAsState(initial = remember(owner.id) { container.contacts.contacts(owner.id) })
    val contacts by remember(allContacts) { derivedStateOf { allContacts.filter { it.kind == 0 } } }
    val groups by remember(owner.id) { container.groups.observeGroups(owner.id) }
        .collectAsState(initial = remember(owner.id) { container.groups.allGroups(owner.id) })
    val stadiums by remember(owner.id) { container.stadiums.observeStadiums(owner.id) }
        .collectAsState(initial = remember(owner.id) { container.stadiums.allStadiums(owner.id) })
    val connectedSet by container.sync.connectedContacts.collectAsState()
    val pinned by remember(owner.id) { container.pinnedChats.observePinned(owner.id) }
        .collectAsState(initial = remember(owner.id) { container.pinnedChats.pinned(owner.id) })
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current

    val contactLastMessages by remember(contacts) {
        combine(
            contacts.map { c -> container.messages.observeLastMessage(c.id) }
                .ifEmpty { listOf(kotlinx.coroutines.flow.flowOf(null)) }
        ) { it.toList() }
    }.collectAsState(initial = contacts.map { container.messages.lastMessage(it.id) }.ifEmpty { listOf(null) })

    val groupLastMessages by remember(groups) {
        combine(
            groups.map { g -> container.groups.observeLastMessage(g.id) }
                .ifEmpty { listOf(kotlinx.coroutines.flow.flowOf(null)) }
        ) { it.toList() }
    }.collectAsState(initial = groups.map { container.groups.lastMessage(it.id) }.ifEmpty { listOf(null) })

    val stadiumLastMessages by remember(stadiums) {
        combine(
            stadiums.map { s -> container.stadiums.observeLastMessage(s.id) }
                .ifEmpty { listOf(kotlinx.coroutines.flow.flowOf(null)) }
        ) { it.toList() }
    }.collectAsState(initial = stadiums.map { container.stadiums.lastMessage(it.id) }.ifEmpty { listOf(null) })

    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    var actionItem by remember { mutableStateOf<ChatListItem?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    if (actionItem != null && !showDeleteConfirm) {
        val item = actionItem!!
        val itemPinned = item.pinnedAt != null
        AlertDialog(
            onDismissRequest = { actionItem = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,

            icon = {
                when (item) {
                    is ChatListItem.ContactItem -> Avatar(item.contact.nickname, size = 56.dp)
                    is ChatListItem.GroupItem -> Avatar(item.group.name, size = 56.dp, icon = Icons.Default.Group)
                    is ChatListItem.StadiumItem -> Avatar(item.stadium.name, size = 56.dp, icon = Icons.Default.Podcasts)
                }
            },

            title = {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },

            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            container.pinnedChats.setPinned(owner.id, item.key, !itemPinned)
                            actionItem = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (itemPinned) strings.unpinChatAction else strings.pinChatAction)
                    }

                    val itemMuted = when (item) {
                        is ChatListItem.ContactItem -> item.contact.muted
                        is ChatListItem.GroupItem -> item.group.muted
                        is ChatListItem.StadiumItem -> item.stadium.muted
                    }
                    FilledTonalButton(
                        onClick = {
                            when (item) {
                                is ChatListItem.ContactItem -> container.contacts.setMuted(item.contact.id, !itemMuted)
                                is ChatListItem.GroupItem -> container.groups.setMuted(item.group.id, !itemMuted)
                                is ChatListItem.StadiumItem -> container.stadiums.setMuted(item.stadium.id, !itemMuted)
                            }
                            actionItem = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = if (itemMuted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (itemMuted) strings.unmuteChatAction else strings.muteChatAction)
                    }

                    if (item is ChatListItem.ContactItem) {
                        FilledTonalButton(
                            onClick = {
                                actionItem = null
                                onLongPressVerify(item.contact.id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(strings.viewProfileAction)
                        }

                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                            ),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(strings.deleteContact)
                        }
                    }
                }
            },

            confirmButton = {}
        )
    }

    if (showDeleteConfirm && actionItem is ChatListItem.ContactItem) {
        val c = (actionItem as ChatListItem.ContactItem).contact
        AlertDialog(
            onDismissRequest = {
                if (!deleting) {
                    showDeleteConfirm = false
                    actionItem = null
                }
            },
            icon = {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text(strings.deleteContactTitle(c.nickname)) },
            text = {
                Text(
                    strings.deleteContactBody,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        scope.launch {
                            withContext(Dispatchers.Default) {
                                runCatching {
                                    container.sync.forgetContact(c.id)
                                    container.contacts.purge(c.id)
                                }
                            }
                            showDeleteConfirm = false
                            actionItem = null
                            deleting = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(strings.delete) }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        showDeleteConfirm = false
                        actionItem = null
                    }
                ) { Text(strings.cancel) }
            }
        )
    }

    LaunchedEffect(contacts.size) {
        if (contacts.isEmpty() && searchActive) {
            searchActive = false
            query = ""
        }
    }

    LaunchedEffect(searchActive) {
        if (searchActive) focusRequester.requestFocus()
    }

    PlatformBackHandler(enabled = searchActive) {
        searchActive = false
        query = ""
    }

    val filtered by remember {
        derivedStateOf {
            if (!searchActive || query.isBlank()) contacts
            else contacts.filter { it.nickname.contains(query.trim(), ignoreCase = true) }
        }
    }

    var messageResults by remember { mutableStateOf(emptyList<SearchResult>()) }
    LaunchedEffect(query, searchActive) {
        val q = query.trim()
        if (!searchActive || q.isBlank()) {
            messageResults = emptyList()
            return@LaunchedEffect
        }
        delay(200)
        val results = withContext(Dispatchers.Default) {
            (container.messages.searchMessages(owner.id, q) + container.groups.searchMessages(owner.id, q) + container.stadiums.searchMessages(owner.id, q))
                .sortedByDescending { it.timestamp }
                .take(30)
        }
        messageResults = results
    }

    val combinedItems by remember(filtered, groups, stadiums, searchActive, query, contactLastMessages, groupLastMessages, stadiumLastMessages, pinned) {
        derivedStateOf {
            val q = query.trim()
            val result = mutableListOf<ChatListItem>()
            groups
                .filter { !searchActive || q.isBlank() || it.name.contains(q, ignoreCase = true) }
                .forEachIndexed { i, g ->
                    result.add(ChatListItem.GroupItem(g, groupLastMessages.getOrNull(i)?.timestamp, pinned["grp_${g.id}"]))
                }
            stadiums
                .filter { !searchActive || q.isBlank() || it.name.contains(q, ignoreCase = true) }
                .forEachIndexed { i, s ->
                    result.add(ChatListItem.StadiumItem(s, stadiumLastMessages.getOrNull(i)?.timestamp, pinned["std_${s.id}"]))
                }
            filtered.forEachIndexed { i, c ->
                val origIdx = contacts.indexOf(c)
                result.add(ChatListItem.ContactItem(c, contactLastMessages.getOrNull(origIdx)?.timestamp, pinned[c.id]))
            }
            result.sortWith(
                compareByDescending<ChatListItem> { it.pinnedAt != null }
                    .thenByDescending { it.pinnedAt ?: it.sortKey }
            )
            result
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    AnimatedVisibility(
                        visible = searchActive,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(onClick = { searchActive = false; query = "" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.closeSearch)
                        }
                    }
                },
                title = {
                    AnimatedContent(
                        targetState = searchActive,
                        transitionSpec = { fadeIn() togetherWith fadeOut() }
                    ) { active ->
                        if (active) {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                placeholder = {
                                    Text(
                                        strings.searchContactsPlaceholder,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge,
                                trailingIcon = {
                                    if (query.isNotEmpty()) {
                                        IconButton(onClick = { query = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = strings.closeSearch)
                                        }
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                )
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(
                                    name = owner.nickname,
                                    size = 38.dp,
                                    shape = RoundedCornerShape(25)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        strings.appTitle,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        owner.nickname,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    AnimatedVisibility(
                        visible = !searchActive,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Row {
                            if (contacts.isNotEmpty()) {
                                IconButton(onClick = { searchActive = true }) {
                                    Icon(Icons.Default.Search, contentDescription = strings.searchAction)
                                }
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Default.Settings, contentDescription = strings.settingsAction)
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ChatListFabMenu(
                onAddContact = onAddContact,
                onCreateGroup = onCreateGroup,
                onCreateStadium = onCreateStadium,
                onJoinStadium = onJoinStadium
            )
        }
    ) { padding ->
        if (contacts.isEmpty() && groups.isEmpty() && stadiums.isEmpty()) {
            EmptyContacts(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(combinedItems, key = { it.key }) { item ->
                    when (item) {
                        is ChatListItem.ContactItem -> {
                            val contact = item.contact
                            val lastMsg by remember(contact.id) { container.messages.observeLastMessage(contact.id) }
                                .collectAsState(initial = remember(contact.id) { container.messages.lastMessage(contact.id) })
                            val unread by remember(contact.id) { container.messages.observeUnreadCount(contact.id) }
                                .collectAsState(initial = remember(contact.id) { container.messages.unreadCount(contact.id) })
                            val preview by remember(lastMsg?.id) {
                                derivedStateOf { lastMsg?.body?.let { previewBody(it, strings.photoMessage, strings.voiceMessage, strings.videoMessage, strings.stickerMessage) } }
                            }
                            ContactRow(
                                contact = contact,
                                connected = connectedSet.contains(contact.id),
                                lastMessage = preview,
                                unread = unread,
                                pinned = item.pinnedAt != null,
                                onClick = { onOpenChat(contact.id) },
                                onLongPress = { actionItem = item }
                            )
                        }
                        is ChatListItem.GroupItem -> {
                            val group = item.group
                            val lastMsg by remember(group.id) { container.groups.observeLastMessage(group.id) }
                                .collectAsState(initial = remember(group.id) { container.groups.lastMessage(group.id) })
                            val unread by remember(group.id) { container.groups.observeUnreadCount(group.id) }
                                .collectAsState(initial = remember(group.id) { container.groups.unreadCount(group.id) })
                            val preview by remember(lastMsg?.id) {
                                derivedStateOf { lastMsg?.body?.let { previewBody(it, strings.photoMessage, strings.voiceMessage, strings.videoMessage, strings.stickerMessage) } }
                            }
                            GroupRow(
                                group = group,
                                lastMessage = preview,
                                unread = unread,
                                pinned = item.pinnedAt != null,
                                onClick = { onOpenGroupChat(group.id) },
                                onLongPress = { actionItem = item }
                            )
                        }
                        is ChatListItem.StadiumItem -> {
                            val stadium = item.stadium
                            StadiumRow(
                                stadium = stadium,
                                pinned = item.pinnedAt != null,
                                onClick = { onOpenStadium(stadium.id) },
                                onLongPress = { actionItem = item }
                            )
                        }
                    }
                }
                if (searchActive && query.isNotBlank() && messageResults.isNotEmpty()) {
                    item {
                        Text(
                            strings.searchResultsSectionMessages,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(messageResults, key = { "msg_" + it.messageId }) { result ->
                        MessageSearchRow(
                            result = result,
                            onClick = {
                                when {
                                    result.isStadium -> onOpenStadiumMessage(result.chatId, result.messageId)
                                    result.isGroup -> onOpenGroupMessage(result.chatId, result.messageId)
                                    else -> onOpenChatMessage(result.chatId, result.messageId)
                                }
                            }
                        )
                    }
                }
                if (searchActive && query.isNotBlank() && combinedItems.isEmpty() && messageResults.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                strings.noSearchResults,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageSearchRow(result: SearchResult, onClick: () -> Unit) {
    val subtleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(
                name = result.title,
                size = 40.dp,
                icon = when {
                    result.isStadium -> Icons.Default.Podcasts
                    result.isGroup -> Icons.Default.Group
                    else -> null
                }
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    result.title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    result.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleColor,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                formatChatTime(result.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = subtleColor
            )
        }
    }
}

@Composable
private fun EmptyContacts(modifier: Modifier) {
    val strings = LocalStrings.current
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                strings.noContactsTitle,
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.size(8.dp))
            Text(
                strings.noContactsHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    contact: Contact,
    connected: Boolean,
    lastMessage: String?,
    unread: Long,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val strings = LocalStrings.current
    val subtleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onClick() },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Avatar(contact.nickname, size = 52.dp)
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(2.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(
                                if (connected) StadeColors.online else StadeColors.offline
                            )
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        contact.nickname,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (contact.verified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (pinned) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = strings.pinChatAction,
                            modifier = Modifier.size(14.dp).rotate(45f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    lastMessage ?: strings.noMessages,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleColor,
                    maxLines = 1
                )
            }

            if (unread > 0) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        unread.toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupRow(
    group: GroupInfo,
    lastMessage: String?,
    unread: Long,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val strings = LocalStrings.current
    val subtleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onClick() },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(name = group.name, size = 52.dp, icon = Icons.Default.Group)

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (pinned) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = strings.pinChatAction,
                            modifier = Modifier.size(14.dp).rotate(45f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    lastMessage ?: strings.noMessages,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleColor,
                    maxLines = 1
                )
            }

            if (unread > 0) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        unread.toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StadiumRow(
    stadium: dev.stade.stadium.StadiumInfo,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val strings = LocalStrings.current
    val subtleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onClick() },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(name = stadium.name, size = 52.dp, icon = Icons.Default.Podcasts)

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stadium.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (pinned) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = strings.pinChatAction,
                            modifier = Modifier.size(14.dp).rotate(45f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    strings.stadiumSubscriberCount(stadium.memberCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleColor,
                    maxLines = 1
                )
            }
        }
    }
}


