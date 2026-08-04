package dev.stade.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.stade.AppContainer
import dev.stade.contact.Contact
import dev.stade.group.GroupInfo
import dev.stade.identity.LocalIdentity
import dev.stade.message.SearchResult
import dev.stade.message.previewBody
import dev.stade.ui.components.Avatar
import dev.stade.ui.components.BrandMark
import dev.stade.ui.components.ChatListFabMenu
import dev.stade.ui.components.formatChatTime
import dev.stade.ui.screens.AboutScreen
import dev.stade.ui.screens.AddContactScreen
import dev.stade.ui.screens.ChatScreen
import dev.stade.ui.screens.CreateGroupScreen
import dev.stade.ui.screens.CreateStadiumScreen
import dev.stade.ui.screens.GroupChatScreen
import dev.stade.ui.screens.GroupMembersScreen
import dev.stade.ui.screens.JoinStadiumScreen
import dev.stade.ui.screens.ManageStadiumScreen
import dev.stade.ui.screens.PinSetupScreen
import dev.stade.ui.screens.StadiumScreen
import dev.stade.ui.screens.SettingsScreen
import dev.stade.ui.screens.TransportsScreen
import dev.stade.ui.screens.VerifyContactScreen
import dev.stade.ui.screens.SecuritySettingsScreen
import dev.stade.ui.i18n.LocalStrings
import dev.stade.ui.theme.StadeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine

private sealed class PanelRight {
    data object Empty : PanelRight()
    data class Chat(val contactId: String, val highlightMessageId: String? = null) : PanelRight()
    data class GroupChat(val groupId: String, val highlightMessageId: String? = null) : PanelRight()
    data class GroupMembers(val groupId: String) : PanelRight()
    data object CreateGroup : PanelRight()
    data class Stadium(val stadiumId: String, val highlightMessageId: String? = null) : PanelRight()
    data object CreateStadium : PanelRight()
    data class ManageStadium(val stadiumId: String) : PanelRight()
    data object JoinStadium : PanelRight()
    data object Settings : PanelRight()
    data object Security : PanelRight()
    data object Transports : PanelRight()
    data object About : PanelRight()
    data object AddContact : PanelRight()
    data class Verify(val contactId: String, val from: PanelRight = Chat(contactId)) : PanelRight()
    data class PinSetup(val requireCurrent: Boolean, val ret: PanelRight, val mode: dev.stade.ui.screens.PinSetupMode = dev.stade.ui.screens.PinSetupMode.Primary) : PanelRight()
}

private sealed class PanelChatItem {
    data class ContactItem(val contact: Contact, val lastMessageTs: Long? = null, val pinnedAtValue: Long? = null) : PanelChatItem()
    data class GroupItem(val group: GroupInfo, val lastMessageTs: Long? = null, val pinnedAtValue: Long? = null) : PanelChatItem()
    data class StadiumItem(val stadium: dev.stade.stadium.StadiumInfo, val lastMessageTs: Long? = null, val pinnedAtValue: Long? = null) : PanelChatItem()
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
fun TwoPanelLayout(
    container: AppContainer,
    owner: LocalIdentity,
    onLogout: () -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
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
    var right by remember { mutableStateOf<PanelRight>(PanelRight.Empty) }
    var query by remember { mutableStateOf("") }
    val settingsListState = rememberLazyListState()

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

    val pendingInvite by container.pendingInvite.collectAsState()
    LaunchedEffect(pendingInvite) {
        if (pendingInvite != null && right !is PanelRight.AddContact && right !is PanelRight.PinSetup) {
            right = PanelRight.AddContact
        }
    }

    val pendingOpenChatId by container.pendingOpenChat.collectAsState()
    LaunchedEffect(pendingOpenChatId) {
        val id = pendingOpenChatId
        if (id != null) {
            right = PanelRight.Chat(id)
            container.pendingOpenChat.value = null
        }
    }

    val pendingOpenStadiumId by container.pendingOpenStadium.collectAsState()
    LaunchedEffect(pendingOpenStadiumId) {
        val id = pendingOpenStadiumId
        if (id != null) {
            right = PanelRight.Stadium(id)
            container.pendingOpenStadium.value = null
        }
    }

    val pendingGoHome by container.pendingGoHome.collectAsState()
    LaunchedEffect(pendingGoHome) {
        if (pendingGoHome) {
            right = PanelRight.Empty
            container.pendingGoHome.value = false
        }
    }

    var deleteTargetContact by remember { mutableStateOf<Contact?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val filtered = remember(contacts, query) {
        if (query.isBlank()) contacts
        else contacts.filter { it.nickname.contains(query.trim(), ignoreCase = true) }
    }

    val combinedPanelItems = remember(filtered, groups, stadiums, query, contactLastMessages, groupLastMessages, stadiumLastMessages, pinned) {
        val q = query.trim()
        val result = mutableListOf<PanelChatItem>()
        groups
            .filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }
            .forEachIndexed { i, g ->
                result.add(PanelChatItem.GroupItem(g, groupLastMessages.getOrNull(i)?.timestamp, pinned["grp_${g.id}"]))
            }
        stadiums
            .filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }
            .forEachIndexed { i, s ->
                result.add(PanelChatItem.StadiumItem(s, stadiumLastMessages.getOrNull(i)?.timestamp, pinned["std_${s.id}"]))
            }
        filtered.forEachIndexed { i, c ->
            val origIdx = contacts.indexOf(c)
            result.add(PanelChatItem.ContactItem(c, contactLastMessages.getOrNull(origIdx)?.timestamp, pinned[c.id]))
        }
        result.sortWith(
            compareByDescending<PanelChatItem> { it.pinnedAt != null }
                .thenByDescending { it.pinnedAt ?: it.sortKey }
        )
        result
    }

    var panelMessageResults by remember { mutableStateOf(emptyList<SearchResult>()) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isBlank()) {
            panelMessageResults = emptyList()
            return@LaunchedEffect
        }
        delay(200)
        val results = withContext(Dispatchers.Default) {
            (container.messages.searchMessages(owner.id, q) + container.groups.searchMessages(owner.id, q) + container.stadiums.searchMessages(owner.id, q))
                .sortedByDescending { it.timestamp }
                .take(30)
        }
        panelMessageResults = results
    }

    if (showDeleteConfirm && deleteTargetContact != null) {
        val c = deleteTargetContact!!
        AlertDialog(
            onDismissRequest = {
                if (!deleting) {
                    showDeleteConfirm = false
                    deleteTargetContact = null
                }
            },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
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
                            val currentRight = right
                            if (currentRight is PanelRight.Chat && currentRight.contactId == c.id) {
                                right = PanelRight.Empty
                            }
                            if (right is PanelRight.Verify &&
                                (right as PanelRight.Verify).contactId == c.id) {
                                right = PanelRight.Empty
                            }
                            showDeleteConfirm = false
                            deleteTargetContact = null
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
                        deleteTargetContact = null
                    }
                ) { Text(strings.cancel) }
            }
        )
    }

    Row(modifier = Modifier.fillMaxSize()) {

        Surface(
            modifier = Modifier.width(320.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        ),
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(name = owner.nickname, size = 32.dp,shape = RoundedCornerShape(25))
                                Spacer(Modifier.size(10.dp))
                                Column {
                                    Text(strings.appTitle, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        owner.nickname,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { right = PanelRight.Settings }) {
                                Icon(Icons.Default.Settings, contentDescription = strings.settingsAction)
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    if (contacts.isNotEmpty()) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            placeholder = { Text(strings.searchContactsPlaceholder) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            )
                        )
                    }

                    if (contacts.isEmpty() && groups.isEmpty() && stadiums.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.PersonAdd, null,
                                    modifier = Modifier.size(52.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(strings.noContactsTitle, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    strings.noContactsHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(4.dp))
                                FilledTonalButton(onClick = { right = PanelRight.AddContact }) {
                                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(strings.addContactAction)
                                }
                                FilledTonalButton(onClick = { right = PanelRight.CreateStadium }) {
                                    Icon(Icons.Default.Podcasts, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(strings.createStadiumAction)
                                }
                                FilledTonalButton(onClick = { right = PanelRight.JoinStadium }) {
                                    Icon(Icons.Default.Podcasts, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(strings.joinStadiumAction)
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(combinedPanelItems, key = { it.key }) { item ->
                                    when (item) {
                                        is PanelChatItem.ContactItem -> {
                                            val contact = item.contact
                                            val lastMsg by remember(contact.id) { container.messages.observeLastMessage(contact.id) }
                                                .collectAsState(initial = remember(contact.id) { container.messages.lastMessage(contact.id) })
                                            val unread by remember(contact.id) { container.messages.observeUnreadCount(contact.id) }
                                                .collectAsState(initial = remember(contact.id) { container.messages.unreadCount(contact.id) })
                                            val preview by remember(lastMsg?.id) {
                                                derivedStateOf { lastMsg?.body?.let { previewBody(it, strings.photoMessage, strings.voiceMessage, strings.videoMessage, strings.stickerMessage) } }
                                            }
                                            val isSelected by remember(contact.id) {
                                                derivedStateOf {
                                                    when (val r = right) {
                                                        is PanelRight.Chat   -> r.contactId == contact.id
                                                        is PanelRight.Verify -> r.contactId == contact.id
                                                        else -> false
                                                    }
                                                }
                                            }
                                            PanelContactRow(
                                                contact = contact,
                                                selected = isSelected,
                                                connected = connectedSet.contains(contact.id),
                                                pinned = item.pinnedAt != null,
                                                lastMessage = preview,
                                                lastMessageTs = lastMsg?.timestamp,
                                                unread = unread,
                                                onClick = { right = PanelRight.Chat(contact.id) },
                                                onVerifyRequest = { right = PanelRight.Verify(contact.id) },
                                                onDeleteRequest = {
                                                    deleteTargetContact = contact
                                                    showDeleteConfirm = true
                                                },
                                                onTogglePin = {
                                                    container.pinnedChats.setPinned(owner.id, item.key, item.pinnedAt == null)
                                                },
                                                onToggleMute = {
                                                    container.contacts.setMuted(contact.id, !contact.muted)
                                                }
                                            )
                                        }
                                        is PanelChatItem.GroupItem -> {
                                            val group = item.group
                                            val lastGroupMsg by remember(group.id) { container.groups.observeLastMessage(group.id) }
                                                .collectAsState(initial = remember(group.id) { container.groups.lastMessage(group.id) })
                                            val groupUnread by remember(group.id) { container.groups.observeUnreadCount(group.id) }
                                                .collectAsState(initial = remember(group.id) { container.groups.unreadCount(group.id) })
                                            val groupPreview by remember(lastGroupMsg?.id) {
                                                derivedStateOf { lastGroupMsg?.body?.let { previewBody(it, strings.photoMessage, strings.voiceMessage, strings.videoMessage, strings.stickerMessage) } }
                                            }
                                            val isGroupSelected by remember(group.id) {
                                                derivedStateOf {
                                                    when (val r = right) {
                                                        is PanelRight.GroupChat -> r.groupId == group.id
                                                        is PanelRight.GroupMembers -> r.groupId == group.id
                                                        else -> false
                                                    }
                                                }
                                            }
                                            PanelGroupRow(
                                                group = group,
                                                selected = isGroupSelected,
                                                pinned = item.pinnedAt != null,
                                                lastMessage = groupPreview,
                                                lastMessageTs = lastGroupMsg?.timestamp,
                                                unread = groupUnread,
                                                onClick = { right = PanelRight.GroupChat(group.id) },
                                                onTogglePin = {
                                                    container.pinnedChats.setPinned(owner.id, item.key, item.pinnedAt == null)
                                                },
                                                onToggleMute = {
                                                    container.groups.setMuted(group.id, !group.muted)
                                                }
                                            )
                                        }
                                        is PanelChatItem.StadiumItem -> {
                                            val stadium = item.stadium
                                            val isStadiumSelected by remember(stadium.id) {
                                                derivedStateOf {
                                                    when (val r = right) {
                                                        is PanelRight.Stadium -> r.stadiumId == stadium.id
                                                        is PanelRight.ManageStadium -> r.stadiumId == stadium.id
                                                        else -> false
                                                    }
                                                }
                                            }
                                            PanelStadiumRow(
                                                stadium = stadium,
                                                selected = isStadiumSelected,
                                                pinned = item.pinnedAt != null,
                                                onClick = { right = PanelRight.Stadium(stadium.id) },
                                                onTogglePin = {
                                                    container.pinnedChats.setPinned(owner.id, item.key, item.pinnedAt == null)
                                                },
                                                onToggleMute = {
                                                    container.stadiums.setMuted(stadium.id, !stadium.muted)
                                                }
                                            )
                                        }
                                    }
                                }
                                if (query.isNotBlank() && panelMessageResults.isNotEmpty()) {
                                    item {
                                        Text(
                                            strings.searchResultsSectionMessages,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                    items(panelMessageResults, key = { "msg_" + it.messageId }) { result ->
                                        PanelMessageSearchRow(
                                            result = result,
                                            onClick = {
                                                right = when {
                                                    result.isStadium -> PanelRight.Stadium(result.chatId, highlightMessageId = result.messageId)
                                                    result.isGroup -> PanelRight.GroupChat(result.chatId, highlightMessageId = result.messageId)
                                                    else -> PanelRight.Chat(result.chatId, highlightMessageId = result.messageId)
                                                }
                                            }
                                        )
                                    }
                                }
                                if (query.isNotBlank() && combinedPanelItems.isEmpty() && panelMessageResults.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                strings.noSearchResults,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                item { Spacer(Modifier.height(80.dp)) }
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                            ) {
                                ChatListFabMenu(
                                    onAddContact = { right = PanelRight.AddContact },
                                    onCreateGroup = { right = PanelRight.CreateGroup },
                                    onCreateStadium = { right = PanelRight.CreateStadium },
                                    onJoinStadium = { right = PanelRight.JoinStadium }
                                )
                            }
                        }
                    }
                }
            }
        }

        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (val rp = right) {
                is PanelRight.Empty -> Box(
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            BrandMark(size = 125.dp)

                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Stade",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    strings.selectContactHint,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                is PanelRight.Chat -> ChatScreen(
                    container = container,
                    owner = owner,
                    contactId = rp.contactId,
                    highlightMessageId = rp.highlightMessageId,
                    onBack = null,
                    onOpenProfile = { right = PanelRight.Verify(rp.contactId, from = PanelRight.Chat(rp.contactId)) },
                    onContactDeleted = { right = PanelRight.Empty }
                )

                is PanelRight.Settings -> SettingsScreen(
                    container = container,
                    owner = owner,
                    onBack = { right = PanelRight.Empty },
                    onOpenTransports = { right = PanelRight.Transports },
                    onOpenSecurity = { right = PanelRight.Security },
                    onOpenAbout = { right = PanelRight.About },
                    onLogout = onLogout,
                    listState = settingsListState
                )

                is PanelRight.Security -> SecuritySettingsScreen(
                    container = container,
                    onBack = { right = PanelRight.Settings },
                    onOpenPinSetup = { requireCurrent ->
                        right = PanelRight.PinSetup(requireCurrent, PanelRight.Security)
                    },
                    onOpenDuressPinSetup = {
                        right = PanelRight.PinSetup(true, PanelRight.Security, dev.stade.ui.screens.PinSetupMode.Duress)
                    }
                )

                is PanelRight.PinSetup -> PinSetupScreen(
                    vault = container.vault,
                    requireCurrent = rp.requireCurrent,
                    mode = rp.mode,
                    onDone = { right = rp.ret },
                    onCancel = { right = rp.ret }
                )

                is PanelRight.Transports -> TransportsScreen(
                    container = container,
                    onBack = { right = PanelRight.Settings }
                )

                is PanelRight.About -> AboutScreen(
                    onBack = { right = PanelRight.Settings }
                )

                is PanelRight.AddContact -> AddContactScreen(
                    container = container,
                    owner = owner,
                    onBack = {
                        container.pendingInvite.value = null
                        right = PanelRight.Empty
                    }
                )

                is PanelRight.Verify -> VerifyContactScreen(
                    container = container,
                    owner = owner,
                    contactId = rp.contactId,
                    onBack = { right = rp.from }
                )

                is PanelRight.CreateGroup -> CreateGroupScreen(
                    container = container,
                    owner = owner,
                    onBack = { right = PanelRight.Empty },
                    onGroupCreated = { groupId -> right = PanelRight.GroupChat(groupId) }
                )

                is PanelRight.GroupChat -> GroupChatScreen(
                    container = container,
                    owner = owner,
                    groupId = rp.groupId,
                    highlightMessageId = rp.highlightMessageId,
                    onBack = { right = PanelRight.Empty },
                    onOpenMembers = { right = PanelRight.GroupMembers(rp.groupId) }
                )

                is PanelRight.GroupMembers -> GroupMembersScreen(
                    container = container,
                    owner = owner,
                    groupId = rp.groupId,
                    onBack = { right = PanelRight.GroupChat(rp.groupId) },
                    onOpenProfile = { memberId ->
                        right = PanelRight.Verify(memberId, from = PanelRight.GroupMembers(rp.groupId))
                    }
                )

                is PanelRight.Stadium -> StadiumScreen(
                    container = container,
                    owner = owner,
                    stadiumId = rp.stadiumId,
                    onBack = { right = PanelRight.Empty },
                    onManage = { right = PanelRight.ManageStadium(rp.stadiumId) }
                )

                is PanelRight.ManageStadium -> ManageStadiumScreen(
                    container = container,
                    owner = owner,
                    stadiumId = rp.stadiumId,
                    onBack = { right = PanelRight.Stadium(rp.stadiumId) },
                    onDeleted = { right = PanelRight.Empty }
                )

                is PanelRight.CreateStadium -> CreateStadiumScreen(
                    container = container,
                    owner = owner,
                    onBack = { right = PanelRight.Empty },
                    onStadiumCreated = { stadiumId -> right = PanelRight.Stadium(stadiumId) }
                )

                is PanelRight.JoinStadium -> JoinStadiumScreen(
                    container = container,
                    owner = owner,
                    onBack = { right = PanelRight.Empty },
                    onJoined = { stadiumId -> right = PanelRight.Stadium(stadiumId) }
                )
            }
        }
    }
}


@Composable
private fun PanelMessageSearchRow(result: SearchResult, onClick: () -> Unit) {
    val subtleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            name = result.title,
            size = 36.dp,
            icon = when {
                result.isStadium -> Icons.Default.Podcasts
                result.isGroup -> Icons.Default.Group
                else -> null
            }
        )
        Spacer(Modifier.width(12.dp))
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PanelContactRow(
    contact: Contact,
    selected: Boolean,
    connected: Boolean,
    pinned: Boolean,
    lastMessage: String?,
    lastMessageTs: Long?,
    unread: Long,
    onClick: () -> Unit,
    onVerifyRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
    val strings = LocalStrings.current

    var showContextMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var rowHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Box(modifier = Modifier.onSizeChanged { rowHeightPx = it.height }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .clickable(onClick = onClick)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                val pos = event.changes.firstOrNull()?.position
                                if (pos != null) {
                                    menuOffset = with(density) {
                                        DpOffset(
                                            x = pos.x.toDp(),
                                            y = pos.y.toDp() - rowHeightPx.toDp()
                                        )
                                    }
                                }
                                event.changes.forEach { it.consume() }
                                showContextMenu = true
                            }
                        }
                    }
                }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
            )
            Spacer(Modifier.width(8.dp))

            Box {
                Avatar(name = contact.nickname, size = 42.dp)
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(2.dp)
                ) {
                    Box(
                        Modifier.fillMaxSize().clip(CircleShape).background(
                            if (connected) StadeColors.online else StadeColors.offline
                        )
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        contact.nickname,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        lastMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (lastMessageTs != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            formatChatTime(lastMessageTs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (unread > 0) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (unread > 99) "99+" else unread.toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = menuOffset
        ) {
            DropdownMenuItem(
                text = { Text(if (pinned) strings.unpinChatAction else strings.pinChatAction) },
                leadingIcon = {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showContextMenu = false
                    onTogglePin()
                }
            )
            DropdownMenuItem(
                text = { Text(if (contact.muted) strings.unmuteChatAction else strings.muteChatAction) },
                leadingIcon = {
                    Icon(
                        if (contact.muted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showContextMenu = false
                    onToggleMute()
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(strings.viewProfileAction) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showContextMenu = false
                    onVerifyRequest()
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(strings.deleteContact, color = MaterialTheme.colorScheme.error)
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    showContextMenu = false
                    onDeleteRequest()
                }
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PanelGroupRow(
    group: GroupInfo,
    selected: Boolean,
    pinned: Boolean,
    lastMessage: String?,
    lastMessageTs: Long?,
    unread: Long,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
    val strings = LocalStrings.current

    var showContextMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var rowHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Box(modifier = Modifier.onSizeChanged { rowHeightPx = it.height }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .clickable(onClick = onClick)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                val pos = event.changes.firstOrNull()?.position
                                if (pos != null) {
                                    menuOffset = with(density) {
                                        DpOffset(
                                            x = pos.x.toDp(),
                                            y = pos.y.toDp() - rowHeightPx.toDp()
                                        )
                                    }
                                }
                                event.changes.forEach { it.consume() }
                                showContextMenu = true
                            }
                        }
                    }
                }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
            )
            Spacer(Modifier.width(8.dp))

            Avatar(name = group.name, size = 44.dp, icon = Icons.Default.Group)

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        lastMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (lastMessageTs != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            formatChatTime(lastMessageTs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (unread > 0) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (unread > 99) "99+" else unread.toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = menuOffset
        ) {
            DropdownMenuItem(
                text = { Text(if (pinned) strings.unpinChatAction else strings.pinChatAction) },
                leadingIcon = {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showContextMenu = false
                    onTogglePin()
                }
            )
            DropdownMenuItem(
                text = { Text(if (group.muted) strings.unmuteChatAction else strings.muteChatAction) },
                leadingIcon = {
                    Icon(
                        if (group.muted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showContextMenu = false
                    onToggleMute()
                }
            )
        }
    }
}
@Composable
private fun PanelStadiumRow(
    stadium: dev.stade.stadium.StadiumInfo,
    selected: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
    val strings = LocalStrings.current

    var showContextMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var rowHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Box(modifier = Modifier.onSizeChanged { rowHeightPx = it.height }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .clickable(onClick = onClick)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                val pos = event.changes.firstOrNull()?.position
                                if (pos != null) {
                                    menuOffset = with(density) {
                                        DpOffset(
                                            x = pos.x.toDp(),
                                            y = pos.y.toDp() - rowHeightPx.toDp()
                                        )
                                    }
                                }
                                event.changes.forEach { it.consume() }
                                showContextMenu = true
                            }
                        }
                    }
                }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
            )
            Spacer(Modifier.width(8.dp))

            Avatar(name = stadium.name, size = 44.dp, icon = Icons.Default.Podcasts)

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stadium.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = menuOffset
        ) {
            DropdownMenuItem(
                text = { Text(if (pinned) strings.unpinChatAction else strings.pinChatAction) },
                leadingIcon = {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showContextMenu = false
                    onTogglePin()
                }
            )
            DropdownMenuItem(
                text = { Text(if (stadium.muted) strings.unmuteStadiumAction else strings.muteStadiumAction) },
                leadingIcon = {
                    Icon(
                        if (stadium.muted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showContextMenu = false
                    onToggleMute()
                }
            )
        }
    }
}
