package app.stade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stade.AppContainer
import app.stade.identity.LocalIdentity
import app.stade.ui.components.Avatar
import app.stade.ui.components.StadeIdCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyContactScreen(
    container: AppContainer,
    owner: LocalIdentity,
    contactId: String,
    onBack: () -> Unit
) {
    val contact = remember(contactId) { container.contacts.get(contactId) }
    val safety = remember(contact?.id) {
        contact?.let { container.fingerprint.safetyNumber(owner.publicSigningKey, it.publicSigningKey) }
    }
    var verified by remember(contact?.id) { mutableStateOf(contact?.verified == true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kişiyi doğrula") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Kişi başlığı
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Avatar(name = contact?.nickname ?: "?", size = 64.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(contact?.nickname ?: "", style = MaterialTheme.typography.titleMedium)
                    contact?.let {
                        Spacer(Modifier.height(10.dp))
                        StadeIdCard(stadeId = it.stadeId, title = "Karşı tarafın Stade ID'si")
                    }
                    if (verified) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Doğrulandı",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Güvenlik numarası
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Güvenlik numarası",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    SafetyNumberBlock(safety ?: "")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Bu numarayı yüz yüze veya başka bir güvenli kanaldan karşılaştır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // QR
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Box(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(Color.White)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        app.stade.ui.qr.QrCodeView(
                            payload = "stade-safety:" + (safety ?: ""),
                            modifier = Modifier.size(220.dp)
                        )
                    }
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                enabled = !verified,
                onClick = {
                    container.contacts.verify(contactId)
                    verified = true
                }
            ) {
                Text(if (verified) "Doğrulandı ✓" else "Doğrulandı olarak işaretle")
            }
        }
    }
}

/**
 * Güvenlik numarasını 5'erli gruplara bölüp 4 satıra yayan, monospace blok.
 */
@Composable
private fun SafetyNumberBlock(raw: String) {
    val digits = raw.filter { it.isDigit() }
    if (digits.isEmpty()) {
        Text("-", style = MaterialTheme.typography.titleSmall)
        return
    }
    // 5'erli gruplar
    val groups = digits.chunked(5)
    // 4 satır × 4 grup düzeni (toplam 80 hane). Eksikse esnek dolduralım.
    val perRow = if (groups.size >= 4) (groups.size + 3) / 4 else groups.size
    val rows = groups.chunked(perRow.coerceAtLeast(1))

    Column(horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Text(
                row.joinToString("  "),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}
