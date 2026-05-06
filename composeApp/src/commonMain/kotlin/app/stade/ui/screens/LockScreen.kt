package app.stade.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.ui.components.BrandMark

@Composable
fun LockScreen(container: AppContainer, onUnlocked: () -> Unit) {
    val isSetup = remember { container.secrets.isLockEnabled() }
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark(size = 80.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                if (isSetup) "Kilidi aç" else "Bir parola belirle",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isSetup) "Devam etmek için parolanı gir."
                else "Yerel verilere erişim için bir parola seç.\nBu parola cihazdan çıkmaz.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it; error = null },
                label = { Text("Parola") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.widthIn(min = 280.dp, max = 420.dp)
            )
            if (!isSetup) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    label = { Text("Parola (tekrar)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.widthIn(min = 280.dp, max = 420.dp)
                )
            }
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                enabled = passphrase.length >= 4 && (isSetup || passphrase == confirm),
                onClick = {
                    if (isSetup) {
                        if (container.secrets.verifyPassphrase(passphrase)) onUnlocked()
                        else error = "Parola hatalı"
                    } else {
                        container.secrets.setupPassphrase(passphrase)
                        onUnlocked()
                    }
                },
                modifier = Modifier.widthIn(min = 280.dp, max = 420.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) { Text(if (isSetup) "Aç" else "Kaydet") }
        }
    }
}
