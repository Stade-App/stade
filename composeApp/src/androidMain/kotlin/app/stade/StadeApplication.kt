package app.stade

import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Bundle
import app.stade.crypto.Encoding
import app.stade.db.DriverFactory
import app.stade.db.StadeDb
import app.stade.security.Vault
import app.stade.security.VaultFactory
import app.stade.transport.BluetoothTransport
import app.stade.transport.LanTransport
import app.stade.transport.TorTransport
import app.stade.transport.TransportSettings
import app.stade.transport.TransportType

class StadeApplication : Application() {
    lateinit var boot: BootContext
        private set

    val container: AppContainer?
        get() = activeContainer

    private var activeContainer: AppContainer? = null

    val containerFlow = kotlinx.coroutines.flow.MutableStateFlow<AppContainer?>(null)

    lateinit var vault: Vault
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        vault = VaultFactory(this).create()
        val driver = DriverFactory(this)
        boot = BootContext(
            vault = vault,
            driverFactory = driver,
            transportFactory = { db ->
                val nodeId = deriveNodeId(db)
                val settings = TransportSettings(db)
                listOf(
                    LanTransport(nodeId = nodeId),
                    TorTransport(configProvider = { settings.get(TransportType.TOR).config }),
                    BluetoothTransport { bluetoothAdapter() }
                )
            },
            onContainerCreated = { c ->
                activeContainer = c
                containerFlow.value = c
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
            runCatching { vault.flushAndClose() }
        })
    }

    override fun onTerminate() {
        runCatching { vault.flushAndClose() }
        super.onTerminate()
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val mgr = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return mgr?.adapter
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
