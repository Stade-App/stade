package app.stade.transport.tor

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

internal class TorControlClient(host: String, port: Int, connectTimeoutMillis: Int = 10_000) : AutoCloseable {

    private val socket = Socket().apply {
        soTimeout = 30_000
        connect(InetSocketAddress(host, port), connectTimeoutMillis)
    }
    private val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
    private val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII)

    fun authenticate(cookieHex: String?) {
        val cmd = if (cookieHex.isNullOrEmpty()) "AUTHENTICATE" else "AUTHENTICATE $cookieHex"
        val reply = send(cmd)
        reply.requireOk("AUTHENTICATE")
    }

    fun takeOwnership() {
        send("TAKEOWNERSHIP").requireOk("TAKEOWNERSHIP")
    }

    fun resetConfOwningPid() {
        send("RESETCONF __OwningControllerProcess").requireOk("RESETCONF")
    }

    fun waitForBootstrap(onProgress: (Int, String) -> Unit, timeoutMillis: Long = 120_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val reply = send("GETINFO status/bootstrap-phase")
            val line = reply.lines.firstOrNull { it.contains("PROGRESS=") } ?: ""
            val pct = Regex("PROGRESS=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val summary = Regex("SUMMARY=\"([^\"]*)\"").find(line)?.groupValues?.get(1) ?: ""
            onProgress(pct, summary)
            if (pct >= 100) return true
            Thread.sleep(500)
        }
        return false
    }

    fun addOnion(privateKeyBlob: String?, virtualPort: Int, targetPort: Int): OnionInfo {
        val keyArg = privateKeyBlob ?: "NEW:ED25519-V3"
        val cmd = "ADD_ONION $keyArg Flags=Detach Port=$virtualPort,127.0.0.1:$targetPort"
        val reply = send(cmd)
        reply.requireOk("ADD_ONION")
        var serviceId: String? = null
        var newKey: String? = null
        for (line in reply.lines) {
            when {
                line.startsWith("ServiceID=") -> serviceId = line.substringAfter("ServiceID=").trim()
                line.startsWith("PrivateKey=") -> newKey = line.substringAfter("PrivateKey=").trim()
            }
        }
        val sid = serviceId ?: error("ADD_ONION did not return ServiceID")
        return OnionInfo(serviceId = sid, privateKey = newKey ?: privateKeyBlob)
    }

    fun waitForOnionPublished(serviceId: String, timeoutMillis: Long = 60_000): Boolean {
        send("SETEVENTS HS_DESC").requireOk("SETEVENTS")
        val deadline = System.currentTimeMillis() + timeoutMillis
        val prevTimeout = socket.soTimeout
        try {
            while (System.currentTimeMillis() < deadline) {
                val remaining = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(500)
                socket.soTimeout = remaining
                val line = try { reader.readLine() } catch (_: java.net.SocketTimeoutException) { null } ?: continue
                if (line.length < 4) continue
                val code = line.substring(0, 3).toIntOrNull() ?: continue
                if (code != 650) continue
                val payload = line.substring(4)
                if (!payload.startsWith("HS_DESC ")) continue
                val parts = payload.split(' ')
                if (parts.size < 3) continue
                val action = parts[1]
                val sid = parts[2]
                if (sid != serviceId) continue
                if (action == "UPLOADED") return true
                if (action == "FAILED") return false
            }
            return false
        } finally {
            socket.soTimeout = prevTimeout
            runCatching { send("SETEVENTS").requireOk("SETEVENTS") }
        }
    }

    private fun send(cmd: String): Reply {
        writer.write(cmd)
        writer.write("\r\n")
        writer.flush()
        return readReply()
    }

    private fun readReply(): Reply {
        val acc = mutableListOf<String>()
        var statusCode = 0
        while (true) {
            val raw = reader.readLine() ?: error("Tor control connection closed")
            if (raw.length < 4) error("Malformed control reply: $raw")
            statusCode = raw.substring(0, 3).toIntOrNull() ?: 0
            val sep = raw[3]
            val payload = raw.substring(4)
            if (sep == '+') {
                acc += payload
                while (true) {
                    val cont = reader.readLine() ?: error("Tor control connection closed mid-data")
                    if (cont == ".") break
                    acc += cont
                }
            } else {
                acc += payload
            }
            if (sep == ' ') return Reply(statusCode, acc.toList())
        }
        @Suppress("UNREACHABLE_CODE") return Reply(statusCode, acc.toList())
    }

    override fun close() {
        runCatching { writer.write("QUIT\r\n"); writer.flush() }
        runCatching { socket.close() }
    }

    data class Reply(val code: Int, val lines: List<String>) {
        fun requireOk(label: String) {
            if (code != 250) error("$label failed: code=$code body=${lines.joinToString(" | ")}")
        }
    }

    data class OnionInfo(val serviceId: String, val privateKey: String?)
}

