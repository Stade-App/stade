package dev.stade.radar

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.ParcelUuid
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

actual val isRadarSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@SuppressLint("MissingPermission")
internal class AndroidRadarSession(
    private val context: Context,
    private val inviteProvider: () -> ByteArray
) : RadarSession {

    private val statusState = mutableStateOf(RadarStatus.PermissionRequired)
    private val peersState = mutableStateOf<List<RadarPeer>>(emptyList())
    private val discoverableState = mutableStateOf(false)

    override val status: RadarStatus get() = statusState.value
    override val peers: List<RadarPeer> get() = peersState.value
    override val discoverable: Boolean get() = discoverableState.value

    var onRequestPermissions: () -> Unit = {}
    var onEnableBluetooth: () -> Unit = {}

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? get() = manager?.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val lock = Any()
    private val seen = LinkedHashMap<String, RadarPeer>()

    private var nickname: String = ""
    private var fingerprint: String = ""
    private var paletteIndex: Int = -1
    private var attached = false
    private var permissionAsked = false
    private var broadcasting = true

    @Volatile
    private var fetching = false
    private var scanning = false
    private var advertiseRequested = false
    private var advertiseBlocked = false
    private var gattServer: RadarGattServer? = null
    private var expiryJob: Job? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            record(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { record(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            discoverableState.value = true
        }

        override fun onStartFailure(errorCode: Int) {
            advertiseRequested = false
            discoverableState.value = false
            if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED ||
                errorCode == AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE
            ) {
                advertiseBlocked = true
            }
        }
    }

    fun requiredPermissions(): Array<String> = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    fun attach(name: String, fp: String, palette: Int, broadcast: Boolean) {
        nickname = name
        fingerprint = fp
        paletteIndex = palette
        broadcasting = broadcast
        attached = true
        refresh()
    }

    fun configure(name: String, fp: String, palette: Int, broadcast: Boolean) {
        if (nickname == name && fingerprint == fp && paletteIndex == palette && broadcasting == broadcast) return
        nickname = name
        fingerprint = fp
        paletteIndex = palette
        broadcasting = broadcast
        if (!attached || statusState.value != RadarStatus.Scanning) return
        stopBroadcast()
        advertiseBlocked = false
        if (broadcasting) startBroadcast()
    }

    fun detach() {
        attached = false
        stopRadio()
        synchronized(lock) { seen.clear() }
        peersState.value = emptyList()
    }

    fun refresh() {
        val next = evaluate()
        statusState.value = next
        if (attached && next == RadarStatus.Scanning) startRadio() else stopRadio()
    }

    override fun resolve() {
        when (statusState.value) {
            RadarStatus.PermissionRequired -> {
                if (permissionAsked) openAppSettings() else onRequestPermissions()
                permissionAsked = true
            }
            RadarStatus.BluetoothOff -> onEnableBluetooth()
            else -> refresh()
        }
    }

    private fun openAppSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override suspend fun fetchInvite(peer: RadarPeer, onProgress: (Float) -> Unit): ByteArray? {
        val a = adapter ?: return null
        val device = runCatching { a.getRemoteDevice(peer.id) }.getOrNull() ?: return null
        val fetch = RadarInviteFetch(context, device)
        fetching = true
        pauseScan()
        return try {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(RadarProtocol.FETCH_TIMEOUT_MS) { fetch.fetch(onProgress) }
            }
        } finally {
            fetch.close()
            fetching = false
            resumeScan()
        }
    }

    private fun evaluate(): RadarStatus {
        if (!isRadarSupported) return RadarStatus.Unsupported
        val a = adapter ?: return RadarStatus.Unsupported
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return RadarStatus.Unsupported
        }
        val missing = requiredPermissions().any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) return RadarStatus.PermissionRequired
        if (!a.isEnabled) return RadarStatus.BluetoothOff
        return RadarStatus.Scanning
    }

    private fun startRadio() {
        if (broadcasting) startBroadcast() else stopBroadcast()
        startScan()
        startExpiry()
    }

    private fun stopRadio() {
        pauseScan()
        stopBroadcast()
        advertiseBlocked = false
        expiryJob?.cancel()
        expiryJob = null
    }

    private fun startBroadcast() {
        val a = adapter ?: return
        if (advertiseBlocked) return
        if (gattServer == null) {
            val m = manager
            if (m != null) {
                val srv = RadarGattServer(context, m, inviteProvider)
                gattServer = if (srv.start()) srv else null
            }
        }
        if (advertiseRequested) return
        val advertiser = a.bluetoothLeAdvertiser
        if (advertiser == null) {
            discoverableState.value = false
            return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val primary = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(RadarProtocol.SERVICE_UUID))
            .build()
        val response = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addManufacturerData(
                RadarProtocol.MANUFACTURER_ID,
                RadarProtocol.advertisePayload(nickname, fingerprint, paletteIndex)
            )
            .build()
        advertiseRequested = runCatching {
            advertiser.startAdvertising(settings, primary, response, advertiseCallback)
            true
        }.getOrDefault(false)
    }

    private fun stopBroadcast() {
        if (advertiseRequested) {
            runCatching { adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) }
            advertiseRequested = false
        }
        discoverableState.value = false
        gattServer?.stop()
        gattServer = null
    }

    private fun resumeBroadcast() {
        if (!attached || !broadcasting || advertiseRequested) return
        if (statusState.value != RadarStatus.Scanning) return
        startBroadcast()
    }

    private fun startScan() {
        if (scanning) return
        val scanner = adapter?.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(RadarProtocol.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanning = runCatching {
            scanner.startScan(listOf(filter), settings, scanCallback)
            true
        }.getOrDefault(false)
    }

    private fun pauseScan() {
        if (!scanning) return
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        scanning = false
    }

    private fun resumeScan() {
        if (fetching) return
        if (attached && statusState.value == RadarStatus.Scanning) startScan()
    }

    private fun startExpiry() {
        if (expiryJob?.isActive == true) return
        expiryJob = scope.launch {
            var ticks = 0
            while (isActive) {
                delay(2000)
                ticks++
                if (ticks % 5 == 0) {
                    resumeScan()
                    resumeBroadcast()
                }
                val now = System.currentTimeMillis()
                val snapshot = synchronized(lock) {
                    val stale = seen.filterValues { now - it.lastSeenAt > RadarProtocol.PEER_TTL_MS }.keys.toList()
                    stale.forEach { seen.remove(it) }
                    seen.values.toList()
                }
                publish(snapshot)
            }
        }
    }

    private fun record(result: ScanResult) {
        val address = result.device?.address ?: return
        val mfg = result.scanRecord?.getManufacturerSpecificData(RadarProtocol.MANUFACTURER_ID)
        val name = RadarProtocol.readNickname(mfg).orEmpty()
        val fp = RadarProtocol.readFingerprint(mfg)
        val palette = RadarProtocol.readPaletteIndex(mfg)
        val snapshot = synchronized(lock) {
            val previous = seen[address]
            seen[address] = RadarPeer(
                id = address,
                nickname = if (name.isEmpty() && previous != null) previous.nickname else name,
                fingerprint = if (fp.isEmpty() && previous != null) previous.fingerprint else fp,
                paletteIndex = palette ?: previous?.paletteIndex,
                rssi = result.rssi,
                lastSeenAt = System.currentTimeMillis()
            )
            seen.values.toList()
        }
        publish(snapshot)
    }

    private fun publish(list: List<RadarPeer>) {
        val sorted = list.sortedByDescending { it.rssi }
        scope.launch { peersState.value = sorted }
    }
}

@Composable
actual fun rememberRadarSession(
    nickname: String,
    fingerprint: String,
    paletteIndex: Int,
    active: Boolean,
    broadcasting: Boolean,
    inviteProvider: () -> ByteArray
): RadarSession {
    val context = LocalContext.current
    val currentProvider by rememberUpdatedState(inviteProvider)
    val session = remember(context) {
        AndroidRadarSession(context.applicationContext) { currentProvider() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { session.refresh() }

    val enableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { session.refresh() }

    DisposableEffect(session) {
        session.onRequestPermissions = {
            runCatching { permissionLauncher.launch(session.requiredPermissions()) }
        }
        session.onEnableBluetooth = {
            runCatching { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
        }
        onDispose {
            session.onRequestPermissions = {}
            session.onEnableBluetooth = {}
        }
    }

    DisposableEffect(session, context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                session.refresh()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    DisposableEffect(session, active) {
        if (active) session.attach(nickname, fingerprint, paletteIndex, broadcasting)
        onDispose { session.detach() }
    }

    LaunchedEffect(session, nickname, fingerprint, paletteIndex, broadcasting) {
        session.configure(nickname, fingerprint, paletteIndex, broadcasting)
    }

    return session
}
