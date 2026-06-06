package app.stade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
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
import app.stade.ui.theme.StadeTheme
import java.security.SecureRandom
import stade.composeapp.generated.resources.Res
import stade.composeapp.generated.resources.app_icon_desktop
import org.jetbrains.compose.resources.painterResource


@Volatile
private var desktopContainerRef: AppContainer? = null

fun setDesktopContainer(container: AppContainer?) {
    desktopContainerRef = container
}

fun desktopContainer(): AppContainer? = desktopContainerRef

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
                setDesktopContainer(c)
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
        position = WindowPosition.Aligned(Alignment.Center),
        size = DpSize(1100.dp, 740.dp)
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
    }

    Window(
        onCloseRequest = {
            if (runInBackground && java.awt.SystemTray.isSupported()) {
                visible = false
                DesktopNotifier.notifyBackgroundIfFirstTime(
                    "Stade",
                    "Uygulama arka planda çalışmaya devam ediyor"
                )
            } else {
                exitApplication()
            }
        },
        state = windowState,
        title = "Stade",
        icon = painterResource(Res.drawable.app_icon_desktop),
        visible = visible,
        undecorated = true
    ) {
        SideEffect { window.minimumSize = Dimension(700, 660) }

        LaunchedEffect(visible) {
            desktopContainer()?.isAppInForeground?.value = visible
        }
        LaunchedEffect(windowState.isMinimized) {
            if (windowState.isMinimized) {
                desktopContainer()?.isAppInForeground?.value = false
            } else if (visible) {
                desktopContainer()?.isAppInForeground?.value = true
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                StadeTitleBar(
                    onMinimize = { windowState.isMinimized = true },
                    onToggleMaximize = { toggleManualMaximize(window) },
                    onClose = {
                        if (runInBackground && java.awt.SystemTray.isSupported()) {
                            visible = false
                            DesktopNotifier.notifyBackgroundIfFirstTime(
                                "Stade",
                                "Uygulama arka planda çalışmaya devam ediyor"
                            )
                        } else {
                            exitApplication()
                        }
                    }
                )
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    StadeApp(boot)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun androidx.compose.ui.window.FrameWindowScope.StadeTitleBar(
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit
) {
    StadeTheme {
        val barColor = MaterialTheme.colorScheme.surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(barColor)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                WindowDragArea(window, modifier = Modifier.weight(1f).fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(start = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Stade",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                TitleBarButton(onMinimize, isClose = false) {
                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(15.dp))
                }
                TitleBarButton(onToggleMaximize, isClose = false) {
                    if (isWindowManuallyMaximized(window)) {
                        Icon(Icons.Default.FilterNone, contentDescription = null, modifier = Modifier.size(13.dp))
                    } else {
                        Icon(Icons.Default.CropSquare, contentDescription = null, modifier = Modifier.size(13.dp))
                    }
                }
                TitleBarButton(onClose, isClose = true) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun TitleBarButton(
    onClick: () -> Unit,
    isClose: Boolean,
    content: @androidx.compose.runtime.Composable () -> Unit
) {
    var hovered by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val baseColor = when {
        isClose && hovered -> Color(0xFFE81123)
        hovered -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val fg = if (isClose && hovered) Color.White else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 36.dp)
            .background(baseColor)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> hovered = true
                            PointerEventType.Exit -> {
                                hovered = false
                                pressed = false
                            }
                            PointerEventType.Press -> {
                                pressed = true
                                event.changes.forEach { it.consume() }
                            }
                            PointerEventType.Release -> {
                                if (pressed && hovered) {
                                    onClick()
                                }
                                pressed = false
                                event.changes.forEach { it.consume() }
                            }
                            else -> {}
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides fg
        ) {
            content()
        }
    }
}


private data class SavedBounds(val x: Int, val y: Int, val w: Int, val h: Int)

private val savedBoundsByWindow = java.util.WeakHashMap<java.awt.Window, SavedBounds>()

@androidx.compose.runtime.Composable
private fun WindowDragArea(
    window: java.awt.Window,
    modifier: Modifier,
    content: @androidx.compose.runtime.Composable () -> Unit
) {
    Box(
        modifier = modifier.pointerInput(window) {
            var startMouse: java.awt.Point? = null
            var startWin: java.awt.Point? = null
            var pendingMove = false
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    when (event.type) {
                        PointerEventType.Press -> {
                            val info = java.awt.MouseInfo.getPointerInfo()
                            if (info != null) {
                                startMouse = info.location
                                startWin = window.location
                            }
                            event.changes.forEach { it.consume() }
                        }
                        PointerEventType.Move -> {
                            val sm = startMouse
                            val sw = startWin
                            if (sm != null && sw != null && !pendingMove) {
                                val info = java.awt.MouseInfo.getPointerInfo() ?: continue
                                val tx = sw.x + (info.location.x - sm.x)
                                val ty = sw.y + (info.location.y - sm.y)
                                if (tx != window.x || ty != window.y) {
                                    pendingMove = true
                                    java.awt.EventQueue.invokeLater {
                                        window.setLocation(tx, ty)
                                        pendingMove = false
                                    }
                                }
                            }
                        }
                        PointerEventType.Release -> {
                            startMouse = null
                            startWin = null
                        }
                        else -> {}
                    }
                }
            }
        },
        content = { content() }
    )
}

private fun isWindowManuallyMaximized(window: java.awt.Window): Boolean =
    savedBoundsByWindow.containsKey(window)

private fun toggleManualMaximize(window: java.awt.Window) {
    val saved = savedBoundsByWindow[window]
    if (saved != null) {
        window.setBounds(saved.x, saved.y, saved.w, saved.h)
        savedBoundsByWindow.remove(window)
    } else {
        val gc = window.graphicsConfiguration ?: return
        val screenBounds = gc.bounds
        val insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc)
        val x = screenBounds.x + insets.left
        val y = screenBounds.y + insets.top
        val w = screenBounds.width - insets.left - insets.right
        val h = screenBounds.height - insets.top - insets.bottom
        savedBoundsByWindow[window] = SavedBounds(window.x, window.y, window.width, window.height)
        window.setBounds(x, y, w, h)
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
