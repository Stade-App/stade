package app.stade

import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import app.stade.crypto.Encoding
import app.stade.db.DriverFactory
import app.stade.db.StadeDb
import app.stade.notification.DesktopNotifier
import app.stade.notification.getRunInBackgroundEnabled
import app.stade.security.Vault
import app.stade.security.VaultFactory
import app.stade.transport.LanTransport
import app.stade.transport.TorTransport
import app.stade.transport.TransportSettings
import app.stade.transport.TransportType
import app.stade.transport.tor.EmbeddedTorManager
import app.stade.transport.tor.TorBinaryLoader
import app.stade.ui.StadeApp
import java.security.SecureRandom
import androidx.compose.ui.Alignment
import stade.composeapp.generated.resources.Res
import stade.composeapp.generated.resources.app_icon_desktop
import org.jetbrains.compose.resources.painterResource


fun main(args: Array<String>) = application {
    val boot = remember {
        val vault: Vault = VaultFactory().create()
        val torAppRoot = java.io.File(System.getProperty("user.home") ?: ".", ".stade")
        val embeddedTor = EmbeddedTorManager(torAppRoot, layoutProvider = { TorBinaryLoader.prepare(torAppRoot) })
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { kotlinx.coroutines.runBlocking { embeddedTor.shutdown() } }
            runCatching { vault.flushAndClose() }
            runCatching { DesktopNotifier.removeTray() }
        })
        BootContext(
            vault = vault,
            driverFactory = DriverFactory(),
            transportFactory = { db ->
                val nodeId = deriveNodeId(db)
                val settings = TransportSettings(db)
                listOf(
                    LanTransport(nodeId = nodeId),
                    TorTransport(
                        configProvider = { settings.get(TransportType.TOR).config },
                        embedded = embeddedTor
                    )
                )
            },
            onContainerCreated = { c ->
                pendingInviteAtBoot?.let { c.pendingInvite.value = it }
            }
        )
    }
    remember(args) {
        val path = args.firstOrNull { it.endsWith(".stadeid", ignoreCase = true) }
        if (path != null) {
            runCatching {
                val text = java.io.File(path).readText().trim()
                if (text.startsWith("STADE2-")) {
                    pendingInviteAtBoot = text
                }
            }
        }
        Unit
    }
    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.Center)
    )
    var visible by remember { mutableStateOf(true) }
    val runInBackground by getRunInBackgroundEnabled()

    remember(runInBackground) {
        if (runInBackground && java.awt.SystemTray.isSupported()) {
            DesktopNotifier.setHandlers(
                onActivate = { visible = true },
                onQuit = { exitApplication() }
            )
            DesktopNotifier.ensureTray()
        } else {
            DesktopNotifier.removeTray()
        }
        Unit
    }

    Window(
        onCloseRequest = {
            if (runInBackground && java.awt.SystemTray.isSupported()) {
                visible = false
                DesktopNotifier.showMessage("Stade", "Uygulama arka planda çalışmaya devam ediyor")
            } else {
                exitApplication()
            }
        },
        state = windowState,
        title = "Stade",
        icon = painterResource(Res.drawable.app_icon_desktop),
        visible = visible
    ) {
        SideEffect { window.minimumSize = Dimension(700, 660) }
        Surface { StadeApp(boot) }
    }
}

private var pendingInviteAtBoot: String? = null

private fun deriveNodeId(db: StadeDb): String {
    val key = "node.id"
    val existing = db.stadeDbQueries.getKv(key).executeAsOneOrNull()
    if (existing != null) return Encoding.toHex(existing)
    val rnd = ByteArray(16)
    SecureRandom().nextBytes(rnd)
    db.stadeDbQueries.putKv(key, rnd)
    return Encoding.toHex(rnd)
}
