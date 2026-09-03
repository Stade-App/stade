package dev.stade.radar

import androidx.compose.runtime.Composable
import dev.stade.crypto.Encoding

private const val RADAR_FP_BYTES = 4
private val RADAR_FP_DOMAIN = "stade-radar-fp:".encodeToByteArray()

fun radarFingerprint(hash: (ByteArray) -> ByteArray, signingPublicKey: ByteArray): String =
    Encoding.toHex(hash(RADAR_FP_DOMAIN + signingPublicKey).copyOfRange(0, RADAR_FP_BYTES))

data class RadarPeer(
    val id: String,
    val nickname: String,
    val fingerprint: String,
    val paletteIndex: Int?,
    val rssi: Int,
    val lastSeenAt: Long
)

enum class RadarStatus {
    Unsupported,
    PermissionRequired,
    BluetoothOff,
    Scanning
}

interface RadarSession {
    val status: RadarStatus
    val peers: List<RadarPeer>
    val discoverable: Boolean
    fun resolve()
    suspend fun fetchInvite(peer: RadarPeer, onProgress: (Float) -> Unit = {}): ByteArray?
}

expect val isRadarSupported: Boolean

@Composable
expect fun rememberRadarSession(
    nickname: String,
    fingerprint: String,
    paletteIndex: Int,
    active: Boolean,
    broadcasting: Boolean,
    inviteProvider: () -> ByteArray
): RadarSession
