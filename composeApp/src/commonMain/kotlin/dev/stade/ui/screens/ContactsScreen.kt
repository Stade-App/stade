package dev.stade.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import dev.stade.group.GroupInfo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
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
import dev.stade.message.previewBody
import dev.stade.ui.PlatformBackHandler
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine

private sealed class ChatListItem {
    data class ContactItem(val contact: Contact, val lastMessageTs: Long? = null) : ChatListItem()
    data class GroupItem(val group: GroupInfo, val lastMessageTs: Long? = null) : ChatListItem()
    val displayName: String get() = when (this) {
        is ContactItem -> contact.nickname
        is GroupItem   -> group.name
    }
    val key: String get() = when (this) {
        is ContactItem -> contact.id
        is GroupItem   -> "grp_${group.id}"
    }
    val sortKey: Long get() = when (this) {
        is ContactItem -> lastMessageTs ?: 0L
        is GroupItem   -> lastMessageTs ?: 0L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    container: AppContainer,
    owner: LocalIdentity,
    onOpenChat: (String) -> Unit,
    onOpenGroupChat: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onAddContact: () -> Unit,
    onCreateGroup: () -> Unit,
    onLongPressVerify: (String) -> Unit
) {
    val contacts by container.contacts.observeContacts(owner.id).collectAsState(initial = emptyList())
    val groups by container.groups.observeGroups(owner.id).collectAsState(initial = emptyList())
    val connectedSet by container.sync.connectedContacts.collectAsState()
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current

    val contactLastMessages by remember(contacts) {
        combine(
            contacts.map { c -> container.messages.observeLastMessage(c.id) }
                .ifEmpty { listOf(kotlinx.coroutines.flow.flowOf(null)) }
        ) { it.toList() }
    }.collectAsState(initial = emptyList())

    val groupLastMessages by remember(groups) {
        combine(
            groups.map { g -> container.groups.observeLastMessage(g.id) }
                .ifEmpty { listOf(kotlinx.coroutines.flow.flowOf(null)) }
        ) { it.toList() }
    }.collectAsState(initial = emptyList())

    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var isFabExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    var actionContact by remember { mutableStateOf<Contact?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    if (actionContact != null && !showDeleteConfirm) {
        val c = actionContact!!
        AlertDialog(
            onDismissRequest = { actionContact = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,

            icon = {
                Avatar(
                    c.nickname,
                    size = 56.dp
                )
            },

            title = {
                Text(
                    text = c.nickname,
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
                            actionContact = null
                            onLongPressVerify(c.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(strings.showVerificationCode)
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
            },

            confirmButton = {}
        )
    }

    if (showDeleteConfirm && actionContact != null) {
        val c = actionContact!!
        AlertDialog(
            onDismissRequest = {
                if (!deleting) {
                    showDeleteConfirm = false
                    actionContact = null
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
                            actionContact = null
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
                        actionContact = null
                    }
                ) { Text(strings.cancel) }
            }
        )
    }

    LaunchedEffect(contacts.size) {
        if (contacts.size <= 1 && searchActive) {
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

    val combinedItems by remember(filtered, groups, searchActive, query, contactLastMessages, groupLastMessages) {
        derivedStateOf {
            val q = query.trim()
            val result = mutableListOf<ChatListItem>()
            groups
                .filter { !searchActive || q.isBlank() || it.name.contains(q, ignoreCase = true) }
                .forEachIndexed { i, g ->
                    result.add(ChatListItem.GroupItem(g, groupLastMessages.getOrNull(i)?.timestamp))
                }
            filtered.forEachIndexed { i, c ->
                val origIdx = contacts.indexOf(c)
                result.add(ChatListItem.ContactItem(c, contactLastMessages.getOrNull(origIdx)?.timestamp))
            }
            result.sortByDescending { it.sortKey }
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
                            if (contacts.size > 1) {
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
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = strings.createGroupAction,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
                                    onCreateGroup()
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Icon(Icons.Default.GroupAdd, contentDescription = strings.createGroupAction)
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = strings.addContactAction,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
                                    onAddContact()
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = strings.addContactAction)
                            }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { isFabExpanded = !isFabExpanded },
                    containerColor = if (isFabExpanded) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primary,
                    contentColor = if (isFabExpanded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = if (isFabExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = null
                    )
                }
            }
        }
    ) { padding ->
        if (contacts.isEmpty() && groups.isEmpty()) {
            EmptyContacts(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(combinedItems, key = { it.key }) { item ->
                    when (item) {
                        is ChatListItem.ContactItem -> {
                            val contact = item.contact
                            val lastMsg by container.messages.observeLastMessage(contact.id).collectAsState(initial = null)
                            val unread by container.messages.observeUnreadCount(contact.id).collectAsState(initial = 0L)
                            val preview by remember(lastMsg?.id) {
                                derivedStateOf { lastMsg?.body?.let { previewBody(it, strings.photoMessage) } }
                            }
                            ContactRow(
                                contact = contact,
                                connected = connectedSet.contains(contact.id),
                                lastMessage = preview,
                                unread = unread,
                                onClick = { onOpenChat(contact.id) },
                                onLongPress = { actionContact = contact }
                            )
                        }
                        is ChatListItem.GroupItem -> {
                            val group = item.group
                            val lastMsg by container.groups.observeLastMessage(group.id).collectAsState(initial = null)
                            val unread by container.groups.observeUnreadCount(group.id).collectAsState(initial = 0L)
                            val preview by remember(lastMsg?.id) {
                                derivedStateOf { lastMsg?.body?.let { previewBody(it, strings.photoMessage) } }
                            }
                            GroupRow(
                                group = group,
                                lastMessage = preview,
                                unread = unread,
                                onClick = { onOpenGroupChat(group.id) }
                            )
                        }
                    }
                }
                if (searchActive && combinedItems.isEmpty()) {
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

@Composable
private fun GroupRow(
    group: GroupInfo,
    lastMessage: String?,
    unread: Long,
    onClick: () -> Unit
) {
    val strings = LocalStrings.current
    val subtleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    group.name,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
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


