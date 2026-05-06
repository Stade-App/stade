package app.stade.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.identity.LocalIdentity
import app.stade.ui.qr.QrCodeView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(container: AppContainer, owner: LocalIdentity, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val invite = remember(owner.id) {
        container.handshake.createInvite(owner, container.connections.selfAddresses())
    }
    var alias by remember { mutableStateOf("") }
    var pastedLink by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()

    LaunchedEffect(status) {
        if (status != null) {
            delay(3000)
            status = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kişi ekle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Adım 1
            StepCard(stepNumber = 1, title = "Kendi davetini paylaş") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(androidx.compose.ui.graphics.Color.White)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        QrCodeView(payload = invite, modifier = Modifier.size(200.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    invite,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 4
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(invite))
                    status = "Davet linki kopyalandı"
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Linki kopyala")
                }
            }

            // Adım 2
            StepCard(stepNumber = 2, title = "Karşı tarafın davetini gir") {
                OutlinedTextField(
                    value = pastedLink,
                    onValueChange = { pastedLink = it },
                    label = { Text("Davet linki") },
                    placeholder = { Text("stade://contact?…") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Bu kişi için isim") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    enabled = pastedLink.isNotBlank() && alias.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    onClick = {
                        scope.launch {
                            try {
                                val parsed = container.handshake.parseInvite(pastedLink.trim())
                                if (parsed == null) {
                                    status = "Geçersiz bağlantı"
                                    return@launch
                                }
                                if (parsed.signingPublicKey.contentEquals(owner.publicSigningKey)) {
                                    status = "Bu senin kendi bağlantın"
                                    return@launch
                                }
                                if (container.contacts.findByPublicKey(parsed.signingPublicKey) != null) {
                                    status = "Bu kişi zaten ekli"
                                    return@launch
                                }
                                val rootKey = container.handshake.deriveRootKey(owner, parsed)
                                val isAlice = container.handshake.isAlice(owner, parsed)
                                container.contacts.addFromHandshake(
                                    owner = owner,
                                    nickname = alias.trim(),
                                    peerSigningKey = parsed.signingPublicKey,
                                    peerHandshakeKey = parsed.handshakePublicKey,
                                    rootKey = rootKey,
                                    isAlice = isAlice,
                                    addresses = parsed.addresses
                                )
                                status = "Kişi eklendi ✓"
                                pastedLink = ""
                                alias = ""
                            } catch (e: Exception) {
                                status = "Hata: ${e.message ?: "Bilinmeyen hata"}"
                            }
                        }
                    }
                ) { Text("Kişi olarak ekle") }
                status?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    stepNumber: Int,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stepNumber.toString(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
