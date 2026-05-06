package app.stade.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.contact.Contact
import app.stade.identity.LocalIdentity
import app.stade.ui.components.Avatar
import app.stade.ui.components.BrandMark
import app.stade.ui.components.formatChatTime
import app.stade.ui.screens.AddContactScreen
import app.stade.ui.screens.ChatScreen
import app.stade.ui.screens.SettingsScreen
import app.stade.ui.screens.TransportsScreen
import app.stade.ui.screens.VerifyContactScreen
import app.stade.ui.theme.StadeColors

private sealed class PanelRight {
    data object Empty : PanelRight()
    data class Chat(val contactId: String) : PanelRight()
    data object Settings : PanelRight()
    data object Transports : PanelRight()
    data object AddContact : PanelRight()
    data class Verify(val contactId: String) : PanelRight()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoPanelLayout(
    container: AppContainer,
    owner: LocalIdentity,
    onLogout: () -> Unit
) {
    val contacts by container.contacts.observeContacts(owner.id).collectAsState(initial = emptyList())
    val connectedSet by container.sync.connectedContacts.collectAsState()
    var right by remember { mutableStateOf<PanelRight>(PanelRight.Empty) }
    var query by remember { mutableStateOf("") }

    val filtered = remember(contacts, query) {
        if (query.isBlank()) contacts
        else contacts.filter { it.nickname.contains(query.trim(), ignoreCase = true) }
    }

    Row(modifier = Modifier.fillMaxSize()) {

        // ── Sol panel ─────────────────────────────────────────
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
                                BrandMark(size = 28.dp)
                                Spacer(Modifier.size(10.dp))
                                Column {
                                    Text("Stade", style = MaterialTheme.typography.titleMedium)
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
                                Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
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
                            placeholder = { Text("Ara…") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

                    if (contacts.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, null,
                                    modifier = Modifier.size(52.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Henüz kişin yok", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Mesajlaşmaya başlamak için\nbir kişi ekle",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(4.dp))
                                FilledTonalButton(onClick = { right = PanelRight.AddContact }) {
                                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Kişi ekle")
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filtered, key = { it.id }) { contact ->
                                    val lastMsg by container.messages.observeLastMessage(contact.id)
                                        .collectAsState(initial = null)
                                    val unread by container.messages.observeUnreadCount(contact.id)
                                        .collectAsState(initial = 0L)
                                    val isSelected = when (val r = right) {
                                        is PanelRight.Chat   -> r.contactId == contact.id
                                        is PanelRight.Verify -> r.contactId == contact.id
                                        else -> false
                                    }
                                    PanelContactRow(
                                        contact = contact,
                                        selected = isSelected,
                                        connected = connectedSet.contains(contact.id),
                                        lastMessage = lastMsg?.body,
                                        lastMessageTs = lastMsg?.timestamp,
                                        unread = unread,
                                        onClick = { right = PanelRight.Chat(contact.id) }
                                    )
                                }
                                if (filtered.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "Eşleşen kişi yok",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                item { Spacer(Modifier.height(80.dp)) }
                            }
                            FloatingActionButton(
                                onClick = { right = PanelRight.AddContact },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = "Kişi ekle")
                            }
                        }
                    }
                }
            }
        }

        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── Sağ panel ─────────────────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (val rp = right) {
                is PanelRight.Empty -> Box(
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        BrandMark(size = 96.dp)
                        Text("Stade",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Yeni bir sohbete başlamak için sol panelden bir kişi seç.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                    }
                }

                is PanelRight.Chat -> ChatScreen(
                    container = container,
                    owner = owner,
                    contactId = rp.contactId,
                    onBack = null,
                    onVerify = { right = PanelRight.Verify(rp.contactId) },
                    onContactDeleted = { right = PanelRight.Empty }
                )

                is PanelRight.Settings -> SettingsScreen(
                    container = container,
                    owner = owner,
                    onBack = { right = PanelRight.Empty },
                    onOpenTransports = { right = PanelRight.Transports },
                    onLogout = onLogout
                )

                is PanelRight.Transports -> TransportsScreen(
                    container = container,
                    onBack = { right = PanelRight.Settings }
                )


                is PanelRight.Verify -> VerifyContactScreen(
                    container = container,
                    owner = owner,
                    contactId = rp.contactId,
                    onBack = { right = PanelRight.Chat(rp.contactId) }
                )

                else -> {}
            }
        }
    }
}

@Composable
private fun PanelContactRow(
    contact: Contact,
    selected: Boolean,
    connected: Boolean,
    lastMessage: String?,
    lastMessageTs: Long?,
    unread: Long,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sol seçim çubuğu
        Box(
            Modifier
                .size(width = 3.dp, height = 36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else Color.Transparent
                )
        )
        Spacer(Modifier.width(8.dp))

        // Avatar + online noktası
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
                    Text("✓",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.weight(1f))
                lastMessageTs?.let {
                    Text(
                        formatChatTime(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (lastMessage != null) {
                Text(
                    lastMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (unread > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(22.dp).clip(CircleShape)
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
}
