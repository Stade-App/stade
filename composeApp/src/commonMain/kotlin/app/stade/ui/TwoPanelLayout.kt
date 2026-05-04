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
import androidx.compose.material3.TopAppBar
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
import app.stade.ui.screens.AddContactScreen
import app.stade.ui.screens.ChatScreen
import app.stade.ui.screens.SettingsScreen
import app.stade.ui.screens.TransportsScreen
import app.stade.ui.screens.VerifyContactScreen

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

    Row(modifier = Modifier.fillMaxSize()) {

        // ── Sol panel (sabit 300 dp) ──────────────────────────
        Surface(
            modifier = Modifier.width(300.dp).fillMaxHeight(),
            tonalElevation = 2.dp
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Kişiler", style = MaterialTheme.typography.titleLarge) },
                        actions = {
                            IconButton(onClick = { right = PanelRight.Settings }) {
                                Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                if (contacts.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(52.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Henüz kişin yok", style = MaterialTheme.typography.titleMedium)
                            Text("Mesajlaşmaya başlamak için\nbir kişi ekle",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center)
                            Spacer(Modifier.height(4.dp))
                            FilledTonalButton(onClick = { right = PanelRight.AddContact }) {
                                Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Kişi ekle")
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(contacts, key = { it.id }) { contact ->
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
                                    unread = unread,
                                    onClick = { right = PanelRight.Chat(contact.id) }
                                )
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                        FloatingActionButton(
                            onClick = { right = PanelRight.AddContact },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Kişi ekle")
                        }
                    }
                }
            }
        }

        VerticalDivider()

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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("Henüz bir sohbet seçilmedi",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Başlamak için sol panelden bir kişi seçin",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                is PanelRight.Chat -> ChatScreen(
                    container = container,
                    owner = owner,
                    contactId = rp.contactId,
                    onBack = null,    // İki panelde geri butonu yok
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

                is PanelRight.AddContact -> AddContactScreen(
                    container = container,
                    owner = owner,
                    onBack = { right = PanelRight.Empty }
                )

                is PanelRight.Verify -> VerifyContactScreen(
                    container = container,
                    owner = owner,
                    contactId = rp.contactId,
                    onBack = { right = PanelRight.Chat(rp.contactId) }
                )
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
    unread: Long,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(contact.nickname.firstOrNull()?.uppercase() ?: "?",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(contact.nickname, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (contact.verified) {
                    Spacer(Modifier.width(4.dp))
                    Text("✓", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(7.dp).clip(CircleShape).background(
                    if (connected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                ))
            }
            if (lastMessage != null) {
                Text(lastMessage, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (unread > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(22.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(unread.toString(), color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
