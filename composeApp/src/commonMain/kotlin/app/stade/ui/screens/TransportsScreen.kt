package app.stade.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.stade.AppContainer
import app.stade.transport.TransportType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportsScreen(container: AppContainer, onBack: () -> Unit) {
    var configs by remember { mutableStateOf(container.transportSettings.all()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Taşıma katmanları") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(configs, key = { it.type.name }) { cfg ->
                val plugin = container.transports.get(cfg.type)
                val info by (plugin?.info?.collectAsState(initial = null) ?: remember { mutableStateOf(null) })
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label(cfg.type), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    when {
                                        info == null -> "kayıtlı değil"
                                        info!!.running -> "çalışıyor · ${info!!.message}"
                                        info!!.available -> "hazır"
                                        else -> "uygun değil · ${info!!.message}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = cfg.enabled,
                                onCheckedChange = {
                                    container.transportSettings.setEnabled(cfg.type, it)
                                    configs = container.transportSettings.all()
                                }
                            )
                        }
                        plugin?.selfAddress()?.let {
                            Spacer(Modifier.height(8.dp))
                            Text("Adres: $it", style = MaterialTheme.typography.bodySmall)
                        }
                        val allAddrs = plugin?.let { runCatching { it.selfAddresses() }.getOrDefault(emptyList()) } ?: emptyList()
                        if (allAddrs.size > 1) {
                            Text(
                                "Tüm adresler:\n" + allAddrs.joinToString("\n") { "  • $it" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (cfg.type == TransportType.TOR) {
                            Spacer(Modifier.height(12.dp))
                            TorConfigEditor(
                                initial = cfg.config,
                                onSave = { newCfg ->
                                    container.transportSettings.setConfig(TransportType.TOR, newCfg)
                                    configs = container.transportSettings.all()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TorConfigEditor(initial: String, onSave: (String) -> Unit) {
    val initialMap = remember(initial) {
        initial.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && '=' in it }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
    }
    var onion by remember(initial) { mutableStateOf(initialMap["onion"] ?: "") }
    var port by remember(initial) { mutableStateOf(initialMap["port"] ?: "5901") }
    var listenPort by remember(initial) { mutableStateOf(initialMap["listenPort"] ?: "") }

    Column {
        Text(
            "Hidden Service (isteğe bağlı). torrc'de 'HiddenServicePort <port> 127.0.0.1:<yerel port>' satırı kurun ve üretilen .onion adresini girin. Yerel port boşsa Port ile aynı kullanılır; PC'de 5901 başka bir uygulama (ör. VNC) tarafından tutuluyorsa farklı bir yerel port (ör. 5921) verin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = onion,
            onValueChange = { onion = it.trim() },
            label = { Text(".onion adresi") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
            label = { Text("Port (onion VIRTPORT)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = listenPort,
            onValueChange = { listenPort = it.filter { c -> c.isDigit() }.take(5) },
            label = { Text("Yerel port (boşsa = Port)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val cfg = buildString {
                    if (onion.isNotBlank()) append("onion=").append(onion).append('\n')
                    if (port.isNotBlank()) append("port=").append(port).append('\n')
                    if (listenPort.isNotBlank()) append("listenPort=").append(listenPort).append('\n')
                    append("listenHost=127.0.0.1\n")
                }
                onSave(cfg)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Kaydet (Tor'u yeniden başlat)") }
    }
}

private fun label(type: TransportType): String = when (type) {
    TransportType.LAN -> "LAN (Wi-Fi)"
    TransportType.TOR -> "Tor (Onion)"
    TransportType.BLUETOOTH -> "Bluetooth"
    TransportType.REMOVABLE -> "Çıkarılabilir medya"
}
