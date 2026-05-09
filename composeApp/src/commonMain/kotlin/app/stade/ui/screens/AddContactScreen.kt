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
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.identity.LocalIdentity
import app.stade.ui.components.StadeIdCard
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
    var pastedCode by remember { mutableStateOf("") }
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
            // Adım 1: Stade ID + davet kodu
            StepCard(stepNumber = 1, title = "Kendi davetini paylaş") {
                StadeIdCard(stadeId = owner.stadeId)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Davet kodu karşı tarafa ulaştığında bağlantı bilgileri otomatik aktarılır — " +
                        "ağ adresi vb. teknik detayları manuel paylaşmana gerek yok.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(invite.display))
                    status = "Davet kodu kopyalandı"
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Davet kodunu kopyala")
                }
            }

            // Adım 2
            StepCard(stepNumber = 2, title = "Karşı tarafın davetini gir") {
                OutlinedTextField(
                    value = pastedCode,
                    onValueChange = { pastedCode = it },
                    label = { Text("Davet kodu") },
                    placeholder = { Text("STADE2-…") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
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
                    enabled = pastedCode.isNotBlank() && alias.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    onClick = {
                        scope.launch {
                            try {
                                val parsed = container.handshake.parseInvite(pastedCode.trim())
                                if (parsed == null) {
                                    status = "Davet kodu geçersiz veya bozuk"
                                    return@launch
                                }
                                if (parsed.signingPublicKey.contentEquals(owner.publicSigningKey)) {
                                    status = "Bu senin kendi davetin"
                                    return@launch
                                }
                                if (container.contacts.findByStadeId(parsed.stadeId) != null) {
                                    status = "Bu kişi zaten ekli (${parsed.stadeId})"
                                    return@launch
                                }
                                // Bob rolündeyiz: PQ KEM ile root türet — el ile rehearse
                                // edebiliriz ama gerçek root SyncEngine handshake'ı sırasında
                                // hibrit KEM_OFFER üzerinden hesaplanacak. Burada sadece
                                // contact stub'ını oluşturmuyoruz — bağlantı kurulduğunda
                                // otomatik eklenir. Adres listesi bilgisini paylaşalım:
                                val addrs = parsed.addresses
                                if (addrs.isEmpty()) {
                                    status = "Davet kabul edildi — karşı tarafın çevrimiçi olmasını bekle"
                                } else {
                                    container.connections.queueDial(addrs)
                                    status = "Davet kabul edildi (${parsed.nickname}) — bağlanılıyor…"
                                }
                                pastedCode = ""
                                alias = ""
                            } catch (e: Exception) {
                                status = "Hata: ${e.message ?: "Bilinmeyen hata"}"
                            }
                        }
                    }
                ) { Text("Daveti kabul et") }
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
