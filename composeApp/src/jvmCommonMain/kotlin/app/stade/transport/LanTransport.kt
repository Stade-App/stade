package app.stade.transport

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

class LanTransport(
    private val nodeId: String,
    private val tcpPort: Int = 5901,
    private val discoveryPort: Int = 5902
) : BaseTransport(TransportType.LAN, "LAN"), DiscoverableTransport {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val selector = SelectorManager(Dispatchers.IO)
    private var server: ServerSocket? = null
    /** Bind sırasında esas dinlenen port (fallback denenmişse tcpPort'tan farklı olabilir). */
    @Volatile private var actualPort: Int = tcpPort
    private val discovery = DiscoveryService(nodeId, discoveryPort, tcpPort)
    private val mutex = Mutex()

    override suspend fun start(handler: suspend (Connection) -> Unit) = mutex.withLock {
        if (state.value.running) return@withLock
        val lanIps = allLocalIpv4()
        // Port 5901 başka bir uygulama tarafından (eski Stade instance, VNC, vs.)
        // tutuluyor olabilir. 5901..5910 arası fallback dene; ama davet linkleri
        // bu yeni portu içerecek (selfAddresses() actualPort'u kullanır), bu yüzden
        // güvenli. Karşı taraf yeni davet linkimizi alıp adresi günceller.
        var lastErr: Throwable? = null
        for (candidate in tcpPort..(tcpPort + 9)) {
            try {
                val s = aSocket(selector).tcp().bind(hostname = "0.0.0.0", port = candidate)
                server = s
                actualPort = candidate
                val msg = buildString {
                    if (lanIps.isEmpty()) append("0.0.0.0:$candidate")
                    else append(lanIps.joinToString(", ") { "$it:$candidate" })
                    if (candidate != tcpPort) append(" (port $tcpPort doluydu, fallback)")
                }
                state.value = TransportInfo(
                    type, "LAN",
                    available = true, running = true,
                    message = msg
                )
                scope.launch { runAccept(s, handler) }
                scope.launch { discovery.start() }
                return@withLock
            } catch (e: Throwable) {
                lastErr = e
                continue
            }
        }
        state.value = TransportInfo(type, "LAN", available = false, running = false,
            message = "$tcpPort..${tcpPort + 9} hepsi dolu — ${lastErr?.message ?: ""}")
    }

    override suspend fun stop() = mutex.withLock {
        runCatching { server?.close() }
        server = null
        discovery.stop()
        state.value = state.value.copy(running = false)
    }

    override suspend fun connect(address: String): Connection? {
        val (host, portStr) = address.removePrefix("lan://").split(":", limit = 2).let {
            it[0] to (it.getOrNull(1)?.toIntOrNull() ?: tcpPort)
        }
        return runCatching {
            val socket = aSocket(selector).tcp().connect(hostname = host, port = portStr)
            TcpConnection(socket, "lan://$host:$portStr")
        }.getOrNull()
    }

    override fun selfAddress(): String? {
        val ip = primaryIpv4() ?: return null
        return "lan://$ip:$actualPort"
    }

    /** Tüm aktif arabirimlerin site-local IPv4 adreslerini lan:// olarak döndürür. */
    override fun selfAddresses(): List<String> =
        allLocalIpv4().map { "lan://$it:$actualPort" }

    override fun discoveredPeers(): List<String> = discovery.snapshot()

    private suspend fun runAccept(server: ServerSocket, handler: suspend (Connection) -> Unit) {
        while (scope.isActive) {
            val socket = runCatching { server.accept() }.getOrNull() ?: break
            scope.launch {
                val conn = TcpConnection(socket, "lan://${socket.remoteAddress}")
                handler(conn)
            }
        }
    }

    private fun primaryIpv4(): String? = allLocalIpv4().firstOrNull()

    /** Tüm aktif (loopback olmayan) arabirimlerdeki site-local IPv4 adresleri. */
    private fun allLocalIpv4(): List<String> = runCatching {
        val out = mutableListOf<String>()
        val ifs = NetworkInterface.getNetworkInterfaces()
        for (ni in ifs) {
            if (!ni.isUp || ni.isLoopback || ni.isVirtual) continue
            for (addr in ni.inetAddresses) {
                val host = addr.hostAddress ?: continue
                if (addr.isLoopbackAddress) continue
                if (host.contains(":")) continue          // IPv6 değil
                if (!addr.isSiteLocalAddress &&
                    !host.startsWith("10.") &&
                    !host.startsWith("172.") &&
                    !host.startsWith("192.168.")) continue
                out += host
            }
        }
        out
    }.getOrDefault(emptyList())
}

internal class TcpConnection : Connection {
    private val socket: Socket
    private val reader: ByteReadChannel
    private val writer: ByteWriteChannel
    private val writeMutex = Mutex()
    override val remoteAddress: String

    /** Yeni socket: kendi read/write channel'larını aç. */
    constructor(socket: Socket, remoteAddress: String) {
        this.socket = socket
        this.reader = socket.openReadChannel()
        this.writer = socket.openWriteChannel(autoFlush = true)
        this.remoteAddress = remoteAddress
    }

    /** Mevcut channel'lara sahip socket: SOCKS5 sonrası gibi. Tekrar openReadChannel ÇAĞRILMAZ. */
    constructor(socket: Socket, reader: ByteReadChannel, writer: ByteWriteChannel, remoteAddress: String) {
        this.socket = socket
        this.reader = reader
        this.writer = writer
        this.remoteAddress = remoteAddress
    }

    override suspend fun send(frame: ByteArray): Unit = writeMutex.withLock {
        val len = frame.size
        val header = ByteArray(4)
        header[0] = ((len ushr 24) and 0xff).toByte()
        header[1] = ((len ushr 16) and 0xff).toByte()
        header[2] = ((len ushr 8) and 0xff).toByte()
        header[3] = (len and 0xff).toByte()
        writer.writeFully(header, 0, 4)
        writer.writeFully(frame, 0, frame.size)
        writer.flush()
    }

    override suspend fun receive(): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val header = ByteArray(4)
            reader.readFully(header)
            val len = ((header[0].toInt() and 0xff) shl 24) or
                ((header[1].toInt() and 0xff) shl 16) or
                ((header[2].toInt() and 0xff) shl 8) or
                (header[3].toInt() and 0xff)
            if (len <= 0 || len > 4 * 1024 * 1024) return@runCatching null
            val payload = ByteArray(len)
            reader.readFully(payload)
            payload
        }.getOrNull()
    }

    override suspend fun close() {
        runCatching { socket.close() }
    }
}

private class DiscoveryService(
    private val nodeId: String,
    private val port: Int,
    private val tcpPort: Int
) {
    private val peers = mutableMapOf<String, Long>()
    private val mutex = Mutex()
    private var rxJob: Job? = null
    private var txJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var receiver: DatagramSocket? = null
    private var sender: DatagramSocket? = null

    suspend fun start() {
        if (rxJob != null) return
        runCatching { DatagramSocket(port) }.getOrNull()?.let { sock ->
            receiver = sock.also { it.broadcast = true }
            rxJob = scope.launch { receiveLoop(sock) }
        }
        runCatching { DatagramSocket() }.getOrNull()?.let { sock ->
            sender = sock.also { it.broadcast = true }
            txJob = scope.launch { broadcastLoop(sock) }
        }
    }

    fun stop() {
        rxJob?.cancel(); txJob?.cancel()
        rxJob = null; txJob = null
        runCatching { receiver?.close() }
        runCatching { sender?.close() }
    }

    fun snapshot(): List<String> = synchronized(peers) {
        val now = System.currentTimeMillis()
        peers.entries.removeAll { now - it.value > 60_000 }
        peers.keys.toList()
    }

    private suspend fun receiveLoop(sock: DatagramSocket) {
        val buffer = ByteArray(512)
        while (scope.isActive) {
            val packet = DatagramPacket(buffer, buffer.size)
            runCatching { sock.receive(packet) }.getOrElse { return }
            val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
            val parts = text.split('|')
            if (parts.size < 3 || parts[0] != "STADE") continue
            val peerId = parts[1]
            val peerPort = parts[2].toIntOrNull() ?: continue
            if (peerId == nodeId) continue
            val address = "lan://${packet.address.hostAddress}:$peerPort"
            synchronized(peers) { peers[address] = System.currentTimeMillis() }
        }
    }

    private suspend fun broadcastLoop(sock: DatagramSocket) {
        val msg = "STADE|$nodeId|$tcpPort".toByteArray()
        while (scope.isActive) {
            for (addr in broadcastAddresses()) {
                val packet = DatagramPacket(msg, msg.size, addr, port)
                runCatching { sock.send(packet) }
            }
            kotlinx.coroutines.delay(15_000)
        }
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val out = mutableListOf<InetAddress>()
        runCatching {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) {
                    ia.broadcast?.let { out += it }
                }
            }
        }
        return out
    }
}
