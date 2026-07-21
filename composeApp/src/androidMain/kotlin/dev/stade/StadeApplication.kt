package dev.stade

import android.app.Activity
import android.app.Application
import android.os.Bundle
import dev.stade.crypto.Encoding
import dev.stade.db.DriverFactory
import dev.stade.db.StadeDb
import dev.stade.security.Vault
import dev.stade.security.VaultFactory
import dev.stade.transport.LanTransport
import dev.stade.transport.TorTransport
import dev.stade.transport.TransportSettings
import dev.stade.transport.TransportType
import dev.stade.transport.tor.AndroidTorBinaryLoader
import dev.stade.transport.tor.EmbeddedTorManager

class StadeApplication : Application() {
    lateinit var boot: BootContext
        private set

    val container: AppContainer?
        get() = activeContainer

    private var activeContainer: AppContainer? = null

    val containerFlow = kotlinx.coroutines.flow.MutableStateFlow<AppContainer?>(null)

    private var pendingChatAtBoot: String? = null
    private var pendingGoHomeAtBoot: Boolean = false

    /** Called by MainActivity when a notification tap should open a specific chat. */
    fun handleOpenChatIntent(contactId: String) {
        val c = activeContainer
        if (c != null) c.pendingOpenChat.value = contactId else pendingChatAtBoot = contactId
    }

    /** Called by MainActivity when a notification tap should just bring the app home. */
    fun handleGoHomeIntent() {
        val c = activeContainer
        if (c != null) c.pendingGoHome.value = true else pendingGoHomeAtBoot = true
    }

    lateinit var vault: Vault
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        vault = VaultFactory(this).create()
        val driver = DriverFactory(this)
        val torAppRoot = java.io.File(filesDir, "stade")
        val embeddedTor = runCatching {
            EmbeddedTorManager(torAppRoot, layoutProvider = { AndroidTorBinaryLoader.prepare(this, torAppRoot) })
        }.getOrNull()
        boot = BootContext(
            vault = vault,
            driverFactory = driver,
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
                activeContainer = c
                containerFlow.value = c
                pendingChatAtBoot?.let { c.pendingOpenChat.value = it }
                pendingChatAtBoot = null
                if (pendingGoHomeAtBoot) {
                    c.pendingGoHome.value = true
                    pendingGoHomeAtBoot = false
                }
            }
        )
        var startedCount = 0
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedCount++
                activeContainer?.isAppInForeground?.value = true
            }
            override fun onActivityStopped(activity: Activity) {
                if (--startedCount <= 0) {
                    startedCount = 0
                    activeContainer?.isAppInForeground?.value = false
                }
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { kotlinx.coroutines.runBlocking { embeddedTor?.shutdown() } }
            runCatching { vault.flushAndClose() }
        })
    }

    override fun onTerminate() {
        runCatching { vault.flushAndClose() }
        super.onTerminate()
    }

    private fun deriveNodeId(db: StadeDb): String {
        val key = "node.id"
        val existing = db.stadeDbQueries.getKv(key).executeAsOneOrNull()
        if (existing != null) return Encoding.toHex(existing)
        val rnd = ByteArray(16)
        java.security.SecureRandom().nextBytes(rnd)
        db.stadeDbQueries.putKv(key, rnd)
        return Encoding.toHex(rnd)
    }

    companion object {
        lateinit var instance: StadeApplication
            private set
    }
}
