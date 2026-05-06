package app.stade.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.identity.LocalIdentity
import app.stade.message.Message
import app.stade.message.MessageDirection
import app.stade.sync.SyncEngine
import app.stade.transport.DialAttempt
import app.stade.ui.components.Avatar
import app.stade.ui.components.formatChatTime
import app.stade.ui.theme.StadeColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Bildirim tipi ──────────────────────────────────────────────────────────────
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
    val scope = rememberCoroutineScope()
    val contact = remember(contactId) { container.contacts.get(contactId) }
    val messages by container.messages.observeMessages(contactId).collectAsState(initial = emptyList())
    val connected by container.sync.connectedContacts.collectAsState()
    val isOnline = connected.contains(contactId)
    val diagnostics by container.connections.diagnostics.collectAsState()
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    // ── Bildirim durumu ──────────────────────────────────────────────────────
    var notification by remember { mutableStateOf<NotificationData?>(null) }
    var notificationKey by remember { mutableStateOf(0) }

    // Bildirim otomatik kapanma
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

    // ── Sync olayları ─────────────────────────────────────────────────────────
    LaunchedEffect(contactId) {
        container.sync.events.collect { ev ->
            when (ev) {
                is SyncEngine.SyncEvent.HandshakeRejected ->
                    showNotification("Bağlantı reddedildi: ${ev.reason}", NotificationKind.Error)
                is SyncEngine.SyncEvent.ContactConnected ->
                    if (ev.contactId == contactId)
                        showNotification("Bağlandı ✓", NotificationKind.Success)
                is SyncEngine.SyncEvent.DecryptFailed ->
                    if (ev.contactId == contactId)
                        showNotification(
                            "Mesaj şifresi çözülemedi — kişiyi her iki tarafta da silip yeniden ekleyin",
                            NotificationKind.Error
                        )
                is SyncEngine.SyncEvent.SendFailed ->
                    if (ev.contactId == contactId)
                        showNotification("Mesaj gönderilemedi: ${ev.reason}", NotificationKind.Error)
                else -> {}
            }
        }
    }

    LaunchedEffect(contactId, messages.size) { container.messages.markRead(contactId) }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    // Klavye açıldığında en son mesaja kaydır:
    // İçerik alanının yüksekliği düştüğünde (IME açıldı) scroll tetiklenir.
    var prevColumnHeight by remember { mutableStateOf(Int.MAX_VALUE) }

    // ── Silme dialog'u ────────────────────────────────────────────────────────
    if (showDeleteDialog && contact != null) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteDialog = false },
            title = { Text("Kişiyi sil?") },
            text = {
                Text(
                    "\"${contact.nickname}\" kişisi, tüm mesajlar, bekleyen kuyruk kayıtları ve şifreleme " +
                            "anahtarları (ratchet) tamamen silinecek. Aynı kişiyle yeniden konuşmak için her iki " +
                            "tarafın da kişiyi silip yeni davet linkiyle baştan eklemesi gerekir.\n\nBu işlem geri alınamaz."
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
                                    if (isOnline) "çevrimiçi" else "çevrimdışı",
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
        // ── İçerik + üst bildirim overlay'i ──────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(padding)
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxSize().onSizeChanged { size ->
                // Yükseklik azaldı → klavye açıldı → en sona kaydır
                if (size.height < prevColumnHeight && messages.isNotEmpty()) {
                    scope.launch { listState.scrollToItem(messages.lastIndex) }
                }
                prevColumnHeight = size.height
            }) {
                if (!isOnline && contact != null) {
                    DiagnosticsCard(
                        addresses = contact.addresses,
                        perAddr = diagnostics[contactId].orEmpty(),
                        onApplyInvite = { link ->
                            scope.launch {
                                try {
                                    val parsed = container.handshake.parseInvite(link.trim())
                                    when {
                                        parsed == null ->
                                            showNotification("Geçersiz bağlantı", NotificationKind.Error)
                                        !parsed.signingPublicKey.contentEquals(contact.publicSigningKey) ->
                                            showNotification("Bu link başka bir kişiye ait", NotificationKind.Error)
                                        parsed.addresses.isEmpty() ->
                                            showNotification("Linkte adres yok", NotificationKind.Error)
                                        else -> {
                                            container.contacts.setAddresses(contact.id, parsed.addresses)
                                            showNotification(
                                                "Adresler güncellendi (${parsed.addresses.size})",
                                                NotificationKind.Success
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    showNotification("Hata: ${e.message}", NotificationKind.Error)
                                }
                            }
                        },
                        onClear = {
                            scope.launch {
                                runCatching { container.contacts.setAddresses(contact.id, emptyList()) }
                                showNotification("Adresler temizlendi", NotificationKind.Info)
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
                                "Henüz mesaj yok",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "İlk mesajı sen gönder.",
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
                            Bubble(msg, tightWithPrev = tight)
                        }
                    }
                }

                Composer(
                    draft = draft,
                    onChange = { draft = it },
                    onSend = {
                        val c = contact ?: return@Composer
                        val text = draft.trim()
                        if (text.isEmpty()) return@Composer
                        draft = ""
                        scope.launch { container.chat.send(owner, c, text) }
                    }
                )
            }

            // ── Üst bildirim banneri — TopAppBar'ın hemen altında ────────────
            TopNotificationBanner(
                data = notification,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

// ── Üst bildirim banneri ──────────────────────────────────────────────────────

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

// ── DiagnosticsCard ───────────────────────────────────────────────────────────

@Composable
private fun DiagnosticsCard(
    addresses: List<String>,
    perAddr: Map<String, DialAttempt>,
    onApplyInvite: (String) -> Unit,
    onClear: () -> Unit
) {
    var refreshLink by remember { mutableStateOf("") }
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    MaterialTheme.shapes.medium
                )
                .padding(14.dp)
        ) {
            Text("Bağlantı kurulamadı", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            if (addresses.isEmpty()) {
                Text(
                    "Bu kişinin kayıtlı adresi yok. Karşı tarafın yeni davet linkini aşağıya yapıştır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Denenen adresler",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                addresses.forEach { addr ->
                    val a = perAddr[addr]
                    val (icon, label, color) = when (a?.status) {
                        DialAttempt.Status.TRYING ->
                            Triple("…", "deneniyor", MaterialTheme.colorScheme.onSurfaceVariant)
                        DialAttempt.Status.CONNECT_OK ->
                            Triple("•", "bağlandı, handshake…", MaterialTheme.colorScheme.tertiary)
                        DialAttempt.Status.HANDSHAKE_OK ->
                            Triple("✓", "bağlı", StadeColors.online)
                        DialAttempt.Status.CONNECT_FAIL ->
                            Triple("✗", a.detail ?: "ulaşılamadı", MaterialTheme.colorScheme.error)
                        DialAttempt.Status.HANDSHAKE_FAIL ->
                            Triple("✗", a.detail ?: "handshake hatası", MaterialTheme.colorScheme.error)
                        null ->
                            Triple("·", "henüz denenmedi", MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(icon, color = color, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.size(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                addr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "İpucu: Tor servisi yeni başlatıldıysa onion descriptor'ın yayılması 5–10 dk alabilir.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = refreshLink,
                onValueChange = { refreshLink = it },
                label = { Text("YENİ davet linki") },
                placeholder = { Text("stade://contact?…") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
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
                ) { Text("Adresleri güncelle") }
                OutlinedButton(
                    enabled = addresses.isNotEmpty(),
                    onClick = onClear
                ) { Text("Temizle") }
            }
        }
    }
}

// ── Composer ──────────────────────────────────────────────────────────────────

@Composable
private fun Composer(
    draft: String,
    onChange: (String) -> Unit,
    onSend: () -> Unit
) {
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
            modifier = Modifier.weight(1f),
            placeholder = { Text("Mesaj yaz…") },
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
            enabled = draft.isNotBlank(),
            onClick = onSend,
            modifier = Modifier.size(48.dp),
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

// ── Bubble ────────────────────────────────────────────────────────────────────

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
