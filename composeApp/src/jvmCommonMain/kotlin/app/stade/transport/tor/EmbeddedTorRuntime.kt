package app.stade.transport.tor

interface EmbeddedTorRuntime {
    suspend fun ensureReady(localPort: Int): TorReady
    suspend fun shutdown()
    val statusFlow: kotlinx.coroutines.flow.StateFlow<TorStatus>
}

data class TorReady(
    val socksHost: String,
    val socksPort: Int,
    val onionHostname: String?,
    val onionVirtualPort: Int,
    val onionLocalPort: Int
)

sealed interface TorStatus {
    data object Idle : TorStatus
    data class Bootstrapping(val percent: Int, val summary: String) : TorStatus
    data class Ready(val onion: String?) : TorStatus
    data class Failed(val reason: String) : TorStatus
}
