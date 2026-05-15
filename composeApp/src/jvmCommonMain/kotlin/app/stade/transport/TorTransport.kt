package app.stade.transport

import java.net.InetSocketAddress
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import app.stade.transport.tor.EmbeddedTorRuntime
import app.stade.transport.tor.TorStatus
import kotlinx.coroutines.flow.collectLatest

class TorTransport(
    private val defaultSocksHost: String = "127.0.0.1",
    private val defaultSocksPort: Int = 9050,
    private val configProvider: () -> String = { "" },
    private val embedded: EmbeddedTorRuntime? = null
) : BaseTransport(TransportType.TOR, "Tor") {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val selector = SelectorManager(Dispatchers.IO)
    private val mutex = Mutex()
    @Volatile private var inboundOnion: String? = null
    @Volatile private var inboundPort: Int = 0
    @Volatile private var inboundServer: ServerSocket? = null
    @Volatile private var socksHost: String = defaultSocksHost
    @Volatile private var socksPort: Int = defaultSocksPort

    override suspend fun start(handler: suspend (Connection) -> Unit) = mutex.withLock {
        if (embedded != null) {
            startEmbedded(handler)
            return@withLock
        }
        startExternal(handler)
    }

    private suspend fun startEmbedded(handler: suspend (Connection) -> Unit) {
        state.value = TransportInfo(type, "Tor", available = false, running = false, message = "Tor başlatılıyor…")
        scope.launch {
            embedded!!.statusFlow.collectLatest { st ->
                when (st) {
                    is TorStatus.Bootstrapping -> {
                        state.value = TransportInfo(type, "Tor", available = false, running = false, message = "Tor boot %${st.percent} · ${st.summary}")
                    }
                    is TorStatus.Failed -> {
                        state.value = TransportInfo(type, "Tor", available = false, running = false, message = "başlatılamadı: ${st.reason}")
                    }
                    else -> Unit
                }
            }
        }
        scope.launch {
            val ready = runCatching { embedded!!.ensureReady() }.getOrElse { err ->
                state.value = TransportInfo(type, "Tor", available = false, running = false, message = "başlatılamadı: ${err.message ?: err::class.simpleName}")
                return@launch
            }
            mutex.withLock {
                socksHost = ready.socksHost
                socksPort = ready.socksPort
                inboundOnion = ready.onionHostname
                inboundPort = ready.onionVirtualPort
                var listenError: String? = null
                runCatching {
                    val s = aSocket(selector).tcp().bind(hostname = "127.0.0.1", port = ready.onionLocalPort)
                    inboundServer = s
                    scope.launch { runAccept(s, handler) }
                }.onFailure { err ->
                    listenError = err.message ?: err::class.simpleName
                    inboundOnion = null
                    inboundPort = 0
                }
                val msg = buildString {
                    append("SOCKS5 ✓ ($socksHost:$socksPort)")
                    if (inboundOnion != null) {
                        append(" · onion :$inboundPort ✓")
                    } else if (listenError != null) {
                        append(" · onion DİNLENMİYOR ($listenError)")
                    }
                }
                state.value = TransportInfo(type, "Tor", available = true, running = true, message = msg)
            }
        }
    }

    private suspend fun startExternal(handler: suspend (Connection) -> Unit) {
        val cfg = parseConfig(configProvider())
        val cfgHost = cfg["socksHost"]?.takeIf { it.isNotBlank() } ?: defaultSocksHost
        val cfgPort = cfg["socksPort"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: defaultSocksPort
        val probeOrder = buildList {
            add(cfgHost to cfgPort)
            if (cfgHost == "127.0.0.1") {
                if (cfgPort != 9050) add("127.0.0.1" to 9050)
                if (cfgPort != 9150) add("127.0.0.1" to 9150)
            }
        }
        var socksOk = false
        var autoSwitched = false
        for ((h, p) in probeOrder) {
            if (probeSocks(h, p)) {
                socksHost = h
                socksPort = p
                socksOk = true
                autoSwitched = (h != cfgHost || p != cfgPort)
                break
            }
        }
        if (!socksOk) {
            socksHost = cfgHost
            socksPort = cfgPort
        }
        inboundOnion = cfg["onion"]?.takeIf { it.isNotBlank() }
        val publicPort = cfg["port"]?.toIntOrNull() ?: 0
        val listenPort = cfg["listenPort"]?.toIntOrNull()?.takeIf { it > 0 } ?: publicPort
        inboundPort = publicPort
        val listenHost = cfg["listenHost"] ?: "127.0.0.1"

        var listening = false
        var listenError: String? = null
        if (listenPort > 0) {
            runCatching {
                val s = aSocket(selector).tcp().bind(hostname = listenHost, port = listenPort)
                inboundServer = s
                scope.launch { runAccept(s, handler) }
                listening = true
            }.onFailure { err ->
                inboundOnion = null
                inboundPort = 0
                val raw = err.message ?: err::class.simpleName ?: "bind hatası"
                listenError = if (err is java.net.BindException) {
                    "$listenHost:$listenPort kullanımda — torrc'de farklı listenPort verin"
                } else raw
            }
        }

        val msg = buildString {
            if (socksOk) {
                append("SOCKS5 ✓ ($socksHost:$socksPort)")
                if (autoSwitched) append(" [auto]")
            } else {
                append("SOCKS5 yok — Tor/Orbot çalışmıyor (9050 ve 9150 tarandı)")
            }
            when {
                listening -> {
                    append(" · onion :$inboundPort ✓")
                    if (listenPort != inboundPort) append(" (yerel :$listenPort)")
                }
                listenError != null -> append(" · onion DİNLENMİYOR ($listenError)")
                cfg["onion"].isNullOrBlank() -> append(" · onion adresi girilmedi")
            }
        }
        val anyUp = socksOk || listening
        state.value = TransportInfo(
            type, "Tor",
            available = anyUp,
            running = anyUp,
            message = msg
        )
    }

    override suspend fun stop() = mutex.withLock {
        runCatching { inboundServer?.close() }
        inboundServer = null
        state.value = state.value.copy(running = false)
    }

    override suspend fun connect(address: String): Connection? {
        val onion = address.removePrefix("tor://")
        val parts = onion.split(":", limit = 2)
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 80
        return withTimeoutOrNull(90_000) {
            var socket: io.ktor.network.sockets.Socket? = null
            try {
                socket = aSocket(selector).tcp().connect(hostname = socksHost, port = socksPort)
                val reader = socket.openReadChannel()
                val writer = socket.openWriteChannel(autoFlush = true)

                val greeting = byteArrayOf(0x05, 0x01, 0x00)
                writer.writeFully(greeting, 0, greeting.size)
                val greetReply = ByteArray(2)
                reader.readFully(greetReply)
                if (greetReply[0] != 0x05.toByte() || greetReply[1] != 0x00.toByte()) {
                    throw IllegalStateException("SOCKS5 greeting reddedildi (Tor servisi açık mı?)")
                }

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
                val rep = head[1].toInt() and 0xff
                if (rep != 0x00) {
                    throw IllegalStateException("SOCKS5 hata: ${socksReplyMessage(rep)}")
                }
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
                val ok = TcpConnection(socket, reader, writer, "tor://$host:$port")
                socket = null
                ok
            } finally {
                socket?.let { runCatching { it.close() } }
            }
        }
    }

    private fun socksReplyMessage(code: Int): String = when (code) {
        0x01 -> "genel SOCKS sunucu hatası"
        0x02 -> "kural izin vermiyor"
        0x03 -> "ağa ulaşılamıyor"
        0x04 -> "host'a ulaşılamıyor (onion descriptor yok / yayılmadı)"
        0x05 -> "bağlantı reddedildi (karşı taraf .onion'da dinlemiyor)"
        0x06 -> "TTL süresi doldu"
        0x07 -> "komut desteklenmiyor"
        0x08 -> "adres tipi desteklenmiyor"
        else -> "kod=$code"
    }

    override fun selfAddress(): String? {
        val o = inboundOnion ?: return null
        return if (inboundPort > 0) "tor://$o:$inboundPort" else null
    }

    private suspend fun runAccept(server: ServerSocket, handler: suspend (Connection) -> Unit) {
        while (scope.isActive) {
            val socket = runCatching { server.accept() }.getOrNull() ?: break
            scope.launch {
                val conn = TcpConnection(socket, "tor://inbound")
                handler(conn)
            }
        }
    }

    private fun parseConfig(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && '=' in it }
            .map { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
            .toMap()
    }

    private suspend fun checkSocks(): Boolean = probeSocks(socksHost, socksPort)

    private suspend fun probeSocks(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            java.net.Socket().use { it.connect(InetSocketAddress(host, port), 1500) }
            true
        }.getOrDefault(false)
    }
}
