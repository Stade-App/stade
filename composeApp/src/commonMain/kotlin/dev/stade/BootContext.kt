package dev.stade

import dev.stade.db.DriverFactory
import dev.stade.db.StadeDb
import dev.stade.security.SessionTimeout
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

    @Volatile private var uiUnlocked = false
    @Volatile private var leftForegroundAtMillis = 0L

    fun markUnlocked() {
        uiUnlocked = true
        leftForegroundAtMillis = 0L
    }

    fun markLocked() {
        uiUnlocked = false
        leftForegroundAtMillis = 0L
    }

    fun noteLeftForeground() {
        if (uiUnlocked && leftForegroundAtMillis == 0L) {
            leftForegroundAtMillis = vault.nowMillis()
        }
    }

    fun resolveUnlocked(): Boolean {
        if (!uiUnlocked) return false
        val left = leftForegroundAtMillis
        if (left == 0L) return true
        val timeout = vault.sessionTimeoutSeconds()
        val expired = when (timeout) {
            SessionTimeout.NEVER -> false
            SessionTimeout.IMMEDIATE -> true
            else -> vault.nowMillis() - left >= timeout.toLong() * 1000L
        }
        if (expired) {
            markLocked()
            return false
        }
        leftForegroundAtMillis = 0L
        return true
    }

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
