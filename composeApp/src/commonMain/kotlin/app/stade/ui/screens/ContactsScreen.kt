package app.stade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.contact.Contact
import app.stade.identity.LocalIdentity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    container: AppContainer,
    owner: LocalIdentity,
    onOpenChat: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onAddContact: () -> Unit,
    onLongPressVerify: (String) -> Unit
) {
    val contacts by container.contacts.observeContacts(owner.id).collectAsState(initial = emptyList())
    val connectedSet by container.sync.connectedContacts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stade") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddContact) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Kişi ekle")
            }
        }
    ) { padding ->
        if (contacts.isEmpty()) {
            EmptyContacts(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(contacts, key = { it.id }) { contact ->
                    val lastMsg by container.messages.observeLastMessage(contact.id).collectAsState(initial = null)
                    val unread by container.messages.observeUnreadCount(contact.id).collectAsState(initial = 0L)
                    ContactRow(
                        contact = contact,
                        connected = connectedSet.contains(contact.id),
                        lastMessage = lastMsg?.body,
                        unread = unread,
                        onClick = { onOpenChat(contact.id) },
                        onLongPress = { onLongPressVerify(contact.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun EmptyContacts(modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Henüz kişin yok", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(8.dp))
        Text("Yeni bir kişi eklemek için sağ alttaki butona dokun.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    connected: Boolean,
    lastMessage: String?,
    unread: Long,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(contact.nickname)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(contact.nickname, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                if (contact.verified) {
                    Spacer(Modifier.width(6.dp))
                    Text("✓", color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(
                        if (connected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
                )
            }
            Text(
                lastMessage ?: "Henüz mesaj yok",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (unread > 0) {
            Box(
                Modifier.size(24.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    unread.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun Avatar(nickname: String) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            nickname.firstOrNull()?.uppercase() ?: "?",
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
