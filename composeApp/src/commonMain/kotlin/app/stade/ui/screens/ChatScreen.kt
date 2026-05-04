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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.identity.LocalIdentity
import app.stade.message.Message
import app.stade.message.MessageDirection
import app.stade.sync.SyncEngine
import app.stade.transport.DialAttempt
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val contact = remember(contactId) { container.contacts.get(contactId) }
    val messages by container.messages.observeMessages(contactId).collectAsState(initial = emptyList())
    val connected by container.sync.connectedContacts.collectAsState()
    val isOnline = connected.contains(contactId)
    val diagnostics by container.connections.diagnostics.collectAsState()
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(contactId) {
        container.sync.events.collect { ev ->
            when (ev) {
                is SyncEngine.SyncEvent.HandshakeRejected ->
                    snackbar.showSnackbar("Bağlantı reddedildi: ${ev.reason}")
                is SyncEngine.SyncEvent.ContactConnected ->
                    if (ev.contactId == contactId) snackbar.showSnackbar("Bağlandı ✓")
                is SyncEngine.SyncEvent.DecryptFailed ->
                    if (ev.contactId == contactId)
                        snackbar.showSnackbar("Mesaj şifresi çözülemedi — kişiyi her iki tarafta da silip yeniden ekleyin")
                is SyncEngine.SyncEvent.SendFailed ->
                    if (ev.contactId == contactId)
                        snackbar.showSnackbar("Mesaj gönderilemedi: ${ev.reason}")
                else -> { }
            }
        }
    }

    LaunchedEffect(contactId, messages.size) {
        container.messages.markRead(contactId)
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    if (showDeleteDialog && contact != null) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteDialog = false },
            title = { Text("Kişiyi sil?") },
            text = {
                Text(
                    "\"${contact.nickname}\" kişisi, tüm mesajlar, bekleyen kuyruk kayıtları ve şifreleme " +
                            "anahtarları (ratchet) tamamen silinecek. Aynı kişiyle yeniden konuşmak için her iki " +
                            "tarafın da kişiyi silip yeni davet linkiyle baştan eklemesi gerekir.\n\n" +
                            "Bu işlem geri alınamaz."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        scope.launch {
                            runCatching {
                                container.sync.forgetContact(contact.id)
                                container.contacts.purge(contact.id)
                            }
                            showDeleteDialog = false
                            deleting = false
                            (onContactDeleted ?: onBack)?.invoke()
                        }
                    }
                ) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = { showDeleteDialog = false }
                ) { Text("Vazgeç") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(8.dp).clip(CircleShape).background(
                                    if (isOnline) Color(0xFF22C55E) else Color(0xFF9CA3AF)
                                )
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(contact?.nickname ?: "")
                        }
                        Text(
                            buildString {
                                append(if (isOnline) "bağlı" else "bağlı değil")
                                append(" · ")
                                append(if (contact?.verified == true) "doğrulanmış" else "doğrulanmamış")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onVerify) {
                        Icon(Icons.Default.Verified, contentDescription = "Doğrula")
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        enabled = !deleting
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Kişiyi sil",
                            tint = MaterialTheme.colorScheme.error
                        )
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
            if (!isOnline && contact != null) {
                var refreshLink by remember { mutableStateOf("") }
                var refreshStatus by remember { mutableStateOf<String?>(null) }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Bağlantı kurulamadı — tanı:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        if (contact.addresses.isEmpty()) {
                            Text(
                                "• Bu kişinin kayıtlı adresi yok.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        } else {
                            Text(
                                "• Denenmekte olan adresler:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            val perAddr = diagnostics[contactId].orEmpty()
                            contact.addresses.forEach { addr ->
                                val a = perAddr[addr]
                                val (icon, label) = when (a?.status) {
                                    DialAttempt.Status.TRYING -> "…" to "deneniyor"
                                    DialAttempt.Status.CONNECT_OK -> "•" to "bağlandı, handshake…"
                                    DialAttempt.Status.HANDSHAKE_OK -> "✓" to "bağlı"
                                    DialAttempt.Status.CONNECT_FAIL -> "✗" to (a.detail ?: "ulaşılamadı")
                                    DialAttempt.Status.HANDSHAKE_FAIL -> "✗" to (a.detail ?: "handshake hatası")
                                    null -> "·" to "henüz denenmedi"
                                }
                                Text(
                                    "   $icon  $addr — $label",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "İpucu: Tor servisi yeni başlatıldıysa onion descriptor internete yayılana kadar 5-10 dk sürebilir. Bu süre içinde \"ulaşılamadı\" görmek normaldir.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Eğer adresler eski/eksikse (örn. Tor adresi yok), karşı tarafın YENİ davet linkini buraya yapıştır:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = refreshLink,
                            onValueChange = { refreshLink = it },
                            label = { Text("stade://contact?…") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                enabled = refreshLink.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        try {
                                            val parsed = container.handshake.parseInvite(refreshLink.trim())
                                            if (parsed == null) {
                                                refreshStatus = "Geçersiz bağlantı"
                                                return@launch
                                            }
                                            if (!parsed.signingPublicKey.contentEquals(contact.publicSigningKey)) {
                                                refreshStatus = "Bu link başka bir kişiye ait"
                                                return@launch
                                            }
                                            if (parsed.addresses.isEmpty()) {
                                                refreshStatus = "Linkte adres yok — karşı tarafın da Tor'u kurması gerek"
                                                return@launch
                                            }
                                            container.contacts.setAddresses(contact.id, parsed.addresses)
                                            refreshStatus = "Adresler güncellendi ✓ (${parsed.addresses.size})"
                                            refreshLink = ""
                                        } catch (e: Exception) {
                                            refreshStatus = "Hata: ${e.message}"
                                        }
                                    }
                                }
                            ) { Text("Adresleri güncelle") }
                            OutlinedButton(
                                enabled = contact.addresses.isNotEmpty(),
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            container.contacts.setAddresses(contact.id, emptyList())
                                            refreshStatus = "Adresler temizlendi"
                                        }
                                    }
                                }
                            ) { Text("Temizle") }
                        }
                        refreshStatus?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.id }) { Bubble(it) }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Mesaj yaz…") },
                    maxLines = 4,
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    )
                )
                FilledIconButton(
                    enabled = draft.isNotBlank() && contact != null,
                    onClick = {
                        val c = contact ?: return@FilledIconButton
                        val text = draft.trim()
                        draft = ""
                        scope.launch { container.chat.send(owner, c, text) }
                    },
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gönder")
                }
            }
        }
    }
}

@Composable
private fun Bubble(msg: Message) {
    val outgoing = msg.direction == MessageDirection.OUT
    val align = if (outgoing) Alignment.End else Alignment.Start
    val bg = if (outgoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (outgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Box(
            Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (outgoing) 20.dp else 4.dp,
                    bottomEnd = if (outgoing) 4.dp else 20.dp
                ))
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(msg.body, color = fg, style = MaterialTheme.typography.bodyMedium)
                if (outgoing) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (msg.delivered) "✓ iletildi" else "bekliyor…",
                        color = fg.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
