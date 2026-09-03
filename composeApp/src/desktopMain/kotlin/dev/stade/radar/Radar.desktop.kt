package dev.stade.radar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual val isRadarSupported: Boolean = false

private object UnsupportedRadarSession : RadarSession {
    override val status: RadarStatus = RadarStatus.Unsupported
    override val peers: List<RadarPeer> = emptyList()
    override val discoverable: Boolean = false
    override fun resolve() {}
    override suspend fun fetchInvite(peer: RadarPeer, onProgress: (Float) -> Unit): ByteArray? = null
}

@Composable
actual fun rememberRadarSession(
    nickname: String,
    fingerprint: String,
    paletteIndex: Int,
    active: Boolean,
    broadcasting: Boolean,
    inviteProvider: () -> ByteArray
): RadarSession = remember { UnsupportedRadarSession }
