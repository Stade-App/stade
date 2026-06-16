package app.stade.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class RemovableTransport(
    private val nodeId: String,
    private val configProvider: () -> String = { "" }
) : BaseTransport(TransportType.REMOVABLE, "Removable") {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val activePeers = HashSet<String>()
    @Volatile private var running = false

    override suspend fun start(handler: suspend (Connection) -> Unit) = mutex.withLock {
        if (running) return@withLock
        val root = mailboxRoot()
        if (root == null) {
            state.value = TransportInfo(type, "Removable", available = false, running = false, message = "klasör ayarlanmadı")
            return@withLock
        }
        if (!ensureDir(root)) {
            state.value = TransportInfo(type, "Removable", available = false, running = false, message = "klasör erişilemiyor: ${root.absolutePath}")
            return@withLock
        }
        running = true
        state.value = TransportInfo(type, "Removable", available = true, running = true, message = root.absolutePath)
        scope.launch { watchLoop(root, handler) }
    }

    override suspend fun stop() = mutex.withLock {
        running = false
        state.value = state.value.copy(running = false)
    }

    override suspend fun connect(address: String): Connection? {
        val peer = address.removePrefix("removable://").trim()
        if (peer.isEmpty() || peer == nodeId) return null
        val root = mailboxRoot() ?: return null
        if (!ensureDir(root)) return null
        if (!claimPeer(peer)) return null
        val conn = openConnection(root, peer)
        if (conn == null) releasePeer(peer)
        return conn
    }

    override fun selfAddress(): String? {
        val dir = parseConfig(configProvider())["dir"]?.trim()
        if (dir.isNullOrEmpty()) return null
        return if (File(dir).isDirectory) "removable://$nodeId" else null
    }

    override fun selfAddresses(): List<String> = listOfNotNull(selfAddress())

    private suspend fun watchLoop(root: File, handler: suspend (Connection) -> Unit) {
        val suffix = "__to__$nodeId"
        while (scope.isActive && running) {
            val dirs = runCatching {
                root.listFiles { f -> f.isDirectory && f.name.endsWith(suffix) }
            }.getOrNull() ?: emptyArray()
            for (dir in dirs) {
                val peer = dir.name.removeSuffix(suffix)
                if (peer.isEmpty() || peer == nodeId) continue
                if (!hasFrame(dir)) continue
                if (!claimPeer(peer)) continue
                val conn = openConnection(root, peer)
                if (conn == null) {
                    releasePeer(peer)
                    continue
                }
                scope.launch { handler(conn) }
            }
            delay(1_000)
        }
    }

    private fun openConnection(root: File, peer: String): Connection? {
        val outDir = File(root, "${nodeId}__to__$peer")
        val inDir = File(root, "${peer}__to__$nodeId")
        if (!ensureDir(outDir)) return null
        ensureDir(inDir)
        return FileMailboxConnection(
            remoteAddress = "removable://$peer",
            outDir = outDir,
            inDir = inDir,
            isRunning = { running && scope.isActive },
            onClose = { releasePeer(peer) }
        )
    }

    private fun claimPeer(peer: String): Boolean = synchronized(activePeers) { activePeers.add(peer) }

    private fun releasePeer(peer: String) {
        synchronized(activePeers) { activePeers.remove(peer) }
    }

    private fun mailboxRoot(): File? {
        val dir = parseConfig(configProvider())["dir"]?.trim()
        if (dir.isNullOrEmpty()) return null
        return File(dir, "stade-mailbox")
    }

    private fun ensureDir(f: File): Boolean = f.isDirectory || f.mkdirs()

    private fun hasFrame(dir: File): Boolean =
        runCatching { dir.listFiles { f -> f.isFile && f.name.endsWith(FRAME_SUFFIX) }?.isNotEmpty() }.getOrNull() ?: false

    private fun parseConfig(cfg: String): Map<String, String> =
        cfg.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && '=' in it }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }

    private class FileMailboxConnection(
        override val remoteAddress: String,
        private val outDir: File,
        private val inDir: File,
        private val isRunning: () -> Boolean,
        private val onClose: () -> Unit
    ) : Connection {
        private val seq = AtomicLong(0)
        private val writeMutex = Mutex()
        @Volatile private var closed = false

        override suspend fun send(frame: ByteArray) = writeMutex.withLock {
            if (closed || frame.size > MAX_FRAME) return@withLock
            withContext(Dispatchers.IO) {
                val name = frameName(seq.getAndIncrement())
                val tmp = File(outDir, "$name.tmp")
                val dst = File(outDir, "$name$FRAME_SUFFIX")
                tmp.writeBytes(frame)
                if (!tmp.renameTo(dst)) {
                    tmp.copyTo(dst, overwrite = true)
                    tmp.delete()
                }
            }
        }

        override suspend fun receive(): ByteArray? = withContext(Dispatchers.IO) {
            while (!closed && isRunning()) {
                val next = runCatching {
                    inDir.listFiles { f -> f.isFile && f.name.endsWith(FRAME_SUFFIX) }?.minByOrNull { it.name }
                }.getOrNull()
                if (next != null) {
                    val bytes = runCatching { next.readBytes() }.getOrNull()
                    runCatching { next.delete() }
                    if (bytes == null || bytes.size > MAX_FRAME) continue
                    return@withContext bytes
                }
                delay(POLL_MS)
            }
            null
        }

        override suspend fun close() {
            if (closed) return
            closed = true
            onClose()
            runCatching {
                if (outDir.isDirectory && outDir.listFiles()?.isEmpty() == true) outDir.delete()
            }
        }

        private fun frameName(n: Long): String =
            System.currentTimeMillis().toString().padStart(15, '0') + "-" + n.toString().padStart(9, '0')
    }

    companion object {
        private const val FRAME_SUFFIX = ".frame"
        private const val MAX_FRAME = 4 * 1024 * 1024
        private const val POLL_MS = 500L
    }
}
