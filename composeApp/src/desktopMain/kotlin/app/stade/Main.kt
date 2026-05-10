package app.stade

import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
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
import app.stade.transport.LanTransport
import app.stade.transport.TorTransport
import app.stade.transport.TransportSettings
import app.stade.transport.TransportType
import app.stade.ui.StadeApp
import java.security.SecureRandom
import androidx.compose.ui.Alignment
import stade.composeapp.generated.resources.Res
import stade.composeapp.generated.resources.app_icon_desktop
import org.jetbrains.compose.resources.painterResource


fun main() = application {
    val container = remember {
        AppContainer(DriverFactory()) { db ->
            val nodeId = deriveNodeId(db)
            val settings = TransportSettings(db)
            listOf(
                LanTransport(nodeId = nodeId),
                TorTransport(configProvider = { settings.get(TransportType.TOR).config })
            )
        }
    }
    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.Center)
    )
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Stade",
        icon = painterResource(Res.drawable.app_icon_desktop) // Otomatik üretilen kaynak
    ) {
        SideEffect { window.minimumSize = Dimension(700, 660) }
        Surface { StadeApp(container) }
    }
}

private fun deriveNodeId(db: StadeDb): String {
    val key = "node.id"
    val existing = db.stadeDbQueries.getKv(key).executeAsOneOrNull()
    if (existing != null) return Encoding.toHex(existing)
    val rnd = ByteArray(16)
    SecureRandom().nextBytes(rnd)
    db.stadeDbQueries.putKv(key, rnd)
    return Encoding.toHex(rnd)
}
