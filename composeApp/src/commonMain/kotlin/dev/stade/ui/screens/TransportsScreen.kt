package dev.stade.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.stade.AppContainer
import dev.stade.transport.TransportType
import dev.stade.transport.isTorBuiltIn
import dev.stade.ui.components.PlatformVerticalScrollbar
import dev.stade.ui.components.maskAddress
import dev.stade.ui.i18n.LocalStrings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportsScreen(container: AppContainer, onBack: () -> Unit) {
    val strings = LocalStrings.current
    var configs by remember { mutableStateOf(container.transportSettings.all()) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.transportsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                }
            )
        }
    ) { padding ->
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                state = listState,
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
                                    Text(transportLabel(cfg.type, strings), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        when {
                                            info == null -> strings.notRegistered
                                            info!!.running -> strings.transportRunning(info!!.message)
                                            info!!.available -> strings.transportReady
                                            else -> strings.transportUnavailable(info!!.message)
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
                                Text(
                                    strings.transportStatus(maskAddress(it)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val allAddrs = plugin?.let { runCatching { it.selfAddresses() }.getOrDefault(emptyList()) } ?: emptyList()
                            if (allAddrs.size > 1) {
                                Text(
                                    strings.transportChannelsReady(allAddrs.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (cfg.type == TransportType.TOR) {
                                Spacer(Modifier.height(12.dp))
                                if (isTorBuiltIn) {
                                    Text(
                                        strings.torBuiltinNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    TorConfigEditor(
                                        initial = cfg.config,
                                        onSave = { newCfg ->
                                            container.transportSettings.setConfig(TransportType.TOR, newCfg)
                                            configs = container.transportSettings.all()
                                            scope.launch {
                                                runCatching { container.connections.restart(TransportType.TOR) }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            PlatformVerticalScrollbar(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun TorConfigEditor(initial: String, onSave: (String) -> Unit) {
    val strings = LocalStrings.current
    val initialMap = remember(initial) {
        initial.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && '=' in it }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
    }
    var onion by remember(initial) { mutableStateOf(initialMap["onion"] ?: "") }
    var port by remember(initial) { mutableStateOf(initialMap["port"] ?: "5901") }
    var listenPort by remember(initial) { mutableStateOf(initialMap["listenPort"] ?: "") }
    var socksHost by remember(initial) { mutableStateOf(initialMap["socksHost"] ?: "127.0.0.1") }
    var socksPort by remember(initial) { mutableStateOf(initialMap["socksPort"] ?: "9050") }

    Column {
        Text(
            strings.hiddenServiceDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = onion,
            onValueChange = { onion = it.trim() },
            label = { Text(strings.hiddenServiceId) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
            label = { Text(strings.onionVirtport) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = listenPort,
            onValueChange = { listenPort = it.filter { c -> c.isDigit() }.take(5) },
            label = { Text(strings.localPortLabel) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Text(
            strings.socks5Note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = socksHost,
            onValueChange = { socksHost = it.trim() },
            label = { Text(strings.socks5Host) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = socksPort,
            onValueChange = { socksPort = it.filter { c -> c.isDigit() }.take(5) },
            label = { Text(strings.socks5Port) },
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
                    if (socksHost.isNotBlank()) append("socksHost=").append(socksHost).append('\n')
                    if (socksPort.isNotBlank()) append("socksPort=").append(socksPort).append('\n')
                }
                onSave(cfg)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(strings.saveAndRestart) }
    }
}

private fun transportLabel(type: TransportType, strings: dev.stade.ui.i18n.AppStrings): String = when (type) {
    TransportType.LAN -> strings.lanLabel
    TransportType.TOR -> strings.torLabel
}
