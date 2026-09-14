package dev.stade.pad

import dev.stade.AppContainer
import kotlinx.serialization.Serializable

object PadConfig {
    const val CATALOG_URL = "https://stade.dev/pad/catalog.json"
    const val CATALOG_CACHE_KEY = "pad.catalog.v1"
    const val CATALOG_CACHE_AT_KEY = "pad.catalog.v1.at"
    const val CATALOG_TTL_MS = 6L * 60 * 60 * 1000
    const val MAX_CATALOG_BYTES = 256 * 1024
    const val MAX_SOUND_BYTES = 4 * 1024 * 1024
    const val MAX_MEME_BYTES = 1_500 * 1024
    const val MAX_THUMB_BYTES = 128 * 1024
    const val FETCH_TIMEOUT_MS = 20_000L
    const val ASSET_TIMEOUT_MS = 90_000L
}

@Serializable
data class PadAsset(
    val id: String = "",
    val name: String = "",
    val emoji: String = "",
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val sha256: String = "",
    val url: String = "",
    val thumbUrl: String = "",
    val thumbSha256: String = ""
) {
    val hasIntegrity: Boolean
        get() = id.isNotBlank() && name.isNotBlank() &&
            sha256.length == 64 && url.isNotBlank()
}

@Serializable
data class PadCatalog(
    val version: Int = 1,
    val sounds: List<PadAsset> = emptyList(),
    val memes: List<PadAsset> = emptyList()
) {
    val isEmpty: Boolean get() = sounds.isEmpty() && memes.isEmpty()
}

sealed interface PadLoadState {
    data object TorNotReady : PadLoadState
    data object Loading : PadLoadState
    data class Ready(val catalog: PadCatalog) : PadLoadState
    data object Unavailable : PadLoadState
}

expect fun padNetworkReady(container: AppContainer): Boolean

expect suspend fun fetchPadCatalog(container: AppContainer, force: Boolean = false): PadCatalog?

expect suspend fun fetchPadAsset(
    container: AppContainer,
    url: String,
    expectedSha256: String,
    maxBytes: Int
): ByteArray?
