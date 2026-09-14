package dev.stade.pad

import dev.stade.AppContainer
import dev.stade.transport.TorTransport
import dev.stade.transport.TransportType
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Url
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

private const val INITIAL_READ_BUFFER = 32 * 1024

private val json = Json { ignoreUnknownKeys = true }

private const val ASSET_CACHE_MAX_ENTRIES = 8
private const val ASSET_CACHE_MAX_BYTES = 8 * 1024 * 1024

private val assetCache = LinkedHashMap<String, ByteArray>(0, 0.75f, true)

private fun cachedAsset(sha: String): ByteArray? = synchronized(assetCache) { assetCache[sha] }

private fun rememberAsset(sha: String, bytes: ByteArray) {
    if (bytes.size > ASSET_CACHE_MAX_BYTES) return
    synchronized(assetCache) {
        assetCache[sha] = bytes
        var total = assetCache.values.sumOf { it.size }
        val iterator = assetCache.entries.iterator()
        while (iterator.hasNext() && (assetCache.size > ASSET_CACHE_MAX_ENTRIES || total > ASSET_CACHE_MAX_BYTES)) {
            val entry = iterator.next()
            total -= entry.value.size
            iterator.remove()
        }
    }
}

actual fun padNetworkReady(container: AppContainer): Boolean {
    val tor = container.transports.get(TransportType.TOR) as? TorTransport ?: return false
    return tor.socksProxyAddress() != null
}

private suspend fun torFetch(
    container: AppContainer,
    url: String,
    maxBytes: Int,
    timeoutMs: Long = PadConfig.FETCH_TIMEOUT_MS
): ByteArray? {
    val tor = container.transports.get(TransportType.TOR) as? TorTransport ?: return null
    val proxyAddr = tor.socksProxyAddress() ?: return null
    val parsed = runCatching { Url(url) }.getOrNull() ?: return null
    if (parsed.protocol.name != "https") return null

    return withTimeoutOrNull(timeoutMs) {
        runCatching {
            val client = HttpClient(CIO) {
                expectSuccess = false
                install(HttpTimeout) { requestTimeoutMillis = timeoutMs }
                engine { proxy = ProxyBuilder.socks(proxyAddr.first, proxyAddr.second) }
            }
            try {
                val response: HttpResponse = client.get(url)
                if (response.status.value != 200) return@runCatching null
                val channel = response.bodyAsChannel()
                val sink = ByteArrayOutputStream(INITIAL_READ_BUFFER)
                val chunk = ByteArray(INITIAL_READ_BUFFER)
                while (sink.size() <= maxBytes) {
                    val read = channel.readAvailable(chunk, 0, chunk.size)
                    if (read == -1) break
                    if (read > 0) sink.write(chunk, 0, read)
                }
                if (sink.size() <= 0 || sink.size() > maxBytes) return@runCatching null
                sink.toByteArray()
            } finally {
                client.close()
            }
        }.getOrNull()
    }
}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { b -> ((b.toInt() and 0xff) + 0x100).toString(16).substring(1) }

actual suspend fun fetchPadCatalog(container: AppContainer, force: Boolean): PadCatalog? =
    withContext(Dispatchers.IO) {
        val queries = container.db.stadeDbQueries
        val now = System.currentTimeMillis()

        if (!force) {
            val cachedAt = runCatching {
                queries.getKv(PadConfig.CATALOG_CACHE_AT_KEY).executeAsOneOrNull()
                    ?.decodeToString()?.toLongOrNull()
            }.getOrNull() ?: 0L
            if (now - cachedAt < PadConfig.CATALOG_TTL_MS) {
                val cached = runCatching {
                    queries.getKv(PadConfig.CATALOG_CACHE_KEY).executeAsOneOrNull()?.decodeToString()
                }.getOrNull()
                if (cached != null) {
                    runCatching { json.decodeFromString(PadCatalog.serializer(), cached) }
                        .getOrNull()?.let { return@withContext it }
                }
            }
        }

        val raw = torFetch(container, PadConfig.CATALOG_URL, PadConfig.MAX_CATALOG_BYTES)
        if (raw == null) {
            val cached = runCatching {
                queries.getKv(PadConfig.CATALOG_CACHE_KEY).executeAsOneOrNull()?.decodeToString()
            }.getOrNull() ?: return@withContext null
            return@withContext runCatching {
                json.decodeFromString(PadCatalog.serializer(), cached)
            }.getOrNull()
        }

        val text = raw.decodeToString()
        val parsed = runCatching { json.decodeFromString(PadCatalog.serializer(), text) }.getOrNull()
            ?: return@withContext null
        val cleaned = PadCatalog(
            version = parsed.version,
            sounds = parsed.sounds.filter { it.hasIntegrity },
            memes = parsed.memes.filter { it.hasIntegrity }
        )
        runCatching {
            queries.putKv(PadConfig.CATALOG_CACHE_KEY, text.encodeToByteArray())
            queries.putKv(PadConfig.CATALOG_CACHE_AT_KEY, now.toString().encodeToByteArray())
        }
        cleaned
    }

actual suspend fun fetchPadAsset(
    container: AppContainer,
    url: String,
    expectedSha256: String,
    maxBytes: Int
): ByteArray? = withContext(Dispatchers.IO) {
    if (expectedSha256.length != 64) return@withContext null
    val key = expectedSha256.lowercase()
    cachedAsset(key)?.let { return@withContext it }
    val bytes = torFetch(container, url, maxBytes, PadConfig.ASSET_TIMEOUT_MS)
        ?: return@withContext null
    if (!sha256Hex(bytes).equals(expectedSha256, ignoreCase = true)) return@withContext null
    rememberAsset(key, bytes)
    bytes
}
