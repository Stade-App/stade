package app.stade.transport.tor

import java.io.File
import java.net.ServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class EmbeddedTorManager(
    private val appRoot: File,
    private val virtualPort: Int = 5901,
    private val layoutProvider: () -> TorLayout,
    private val pidProvider: () -> Long = { runCatching { ProcessHandle.current().pid() }.getOrDefault(0L) }
) : EmbeddedTorRuntime {

    private val mutex = Mutex()
    private val status = MutableStateFlow<TorStatus>(TorStatus.Idle)
    override val statusFlow: StateFlow<TorStatus> = status.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var process: Process? = null
    @Volatile private var ready: TorReady? = null
    @Volatile private var controlPort: Int = 0
    @Volatile private var socksPort: Int = 0

    override suspend fun ensureReady(localPort: Int): TorReady = mutex.withLock {
        ready?.let { return@withLock it }
        try {
            withContext(Dispatchers.IO) { bootInternal(localPort) }
        } catch (t: Throwable) {
            status.value = TorStatus.Failed(t.message ?: t::class.java.simpleName)
            throw t
        }
        ready ?: error("Tor failed to come up")
    }

    private fun bootInternal(localPort: Int): TorReady {
        status.value = TorStatus.Bootstrapping(0, "preparing")
        val layout = layoutProvider()
        val socks = pickFreePort()
        val ctrl = pickFreePort()
        val localTarget = if (localPort > 0) localPort else pickFreePort()
        val cookieFile = File(layout.dataDir, "control_auth_cookie")
        val torrc = File(layout.dataDir, "torrc.runtime").apply {
            writeText(buildString {
                appendLine("DataDirectory ${layout.dataDir.absolutePath}")
                appendLine("ClientOnly 1")
                appendLine("AvoidDiskWrites 1")
                appendLine("SocksPort 127.0.0.1:$socks")
                appendLine("ControlPort 127.0.0.1:$ctrl")
                appendLine("CookieAuthentication 1")
                appendLine("CookieAuthFile ${cookieFile.absolutePath}")
                val ownerPid = pidProvider()
                if (ownerPid > 0) appendLine("__OwningControllerProcess $ownerPid")
                layout.geoipFile?.let { appendLine("GeoIPFile ${it.absolutePath}") }
                layout.geoip6File?.let { appendLine("GeoIPv6File ${it.absolutePath}") }
            })
        }
        controlPort = ctrl
        socksPort = socks

        val pb = ProcessBuilder(layout.executable.absolutePath, "-f", torrc.absolutePath)
            .redirectErrorStream(true)
            .directory(layout.torDir)
        val proc = pb.start()
        process = proc
        scope.launch {
            proc.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { _ -> }
            }
        }
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { proc.destroy() } })

        val cookieHex = waitForCookie(cookieFile)
        val client = waitForControl(ctrl)
        try {
            client.authenticate(cookieHex)
            client.takeOwnership()
            client.resetConfOwningPid()
            val ok = client.waitForBootstrap({ pct, summary ->
                status.value = TorStatus.Bootstrapping(pct, summary)
            }, timeoutMillis = 180_000)
            if (!ok) error("Tor bootstrap timed out")
            val keyStore = File(appRoot, "tor/onion.key")
            val existingKey = keyStore.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
            val onion = client.addOnion(existingKey, virtualPort, localTarget)
            if (existingKey == null && onion.privateKey != null) {
                keyStore.parentFile?.mkdirs()
                keyStore.writeText(onion.privateKey)
                runCatching {
                    val perms = java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")
                    java.nio.file.Files.setPosixFilePermissions(keyStore.toPath(), perms)
                }
            }
            val result = TorReady(
                socksHost = "127.0.0.1",
                socksPort = socks,
                onionHostname = "${onion.serviceId}.onion",
                onionVirtualPort = virtualPort,
                onionLocalPort = localTarget
            )
            ready = result
            status.value = TorStatus.Ready(result.onionHostname)
            return result
        } finally {
            runCatching { client.close() }
        }
    }

    override suspend fun shutdown() = mutex.withLock {
        ready = null
        status.value = TorStatus.Idle
        process?.let { p ->
            runCatching { p.destroy() }
            if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                runCatching { p.destroyForcibly() }
            }
        }
        process = null
    }

    private fun waitForCookie(file: File): String {
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            if (file.exists() && file.length() == 32L) {
                return file.readBytes().joinToString("") { "%02x".format(it) }
            }
            Thread.sleep(100)
        }
        error("Tor control cookie not produced within timeout")
    }

    private fun waitForControl(port: Int): TorControlClient {
        val deadline = System.currentTimeMillis() + 20_000
        var last: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                return TorControlClient("127.0.0.1", port, connectTimeoutMillis = 1_000)
            } catch (t: Throwable) {
                last = t
                Thread.sleep(150)
            }
        }
        throw IllegalStateException("Tor control port did not open: ${last?.message}")
    }

    private fun pickFreePort(): Int {
        ServerSocket(0).use { return it.localPort }
    }
}

