package app.stade.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.identity.LocalIdentity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    owner: LocalIdentity,
    onBack: () -> Unit,
    onOpenTransports: () -> Unit,
    onLogout: () -> Unit
) {
    val fingerprint = remember(owner.id) { container.fingerprint.fingerprint(owner.publicSigningKey) }
    var confirmLogout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
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
            verticalArrangement = Arrangement.Top
        ) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Takma ad", style = MaterialTheme.typography.labelMedium)
                    Text(owner.nickname, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Text("Kimlik parmak izi", style = MaterialTheme.typography.labelMedium)
                    Text(fingerprint, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Taşıma katmanları", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenTransports) { Text("Yönet") }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (!confirmLogout) {
                OutlinedButton(onClick = { confirmLogout = true }) { Text("Oturumu kapat") }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Bu cihazda oturumu kapatmak istediğinden emin misin? Veriler korunur.",
                            textAlign = TextAlign.Start
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            TextButton(onClick = { confirmLogout = false }) { Text("Vazgeç") }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = onLogout) { Text("Çıkış") }
                        }
                    }
                }
            }
        }
    }
}
