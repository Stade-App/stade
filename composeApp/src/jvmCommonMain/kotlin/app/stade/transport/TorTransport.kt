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

/**
 * SOCKS5 üzerinden Tor'a outbound bağlantı + isteğe bağlı inbound Hidden Service desteği.
 *
 * Inbound config formatı (TransportSetting.config alanında):
 *   onion=abcd...xyz.onion
 *   port=5901          # Onion'un public (VIRTPORT) portu — davet linkinde görünen port
 *   listenPort=5901    # (opsiyonel) Yerel TCP dinleyici portu. Boşsa `port` ile aynıdır.
 *                      # PC'de 5901 sıkça VNC tarafından tutulduğu için farklı bir
 *                      # yerel port (ör. 5921) verilebilir.
 *   listenHost=127.0.0.1
 *
 * Kullanıcı torrc'sinde şu satırları ekler:
 *   HiddenServiceDir /var/lib/tor/stade/
 *   HiddenServicePort <port> 127.0.0.1:<listenPort>
 * Sonra üretilen `hostname` dosyasındaki onion adresini "Tor adresi" alanına yapıştırır.
 */
class TorTransport(
    private val socksHost: String = "127.0.0.1",
    private val socksPort: Int = 9050,
    private val configProvider: () -> String = { "" }
) : BaseTransport(TransportType.TOR, "Tor") {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val selector = SelectorManager(Dispatchers.IO)
    private val mutex = Mutex()
    @Volatile private var inboundOnion: String? = null
    @Volatile private var inboundPort: Int = 0
    @Volatile private var inboundServer: ServerSocket? = null

    override suspend fun start(handler: suspend (Connection) -> Unit) = mutex.withLock {
        val socksOk = checkSocks()
        // Inbound listener (varsa).
        val cfg = parseConfig(configProvider())
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
                // Liman tutulamadıysa selfAddress'i bildirme — yanlış adres dağıtmayalım.
                inboundOnion = null
                inboundPort = 0
                val raw = err.message ?: err::class.simpleName ?: "bind hatası"
                listenError = if (err is java.net.BindException) {
                    "$listenHost:$listenPort kullanımda — torrc'de farklı listenPort verin"
                } else raw
            }
        }

        val msg = buildString {
            if (socksOk) append("SOCKS5 ✓") else append("SOCKS5 yok")
            when {
                listening -> {
                    append(" · onion :$inboundPort ✓")
                    if (listenPort != inboundPort) append(" (yerel :$listenPort)")
                }
                listenError != null -> append(" · onion DİNLENMİYOR ($listenError)")
                cfg["onion"].isNullOrBlank() -> append(" · onion adresi girilmedi")
            }
        }
        state.value = TransportInfo(
            type, "Tor",
            available = socksOk,
            running = socksOk,
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
        // Tor üzerinden onion bağlantısı; descriptor henüz publish edilmediyse 60-90sn sürebilir.
        // withTimeoutOrNull yalnızca timeout durumunda null döner; protokol hataları
        // exception olarak yukarı (ConnectionManager'ın runCatching'ine) gider ve
        // tanı kartında gerçek SOCKS5 yanıt kodu gösterilir.
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
                // ÖNEMLİ: SOCKS5 anlaşması için açtığımız reader/writer'ı TcpConnection'a
                // aynen ver. Aksi halde TcpConnection kendi içinde openReadChannel() çağırır
                // ve Ktor "reading channel has already been set" hatası fırlatır.
                val ok = TcpConnection(socket, reader, writer, "tor://$host:$port")
                socket = null  // ownership transferred
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

    private suspend fun checkSocks(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            java.net.Socket().use { it.connect(InetSocketAddress(socksHost, socksPort), 1500) }
            true
        }.getOrDefault(false)
    }
}
