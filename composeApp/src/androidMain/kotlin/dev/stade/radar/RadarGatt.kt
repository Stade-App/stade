package dev.stade.radar

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import dev.stade.crypto.Encoding
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

internal object RadarProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("7e63a470-4e9c-4cd8-99b5-9586b85ba33e")
    val CHAR_INFO_UUID: UUID = UUID.fromString("7e63a471-4e9c-4cd8-99b5-9586b85ba33e")
    val CHAR_CONTROL_UUID: UUID = UUID.fromString("7e63a472-4e9c-4cd8-99b5-9586b85ba33e")
    val CHAR_DATA_UUID: UUID = UUID.fromString("7e63a473-4e9c-4cd8-99b5-9586b85ba33e")

    const val MANUFACTURER_ID = 0xFFFF
    const val ADV_MAGIC: Byte = 0x53
    const val VERSION: Byte = 0x03
    const val FP_BYTES = 4
    const val ADV_NICK_MAX = 19
    const val HEADER_BYTES = 3
    const val MAX_INVITE_BYTES = 64 * 1024
    const val DEFAULT_MTU = 23
    const val PREFERRED_MTU = 517
    const val MIN_CHUNK = 16
    const val MAX_CHUNK = 480
    const val PEER_TTL_MS = 15_000L
    const val FETCH_TIMEOUT_MS = 45_000L
    const val MTU_TIMEOUT_MS = 4_000L

    fun advertisePayload(nickname: String, fingerprint: String, paletteIndex: Int): ByteArray =
        byteArrayOf(ADV_MAGIC, VERSION, paletteByte(paletteIndex)) +
            fingerprintBytes(fingerprint) +
            truncateUtf8(nickname, ADV_NICK_MAX)

    private fun paletteByte(index: Int): Byte =
        if (index < 0 || index > 0xFE) 0 else (index + 1).toByte()

    private fun fingerprintBytes(fingerprint: String): ByteArray {
        val raw = runCatching { Encoding.fromHex(fingerprint) }.getOrNull()
        return if (raw != null && raw.size == FP_BYTES) raw else ByteArray(FP_BYTES)
    }

    private fun valid(raw: ByteArray?): Boolean =
        raw != null && raw.size >= HEADER_BYTES + FP_BYTES && raw[0] == ADV_MAGIC && raw[1] == VERSION

    fun readNickname(raw: ByteArray?): String? {
        if (!valid(raw)) return null
        return raw!!.copyOfRange(HEADER_BYTES + FP_BYTES, raw.size).decodeToString().trim()
    }

    fun readFingerprint(raw: ByteArray?): String {
        if (!valid(raw)) return ""
        val fp = raw!!.copyOfRange(HEADER_BYTES, HEADER_BYTES + FP_BYTES)
        return if (fp.all { it == 0.toByte() }) "" else Encoding.toHex(fp)
    }

    fun readPaletteIndex(raw: ByteArray?): Int? {
        if (!valid(raw)) return null
        val stored = raw!![2].toInt() and 0xFF
        return if (stored == 0) null else stored - 1
    }

    fun truncateUtf8(value: String, maxBytes: Int): ByteArray {
        val bytes = value.encodeToByteArray()
        if (bytes.size <= maxBytes) return bytes
        var end = maxBytes
        while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) end--
        return bytes.copyOfRange(0, end)
    }

    fun u16(value: Int): ByteArray =
        byteArrayOf(((value ushr 8) and 0xff).toByte(), (value and 0xff).toByte())

    fun u32(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xff).toByte(),
        ((value ushr 16) and 0xff).toByte(),
        ((value ushr 8) and 0xff).toByte(),
        (value and 0xff).toByte()
    )

    fun readU16(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xff) shl 8) or (buf[off + 1].toInt() and 0xff)

    fun readU32(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xff) shl 24) or
            ((buf[off + 1].toInt() and 0xff) shl 16) or
            ((buf[off + 2].toInt() and 0xff) shl 8) or
            (buf[off + 3].toInt() and 0xff)
}

@SuppressLint("MissingPermission")
internal class RadarGattServer(
    private val context: Context,
    private val manager: BluetoothManager,
    private val inviteProvider: () -> ByteArray
) {
    private val lock = Any()
    private val snapshots = HashMap<String, ByteArray>()
    private val requests = HashMap<String, IntArray>()

    @Volatile
    private var server: BluetoothGattServer? = null

    private val callback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                synchronized(lock) {
                    snapshots.remove(device.address)
                    requests.remove(device.address)
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            val srv = server ?: return
            when (characteristic.uuid) {
                RadarProtocol.CHAR_INFO_UUID -> {
                    val invite = runCatching { inviteProvider() }.getOrNull() ?: ByteArray(0)
                    synchronized(lock) {
                        snapshots[device.address] = invite
                        requests.remove(device.address)
                    }
                    respond(srv, device, requestId, offset, byteArrayOf(RadarProtocol.VERSION) + RadarProtocol.u32(invite.size))
                }
                RadarProtocol.CHAR_DATA_UUID -> {
                    var invite: ByteArray? = null
                    var request: IntArray? = null
                    synchronized(lock) {
                        invite = snapshots[device.address]
                        request = requests[device.address]
                    }
                    val body = invite
                    val req = request
                    if (body == null || req == null) {
                        srv.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                        return
                    }
                    val start = req[0].toLong() * req[1].toLong()
                    if (start >= body.size) {
                        srv.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                        return
                    }
                    val end = minOf(body.size.toLong(), start + req[1]).toInt()
                    respond(srv, device, requestId, offset, body.copyOfRange(start.toInt(), end))
                }
                else -> srv.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val srv = server ?: return
            var accepted = false
            if (characteristic.uuid == RadarProtocol.CHAR_CONTROL_UUID && value != null && value.size >= 4) {
                val index = RadarProtocol.readU16(value, 0)
                val size = RadarProtocol.readU16(value, 2).coerceIn(RadarProtocol.MIN_CHUNK, RadarProtocol.MAX_CHUNK)
                synchronized(lock) { requests[device.address] = intArrayOf(index, size) }
                accepted = true
            }
            if (responseNeeded) {
                val code = if (accepted) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
                srv.sendResponse(device, requestId, code, offset, null)
            }
        }
    }

    private fun respond(
        srv: BluetoothGattServer,
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        value: ByteArray
    ) {
        val slice = when {
            offset <= 0 -> value
            offset >= value.size -> ByteArray(0)
            else -> value.copyOfRange(offset, value.size)
        }
        srv.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
    }

    fun start(): Boolean {
        if (server != null) return true
        val srv = runCatching { manager.openGattServer(context, callback) }.getOrNull() ?: return false
        val service = BluetoothGattService(RadarProtocol.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                RadarProtocol.CHAR_INFO_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                RadarProtocol.CHAR_CONTROL_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                RadarProtocol.CHAR_DATA_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )
        val added = runCatching { srv.addService(service) }.getOrDefault(false)
        if (!added) {
            runCatching { srv.close() }
            return false
        }
        server = srv
        return true
    }

    fun stop() {
        val srv = server ?: return
        server = null
        synchronized(lock) {
            snapshots.clear()
            requests.clear()
        }
        runCatching { srv.clearServices() }
        runCatching { srv.close() }
    }
}

@SuppressLint("MissingPermission")
internal class RadarInviteFetch(
    private val context: Context,
    private val device: BluetoothDevice
) {
    private val connected = CompletableDeferred<Boolean>()
    private val servicesReady = CompletableDeferred<Boolean>()
    private val mtuReady = CompletableDeferred<Int>()
    private val lock = Any()
    private var pendingRead: CompletableDeferred<ByteArray?>? = null
    private var pendingWrite: CompletableDeferred<Boolean>? = null

    @Volatile
    private var gatt: BluetoothGatt? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connected.complete(true)
            } else {
                connected.complete(false)
                servicesReady.complete(false)
                mtuReady.complete(RadarProtocol.DEFAULT_MTU)
                completeRead(null)
                completeWrite(false)
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            mtuReady.complete(if (status == BluetoothGatt.GATT_SUCCESS) mtu else RadarProtocol.DEFAULT_MTU)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ok = status == BluetoothGatt.GATT_SUCCESS && g.getService(RadarProtocol.SERVICE_UUID) != null
            servicesReady.complete(ok)
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            completeRead(if (status == BluetoothGatt.GATT_SUCCESS) value else null)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            completeRead(if (status == BluetoothGatt.GATT_SUCCESS) c.value else null)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            completeWrite(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    private fun completeRead(value: ByteArray?) {
        synchronized(lock) { pendingRead }?.complete(value)
    }

    private fun completeWrite(ok: Boolean) {
        synchronized(lock) { pendingWrite }?.complete(ok)
    }

    private suspend fun read(c: BluetoothGattCharacteristic): ByteArray? {
        val g = gatt ?: return null
        val deferred = CompletableDeferred<ByteArray?>()
        synchronized(lock) { pendingRead = deferred }
        val started = runCatching { g.readCharacteristic(c) }.getOrDefault(false)
        if (!started) {
            synchronized(lock) { if (pendingRead === deferred) pendingRead = null }
            return null
        }
        val value = deferred.await()
        synchronized(lock) { if (pendingRead === deferred) pendingRead = null }
        return value
    }

    private suspend fun write(c: BluetoothGattCharacteristic, value: ByteArray): Boolean {
        val g = gatt ?: return false
        val deferred = CompletableDeferred<Boolean>()
        synchronized(lock) { pendingWrite = deferred }
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(c, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                c.value = value
                @Suppress("DEPRECATION")
                g.writeCharacteristic(c)
            }
        }.getOrDefault(false)
        if (!started) {
            synchronized(lock) { if (pendingWrite === deferred) pendingWrite = null }
            return false
        }
        val ok = deferred.await()
        synchronized(lock) { if (pendingWrite === deferred) pendingWrite = null }
        return ok
    }

    suspend fun fetch(onProgress: (Float) -> Unit): ByteArray? {
        val g = runCatching {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        }.getOrNull() ?: return null
        gatt = g
        if (!connected.await()) return null

        val requested = runCatching { g.requestMtu(RadarProtocol.PREFERRED_MTU) }.getOrDefault(false)
        val mtu = if (requested) {
            withTimeoutOrNull(RadarProtocol.MTU_TIMEOUT_MS) { mtuReady.await() } ?: RadarProtocol.DEFAULT_MTU
        } else {
            RadarProtocol.DEFAULT_MTU
        }

        if (!runCatching { g.discoverServices() }.getOrDefault(false)) return null
        if (!servicesReady.await()) return null

        val service = g.getService(RadarProtocol.SERVICE_UUID) ?: return null
        val info = service.getCharacteristic(RadarProtocol.CHAR_INFO_UUID) ?: return null
        val control = service.getCharacteristic(RadarProtocol.CHAR_CONTROL_UUID) ?: return null
        val data = service.getCharacteristic(RadarProtocol.CHAR_DATA_UUID) ?: return null

        val header = read(info) ?: return null
        if (header.size < 5 || header[0] != RadarProtocol.VERSION) return null
        val total = RadarProtocol.readU32(header, 1)
        if (total <= 0 || total > RadarProtocol.MAX_INVITE_BYTES) return null

        val chunkSize = (mtu - 5).coerceIn(RadarProtocol.MIN_CHUNK, RadarProtocol.MAX_CHUNK)
        val out = ByteArray(total)
        var offset = 0
        var index = 0
        while (offset < total) {
            if (!write(control, RadarProtocol.u16(index) + RadarProtocol.u16(chunkSize))) return null
            val chunk = read(data) ?: return null
            if (chunk.size != minOf(chunkSize, total - offset)) return null
            chunk.copyInto(out, offset)
            offset += chunk.size
            index++
            onProgress(offset.toFloat() / total)
        }
        return out
    }

    fun close() {
        val g = gatt ?: return
        gatt = null
        runCatching { g.disconnect() }
        runCatching { g.close() }
    }
}
