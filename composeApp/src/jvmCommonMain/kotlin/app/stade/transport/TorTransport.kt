package app.stade.transport

import java.net.InetSocketAddress
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class TorTransport(
    private val socksHost: String = "127.0.0.1",
    private val socksPort: Int = 9050
) : BaseTransport(TransportType.TOR, "Tor") {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val selector = SelectorManager(Dispatchers.IO)
    private val mutex = Mutex()

    override suspend fun start(handler: suspend (Connection) -> Unit) = mutex.withLock {
        val available = checkSocks()
        state.value = TransportInfo(
            type, "Tor",
            available = available,
            running = available,
            message = if (available) "$socksHost:$socksPort" else "SOCKS5 yok"
        )
    }

    override suspend fun stop() = mutex.withLock {
        state.value = state.value.copy(running = false)
    }

    override suspend fun connect(address: String): Connection? {
        val onion = address.removePrefix("tor://")
        val parts = onion.split(":", limit = 2)
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 80
        return runCatching {
            val socket = aSocket(selector).tcp().connect(hostname = socksHost, port = socksPort)
            val reader = socket.openReadChannel()
            val writer = socket.openWriteChannel(autoFlush = true)

            val greeting = byteArrayOf(0x05, 0x01, 0x00)
            writer.writeFully(greeting, 0, greeting.size)
            val greetReply = ByteArray(2)
            reader.readFully(greetReply)
            require(greetReply[0] == 0x05.toByte() && greetReply[1] == 0x00.toByte()) { "socks5 greet" }

            val hostBytes = host.toByteArray(Charsets.US_ASCII)
            val req = ByteArray(7 + hostBytes.size)
            req[0] = 0x05; req[1] = 0x01; req[2] = 0x00; req[3] = 0x03
            req[4] = hostBytes.size.toByte()
            hostBytes.copyInto(req, 5)
            req[5 + hostBytes.size] = ((port ushr 8) and 0xff).toByte()
            req[6 + hostBytes.size] = (port and 0xff).toByte()
            writer.writeFully(req, 0, req.size)

            val head = ByteArray(4)
            reader.readFully(head)
            require(head[1] == 0x00.toByte()) { "socks5 connect failed: ${head[1]}" }
            val rest = when (head[3].toInt() and 0xff) {
                0x01 -> 4 + 2
                0x03 -> {
                    val nb = ByteArray(1)
                    reader.readFully(nb)
                    (nb[0].toInt() and 0xff) + 2
                }
                0x04 -> 16 + 2
                else -> 0
            }
            if (rest > 0) reader.readFully(ByteArray(rest))
            TcpConnection(socket, "tor://$host:$port")
        }.getOrNull()
    }

    override fun selfAddress(): String? = null

    private suspend fun checkSocks(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            java.net.Socket().use { it.connect(InetSocketAddress(socksHost, socksPort), 1500) }
            true
        }.getOrDefault(false)
    }
}
