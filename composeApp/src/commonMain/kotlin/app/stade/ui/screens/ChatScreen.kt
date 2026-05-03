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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.identity.LocalIdentity
import app.stade.message.Message
import app.stade.message.MessageDirection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    container: AppContainer,
    owner: LocalIdentity,
    contactId: String,
    onBack: () -> Unit,
    onVerify: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val contact = remember(contactId) { container.contacts.get(contactId) }
    val messages by container.messages.observeMessages(contactId).collectAsState(initial = emptyList())
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }

    // Yeni mesaj geldiğinde de okundu işaretle.
    LaunchedEffect(contactId, messages.size) {
        container.messages.markRead(contactId)
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(messages.lastIndex) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(contact?.nickname ?: "")
                        Text(
                            if (contact?.verified == true) "doğrulanmış" else "doğrulanmamış · uzun bas: doğrula",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    IconButton(onClick = onVerify) {
                        Icon(Icons.Default.Verified, contentDescription = "Doğrula")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.id }) { Bubble(it) }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Mesaj yaz") },
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    enabled = draft.isNotBlank() && contact != null,
                    onClick = {
                        val c = contact ?: return@IconButton
                        val text = draft.trim()
                        draft = ""
                        scope.launch { container.chat.send(owner, c, text) }
                    }
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
            Modifier.widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                Text(msg.body, color = fg)
                if (outgoing) {
                    Text(
                        if (msg.delivered) "iletildi" else "bekliyor",
                        color = fg.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
