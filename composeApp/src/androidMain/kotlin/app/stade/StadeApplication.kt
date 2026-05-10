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
import app.stade.transport.BluetoothTransport
import app.stade.transport.LanTransport
import app.stade.transport.TorTransport
import app.stade.transport.TransportSettings
import app.stade.transport.TransportType

class StadeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val driver = DriverFactory(this)
        container = AppContainer(driver) { db ->
            val nodeId = deriveNodeId(db)
            val settings = TransportSettings(db)
            listOf(
                LanTransport(nodeId = nodeId),
                TorTransport(configProvider = { settings.get(TransportType.TOR).config }),
                BluetoothTransport { bluetoothAdapter() }
            )
        }
        // Ön plan / arka plan takibi — sıfır bağımlılık, saf Android API
        var startedCount = 0
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedCount++
                container.isAppInForeground = true
            }
            override fun onActivityStopped(activity: Activity) {
                if (--startedCount <= 0) {
                    startedCount = 0
                    container.isAppInForeground = false
                }
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
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
