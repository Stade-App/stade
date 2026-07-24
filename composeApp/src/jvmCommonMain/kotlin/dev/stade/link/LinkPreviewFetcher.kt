package dev.stade.link

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
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.withTimeoutOrNull

private const val MAX_PREVIEW_BYTES = 512 * 1024
private const val FETCH_TIMEOUT_MS = 10_000L

actual suspend fun fetchLinkPreview(url: String, container: AppContainer): LinkPreview? {
    val tor = container.transports.get(TransportType.TOR) as? TorTransport ?: return null
    val proxyAddr = tor.socksProxyAddress() ?: return null
    val parsed = runCatching { Url(url) }.getOrNull() ?: return null
    if (parsed.protocol.name != "http" && parsed.protocol.name != "https") return null

    return withTimeoutOrNull(FETCH_TIMEOUT_MS) {
        runCatching {
            val client = HttpClient(CIO) {
                expectSuccess = false
                install(HttpTimeout) { requestTimeoutMillis = FETCH_TIMEOUT_MS }
                engine { proxy = ProxyBuilder.socks(proxyAddr.first, proxyAddr.second) }
            }
            try {
                val response: HttpResponse = client.get(url)
                val ct = response.contentType()
                if (ct == null || ct.contentType != "text" || ct.contentSubtype != "html") return@runCatching null
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(MAX_PREVIEW_BYTES)
                var total = 0
                while (total < buffer.size) {
                    val read = channel.readAvailable(buffer, total, buffer.size - total)
                    if (read == -1) break
                    total += read
                }
                parsePreview(url, buffer.decodeToString(0, total))
            } finally {
                client.close()
            }
        }.getOrNull()
    }
}

private val TITLE_TAG = Regex("""<title[^>]*>([^<]*)</title>""", RegexOption.IGNORE_CASE)
private val OG_TITLE = Regex("""<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
private val OG_DESCRIPTION = Regex("""<meta[^>]+property=["']og:description["'][^>]+content=["']([^"']*)["']""", RegexOption.IGNORE_CASE)

private fun parsePreview(url: String, html: String): LinkPreview? {
    val title = (OG_TITLE.find(html)?.groupValues?.get(1) ?: TITLE_TAG.find(html)?.groupValues?.get(1))
        ?.trim()?.take(200)
    if (title.isNullOrEmpty()) return null
    val description = OG_DESCRIPTION.find(html)?.groupValues?.get(1)?.trim()?.take(300) ?: ""
    return LinkPreview(url, title, description)
}
