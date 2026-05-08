package app.stade.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.identity.LocalIdentity
import app.stade.ui.components.BrandMark
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(container: AppContainer, onReady: (LocalIdentity) -> Unit) {
    val scope = rememberCoroutineScope()
    var nickname by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val list = container.identities.observeIdentities().first()
        if (list.isNotEmpty()) onReady(list.first()) else loading = false
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (loading) {
                BrandMark(size = 72.dp)
                Spacer(Modifier.height(16.dp))
                Text("Yükleniyor…", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                BrandMark(size = 96.dp)
                Spacer(Modifier.height(20.dp))
                Text("Stade'a hoş geldin",
                    style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Uçtan uca şifreli, sunucusuz, post-quantum güvenli mesajlaşma.\n" +
                        "Başlamak için bir takma ad seç — kalıcı bir Stade ID atanacak.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(28.dp))
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Takma ad") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.widthIn(min = 280.dp, max = 420.dp)
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    enabled = nickname.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val id = container.identities.create(nickname.trim())
                            onReady(id)
                        }
                    },
                    modifier = Modifier.widthIn(min = 280.dp, max = 420.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) { Text("Kimlik oluştur") }
            }
        }
    }
}
