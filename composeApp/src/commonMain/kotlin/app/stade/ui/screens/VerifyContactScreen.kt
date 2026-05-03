package app.stade.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.identity.LocalIdentity

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
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(contact?.nickname ?: "", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Güvenlik numarası", style = MaterialTheme.typography.labelMedium)
                    Text(
                        safety ?: "-",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Bu numarayı yüz yüze veya güvenli bir kanaldan karşılaştır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    app.stade.ui.qr.QrCodeView(
                        payload = "stade-safety:" + (safety ?: ""),
                        modifier = Modifier.size(220.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !verified,
                onClick = {
                    container.contacts.verify(contactId)
                    verified = true
                }
            ) { Text(if (verified) "Doğrulandı ✓" else "Doğrulandı olarak işaretle") }
        }
    }
}
