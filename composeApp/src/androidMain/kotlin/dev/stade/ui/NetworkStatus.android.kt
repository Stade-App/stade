package dev.stade.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberNetworkOnline(): Boolean {
    val context = LocalContext.current
    var online by remember { mutableStateOf(true) }

    DisposableEffect(context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        var registered: ConnectivityManager.NetworkCallback? = null

        if (manager != null) {
            fun evaluate() {
                val caps = runCatching { manager.getNetworkCapabilities(manager.activeNetwork) }.getOrNull()
                online = caps != null &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
            evaluate()
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = evaluate()
                override fun onLost(network: Network) = evaluate()
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = evaluate()
            }
            runCatching { manager.registerDefaultNetworkCallback(callback) }
                .onSuccess { registered = callback }
        }

        onDispose {
            val callback = registered
            if (manager != null && callback != null) {
                runCatching { manager.unregisterNetworkCallback(callback) }
            }
        }
    }

    return online
}
