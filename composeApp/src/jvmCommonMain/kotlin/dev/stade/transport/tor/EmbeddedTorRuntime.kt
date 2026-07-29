package dev.stade.transport.tor

interface EmbeddedTorRuntime {
    suspend fun ensureReady(localPort: Int, bridges: TorBridgeConfig = TorBridgeConfig()): TorReady
    suspend fun shutdown()
    fun isAlive(): Boolean
    fun invalidate()
    val statusFlow: kotlinx.coroutines.flow.StateFlow<TorStatus>
}

data class TorReady(
    val socksHost: String,
    val socksPort: Int,
    val onionHostname: String?,
    val onionVirtualPort: Int,
    val onionLocalPort: Int,
    val onionPublished: Boolean = true
)

sealed interface TorStatus {
    data object Idle : TorStatus
    data class Bootstrapping(val percent: Int, val summary: String) : TorStatus
    data class Ready(val onion: String?, val published: Boolean = true) : TorStatus
    data class Failed(val reason: String) : TorStatus
}
