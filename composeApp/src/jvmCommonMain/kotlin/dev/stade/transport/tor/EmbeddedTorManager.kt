package dev.stade.transport.tor

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
    private val pidProvider: () -> Long = { currentPid() }
) : EmbeddedTorRuntime {

    private val mutex = Mutex()
    private val status = MutableStateFlow<TorStatus>(TorStatus.Idle)
    override val statusFlow: StateFlow<TorStatus> = status.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var process: Process? = null
    @Volatile private var ready: TorReady? = null
    @Volatile private var controlPort: Int = 0
    @Volatile private var socksPort: Int = 0
    @Volatile private var controlClient: TorControlClient? = null

    override suspend fun ensureReady(localPort: Int, bridges: TorBridgeConfig): TorReady = mutex.withLock {
        ready?.let { return@withLock it }
        try {
            withContext(Dispatchers.IO) { bootInternal(localPort, bridges) }
        } catch (t: Throwable) {
            status.value = TorStatus.Failed(t.message ?: t::class.java.simpleName)
            throw t
        }
        ready ?: error("Tor failed to come up")
    }

    private fun bootInternal(localPort: Int, bridges: TorBridgeConfig): TorReady {
        status.value = TorStatus.Bootstrapping(0, "preparing")
        val layout = layoutProvider()
        val socks = pickFreePort()
        val ctrl = pickFreePort()
        val localTarget = if (localPort > 0) localPort else pickFreePort()
        val bridgeLines = if (bridges.enabled) bridges.allLines() else emptyList()
        if (bridges.enabled && bridgeLines.isNotEmpty() && layout.obfs4Executable == null) {
            error("Bridge support requested but the obfs4 (lyrebird) binary was not found")
        }
        val cookieFile = File(layout.dataDir, "control_auth_cookie")
        runCatching { cookieFile.delete() }
        val torrc = File(layout.dataDir, "torrc.runtime").apply {
            val logFile = File(layout.dataDir, "tor.log").absolutePath.replace('\\', '/')
            writeText(buildString {
                appendLine("DataDirectory ${layout.dataDir.absolutePath}")
                appendLine("ClientOnly 1")
                appendLine("AvoidDiskWrites 1")
                appendLine("SocksPort 127.0.0.1:$socks")
                appendLine("ControlPort 127.0.0.1:$ctrl")
                appendLine("CookieAuthentication 1")
                appendLine("CookieAuthFile ${cookieFile.absolutePath}")
                appendLine("SafeLogging 0")
                appendLine("Log notice stdout")
                appendLine("Log [handshake,rend,circ,dir]info stdout")
                appendLine("CircuitBuildTimeout 60")
                appendLine("NumEntryGuards 3")
                appendLine("NumDirectoryGuards 3")
                val ownerPid = pidProvider()
                if (ownerPid > 0) appendLine("__OwningControllerProcess $ownerPid")
                layout.geoipFile?.let { appendLine("GeoIPFile ${it.absolutePath}") }
                layout.geoip6File?.let { appendLine("GeoIPv6File ${it.absolutePath}") }
                if (bridgeLines.isNotEmpty()) {
                    val obfs4Path = layout.obfs4Executable!!.absolutePath.replace('\\', '/')
                    appendLine("ClientTransportPlugin obfs4 exec $obfs4Path")
                    appendLine("UseBridges 1")
                    bridgeLines.forEach { line -> appendLine("Bridge $line") }
                }
            })
        }
        controlPort = ctrl
        socksPort = socks

        val pb = ProcessBuilder(layout.executable.absolutePath, "-f", torrc.absolutePath)
            .redirectErrorStream(true)
            .directory(layout.torDir)
        val proc = pb.start()
        process = proc
        val logFile = File(layout.dataDir, "tor.log")
        scope.launch {
            runCatching {
                proc.inputStream.bufferedReader().use { reader ->
                    logFile.bufferedWriter().use { writer ->
                        reader.forEachLine { line ->
                            runCatching {
                                writer.write(line)
                                writer.newLine()
                                writer.flush()
                            }
                        }
                    }
                }
            }
        }
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { proc.destroy() } })

        val cookieHex = waitForCookie(cookieFile)
        val client = waitForControl(ctrl)
        var keepClient = false
        try {
            client.authenticate(cookieHex)
            val ok = client.waitForBootstrap({ pct, summary ->
                status.value = TorStatus.Bootstrapping(pct, summary)
            }, timeoutMillis = 180_000)
            if (!ok) error("Tor bootstrap timed out")
            // HS_DESC event subscription'ını ADD_ONION'dan ÖNCE açıyoruz — UPLOADED event'i kaçmasın.
            client.subscribeHsDescEvents()
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
            status.value = TorStatus.Bootstrapping(100, "publishing onion descriptor… (~30s)")
            val published = runCatching { client.waitForOnionPublished(onion.serviceId, timeoutMillis = 60_000) }.getOrDefault(false)
            val result = TorReady(
                socksHost = "127.0.0.1",
                socksPort = socks,
                onionHostname = "${onion.serviceId}.onion",
                onionVirtualPort = virtualPort,
                onionLocalPort = localTarget
            )
            ready = result
            controlClient = client
            keepClient = true
            if (published) {
                runCatching { client.unsubscribeEvents() }
                status.value = TorStatus.Ready(result.onionHostname)
            } else {
                // Descriptor henüz yayılmamış — Ready ilan ediyoruz ama arka planda izlemeye devam ediyoruz.
                status.value = TorStatus.Ready(result.onionHostname)
                scope.launch {
                    runCatching { client.waitForOnionPublished(onion.serviceId, timeoutMillis = 240_000) }
                    runCatching { client.unsubscribeEvents() }
                }
            }
            return result
        } finally {
            if (!keepClient) runCatching { client.close() }
        }
    }

    override suspend fun shutdown() = mutex.withLock {
        ready = null
        status.value = TorStatus.Idle
        runCatching { controlClient?.close() }
        controlClient = null
        process?.let { p ->
            runCatching { p.destroy() }
            if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                runCatching { p.destroyForcibly() }
            }
        }
        process = null
    }

    override fun isAlive(): Boolean {
        val p = process ?: return false
        return p.isAlive
    }

    override fun invalidate() {
        ready = null
        runCatching { controlClient?.close() }
        controlClient = null
        process?.let { p ->
            runCatching { p.destroy() }
            runCatching {
                if (!p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly()
            }
        }
        process = null
        socksPort = 0
        controlPort = 0
        status.value = TorStatus.Idle
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

/**
 * Mevcut JVM sürecinin PID'ini döner.
 *
 * Java 9+ `ProcessHandle.current().pid()` API'sini reflection ile çağırır.
 * Android veya eski JVM ortamlarında (R8 derleme dahil) `ProcessHandle` sınıfı
 * bulunmayabilir. Reflection sayesinde bu sınıfa doğrudan bir derleme zamanı
 * bağımlılığı oluşmaz ve R8 "Missing class" hatası vermez.
 * API mevcut değilse 0L döner (torrc'ye __OwningControllerProcess satırı eklenmez).
 */
private fun currentPid(): Long = runCatching {
    val phClass = Class.forName("java.lang.ProcessHandle")
    val currentMethod = phClass.getMethod("current")
    val handle = currentMethod.invoke(null)
    val pidMethod = phClass.getMethod("pid")
    (pidMethod.invoke(handle) as? Long) ?: 0L
}.getOrDefault(0L)

