package dev.stade.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.NetworkInterface

private const val POLL_MS = 5_000L

@Composable
actual fun rememberNetworkOnline(): Boolean {
    var online by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            online = withContext(Dispatchers.IO) {
                runCatching {
                    NetworkInterface.getNetworkInterfaces().asSequence().any { nic ->
                        nic.isUp && !nic.isLoopback && nic.inetAddresses.hasMoreElements()
                    }
                }.getOrDefault(true)
            }
            delay(POLL_MS)
        }
    }
    return online
}
