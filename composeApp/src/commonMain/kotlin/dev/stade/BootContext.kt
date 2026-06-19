package dev.stade

import dev.stade.db.DriverFactory
import dev.stade.db.StadeDb
import dev.stade.security.Vault
import dev.stade.transport.TransportPlugin

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

