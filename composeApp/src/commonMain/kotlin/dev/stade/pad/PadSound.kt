package dev.stade.pad

class PreparedSound(val bytes: ByteArray, val durationMs: Long) {
    override fun equals(other: Any?): Boolean =
        other is PreparedSound && other.durationMs == durationMs && other.bytes.contentEquals(bytes)

    override fun hashCode(): Int = bytes.contentHashCode() * 31 + durationMs.hashCode()
}

const val MAX_SOUND_DURATION_MS = 30_000L

expect fun preparePadSound(raw: ByteArray): PreparedSound?
