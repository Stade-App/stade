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
    private val lock = Any()
    private var active: AppContainer? = null

    fun activeContainer(): AppContainer? =
        synchronized(lock) { active?.takeIf { !it.isClosed } }

    fun buildContainer(): AppContainer = synchronized(lock) {
        active?.takeIf { !it.isClosed }?.let { return it }
        val container = AppContainer(driverFactory, vault, transportFactory)
        active = container
        onContainerCreated(container)
        container
    }
}
