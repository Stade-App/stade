package app.stade

import app.stade.db.DriverFactory
import app.stade.db.StadeDb
import app.stade.security.Vault
import app.stade.transport.TransportPlugin

class BootContext(
    val vault: Vault,
    val driverFactory: DriverFactory,
    val transportFactory: (StadeDb) -> List<TransportPlugin>,
    val onContainerCreated: (AppContainer) -> Unit = {}
) {
    fun buildContainer(): AppContainer {
        val container = AppContainer(driverFactory, vault, transportFactory)
        onContainerCreated(container)
        return container
    }
}

