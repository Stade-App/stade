package app.stade.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothTransport(
    private val adapterProvider: () -> BluetoothAdapter?
) : BaseTransport(TransportType.BLUETOOTH, "Bluetooth") {

    private val serviceUuid = UUID.fromString("d2a0e2bc-15ce-4dc6-9f24-f1a4f0d3b0aa")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var server: BluetoothServerSocket? = null

    override suspend fun start(handler: suspend (Connection) -> Unit) = mutex.withLock {
        val adapter = adapterProvider()
        if (adapter == null || !adapter.isEnabled) {
            state.value = TransportInfo(type, "Bluetooth", available = false, running = false, message = "kapalı")
            return@withLock
        }
        try {
            val s = adapter.listenUsingInsecureRfcommWithServiceRecord("Stade", serviceUuid)
            server = s
            state.value = TransportInfo(type, "Bluetooth", available = true, running = true)
            scope.launch { runAccept(s, handler) }
        } catch (e: Throwable) {
            state.value = TransportInfo(type, "Bluetooth", available = false, running = false, message = e.message ?: "")
        }
    }

    override suspend fun stop() = mutex.withLock {
        runCatching { server?.close() }
        server = null
        state.value = state.value.copy(running = false)
    }

    override suspend fun connect(address: String): Connection? = withContext(Dispatchers.IO) {
        val mac = address.removePrefix("bt://")
        val adapter = adapterProvider() ?: return@withContext null
        runCatching {
            val device = adapter.getRemoteDevice(mac)
            val socket = device.createInsecureRfcommSocketToServiceRecord(serviceUuid)
            socket.connect()
            BtConnection(socket, "bt://$mac")
        }.getOrNull()
    }

    override fun selfAddress(): String? {
        val a = adapterProvider() ?: return null
        return runCatching { "bt://${a.address}" }.getOrNull()
    }

    private suspend fun runAccept(server: BluetoothServerSocket, handler: suspend (Connection) -> Unit) {
        while (scope.isActive) {
            val socket = runCatching { server.accept() }.getOrNull() ?: break
            scope.launch {
                val conn = BtConnection(socket, "bt://${socket.remoteDevice.address}")
                handler(conn)
            }
        }
    }
}

private class BtConnection(private val socket: BluetoothSocket, override val remoteAddress: String) : Connection {
    private val input: InputStream = socket.inputStream
    private val output: OutputStream = socket.outputStream
    private val writeMutex = Mutex()

    override suspend fun send(frame: ByteArray) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val len = frame.size
            output.write(byteArrayOf(
                ((len ushr 24) and 0xff).toByte(),
                ((len ushr 16) and 0xff).toByte(),
                ((len ushr 8) and 0xff).toByte(),
                (len and 0xff).toByte()
            ))
            output.write(frame)
            output.flush()
        }
    }

    override suspend fun receive(): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val header = ByteArray(4)
            readFully(header)
            val len = ((header[0].toInt() and 0xff) shl 24) or
                ((header[1].toInt() and 0xff) shl 16) or
                ((header[2].toInt() and 0xff) shl 8) or
                (header[3].toInt() and 0xff)
            if (len <= 0 || len > 4 * 1024 * 1024) return@runCatching null
            val payload = ByteArray(len)
            readFully(payload)
            payload
        }.getOrNull()
    }

    private fun readFully(buffer: ByteArray) {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n <= 0) throw java.io.EOFException()
            read += n
        }
    }

    override suspend fun close() { runCatching { socket.close() } }
}
