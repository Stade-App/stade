package app.stade.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import app.stade.ui.components.Avatar
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import app.stade.ui.theme.StadeColors
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.contact.Contact
import app.stade.identity.LocalIdentity
import app.stade.ui.PlatformBackHandler
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // ── Uzun basma aksiyon durumu ─────────────────────────────────────────────
    var actionContact by remember { mutableStateOf<Contact?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    // ── Uzun basma aksiyon dialog'u ───────────────────────────────────────────
    if (actionContact != null && !showDeleteConfirm) {
        val c = actionContact!!
        AlertDialog(
            onDismissRequest = { actionContact = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c.nickname)
                    Spacer(Modifier.width(12.dp))
                    Text(c.nickname, style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                actionContact = null
                                onLongPressVerify(c.id)
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Verified, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Doğrulama kodunu göster", style = MaterialTheme.typography.bodyLarge)
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeleteConfirm = true }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            "Kişiyi sil",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    HorizontalDivider()
                }
            },
            confirmButton = {
                TextButton(onClick = { actionContact = null }) { Text("Vazgeç") }
            }
        )
    }

    // ── Silme onayı dialog'u ──────────────────────────────────────────────────
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
            title = { Text("\"${c.nickname}\" silinsin mi?") },
            text = {
                Text(
                    "Tüm mesajlar, bekleyen kayıtlar ve şifreleme anahtarları kalıcı olarak silinecek. " +
                            "Yeniden konuşmak için her iki tarafın da kişiyi silip baştan eklemesi gerekir.\n\n" +
                            "Bu işlem geri alınamaz.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        scope.launch {
                            runCatching {
                                container.sync.forgetContact(c.id)
                                container.contacts.purge(c.id)
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
                ) { Text("Sil") }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        showDeleteConfirm = false
                        actionContact = null
                    }
                ) { Text("Vazgeç") }
            }
        )
    }

    // Kişi sayısı 1'e düşerse aramayı kapat
    LaunchedEffect(contacts.size) {
        if (contacts.size <= 1 && searchActive) {
            searchActive = false
            query = ""
        }
    }

    // Arama açıldığında klavyeyi göster
    LaunchedEffect(searchActive) {
        if (searchActive) focusRequester.requestFocus()
    }

    // Geri tuşuyla aramayı kapat
    PlatformBackHandler(enabled = searchActive) {
        searchActive = false
        query = ""
    }

    val filtered = remember(contacts, query, searchActive) {
        if (!searchActive || query.isBlank()) contacts
        else contacts.filter { it.nickname.contains(query.trim(), ignoreCase = true) }
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Aramayı kapat")
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
                                        "Kişi ara…",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge,
                                trailingIcon = {
                                    if (query.isNotEmpty()) {
                                        IconButton(onClick = { query = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Temizle")
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
                            Text("Stade")
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
                                    Icon(Icons.Default.Search, contentDescription = "Ara")
                                }
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                            }
                        }
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
                items(filtered, key = { it.id }) { contact ->
                    val lastMsg by container.messages.observeLastMessage(contact.id).collectAsState(initial = null)
                    val unread by container.messages.observeUnreadCount(contact.id).collectAsState(initial = 0L)
                    ContactRow(
                        contact = contact,
                        connected = connectedSet.contains(contact.id),
                        lastMessage = lastMsg?.body,
                        unread = unread,
                        onClick = { onOpenChat(contact.id) },
                        onLongPress = { actionContact = contact }
                    )
                }
                if (searchActive && filtered.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Eşleşen kişi yok",
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent, // Arka planı şeffaf bırakıyoruz
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onClick() },
                    onLongClick = { onLongPress() }
                )
                .padding(horizontal = 16.dp, vertical = 16.dp), // Dikey boşluğu artırdık
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar + aktiflik noktası (TwoPanelLayout ile aynı şekilde)
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

            // Bilgi Bölümü
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
                    lastMessage ?: "Henüz mesaj yok",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), // Hafif şeffaflık modern gösterir
                    maxLines = 1
                )
            }

            // Okunmamış sayısı
            if (unread > 0) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        unread.toString(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun Avatar(nickname: String, size: Dp = 52.dp) { // Boyutu 52.dp yaptık
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            nickname.firstOrNull()?.uppercase() ?: "?",
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.titleMedium // Yazıyı da hafif büyüttük
        )
    }
}