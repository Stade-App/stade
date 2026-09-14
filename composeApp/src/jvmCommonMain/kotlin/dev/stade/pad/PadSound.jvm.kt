package dev.stade.pad

import dev.stade.audio.AudioFormat
import dev.stade.audio.OpusCodec
import kotlin.math.roundToInt

private const val WAVE_PCM = 1
private const val WAVE_FLOAT = 3
private const val WAVE_EXTENSIBLE = 0xFFFE

actual fun preparePadSound(raw: ByteArray): PreparedSound? {
    if (raw.isEmpty()) return null
    if (looksLikeRiff(raw)) return fromWav(raw)
    nativeFramedDurationMs(raw)?.let { return PreparedSound(raw, it) }
    return null
}

private fun looksLikeRiff(b: ByteArray): Boolean =
    b.size >= 12 && tag(b, 0) == "RIFF" && tag(b, 8) == "WAVE"

private fun fromWav(raw: ByteArray): PreparedSound? {
    val pcm = decodeWavToMono(raw) ?: return null
    if (pcm.isEmpty()) return null
    val durationMs = pcm.size * 1000L / AudioFormat.SAMPLE_RATE
    if (durationMs > MAX_SOUND_DURATION_MS) return null
    val encoded = runCatching { OpusCodec.encode(pcm) }.getOrNull() ?: return null
    return PreparedSound(encoded, durationMs)
}

private fun nativeFramedDurationMs(b: ByteArray): Long? {
    var scan = 0
    var packets = 0
    while (scan + 2 <= b.size) {
        val len = ((b[scan].toInt() and 0xFF) shl 8) or (b[scan + 1].toInt() and 0xFF)
        if (len <= 0) return null
        scan += 2 + len
        if (scan > b.size) return null
        packets++
    }
    if (scan != b.size || packets == 0) return null
    return packets.toLong() * AudioFormat.FRAME_MS
}

private fun decodeWavToMono(b: ByteArray): ShortArray? {
    var off = 12
    var format = 0
    var channels = 0
    var rate = 0
    var bits = 0
    var dataOff = -1
    var dataLen = 0
    while (off + 8 <= b.size) {
        val id = tag(b, off)
        val declared = le32(b, off + 4)
        val body = off + 8
        val size = if (declared < 0 || body + declared > b.size) b.size - body else declared
        when (id) {
            "fmt " -> if (size >= 16) {
                format = le16(b, body)
                channels = le16(b, body + 2)
                rate = le32(b, body + 4)
                bits = le16(b, body + 14)
                if (format == WAVE_EXTENSIBLE && size >= 26) format = le16(b, body + 24)
            }
            "data" -> {
                dataOff = body
                dataLen = size
            }
        }
        if (declared < 0 || body + declared > b.size) break
        off = body + declared + (declared and 1)
    }
    if (dataOff < 0 || dataLen <= 0) return null
    if (channels !in 1..8 || rate !in 4000..192_000) return null
    if (format != WAVE_PCM && format != WAVE_FLOAT) return null

    val mono = downmix(b, dataOff, dataLen, channels, bits, format) ?: return null
    if (mono.isEmpty()) return null
    return resampleTo(mono, rate, AudioFormat.SAMPLE_RATE)
}

private fun downmix(
    b: ByteArray,
    dataOff: Int,
    dataLen: Int,
    channels: Int,
    bits: Int,
    format: Int
): ShortArray? {
    val bytesPerSample = when {
        format == WAVE_FLOAT && bits == 32 -> 4
        format == WAVE_PCM && (bits == 8 || bits == 16 || bits == 24 || bits == 32) -> bits / 8
        else -> return null
    }
    val frameBytes = bytesPerSample * channels
    if (frameBytes <= 0) return null
    val frames = dataLen / frameBytes
    if (frames <= 0) return null
    val out = ShortArray(frames)
    var pos = dataOff
    for (f in 0 until frames) {
        var sum = 0
        for (c in 0 until channels) {
            sum += sampleAt(b, pos + c * bytesPerSample, bytesPerSample, format)
        }
        out[f] = (sum / channels).coerceIn(-32768, 32767).toShort()
        pos += frameBytes
    }
    return out
}

private fun sampleAt(b: ByteArray, at: Int, bytesPerSample: Int, format: Int): Int {
    if (format == WAVE_FLOAT) {
        val bits = le32(b, at)
        val v = Float.fromBits(bits)
        if (v.isNaN()) return 0
        return (v.coerceIn(-1f, 1f) * 32767f).roundToInt()
    }
    return when (bytesPerSample) {
        1 -> ((b[at].toInt() and 0xFF) - 128) shl 8
        2 -> le16Signed(b, at)
        3 -> {
            val v = (b[at].toInt() and 0xFF) or
                ((b[at + 1].toInt() and 0xFF) shl 8) or
                (b[at + 2].toInt() shl 16)
            v shr 8
        }
        else -> le32(b, at) shr 16
    }
}

private fun resampleTo(src: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
    if (srcRate == dstRate) return src
    val filtered = if (srcRate > dstRate) boxFilter(src, srcRate / dstRate) else src
    val outLen = ((filtered.size.toLong() * dstRate) / srcRate).toInt()
    if (outLen <= 0) return ShortArray(0)
    val out = ShortArray(outLen)
    val step = srcRate.toDouble() / dstRate
    var pos = 0.0
    val last = filtered.size - 1
    for (i in 0 until outLen) {
        val idx = pos.toInt()
        val lo = filtered[idx.coerceAtMost(last)].toInt()
        val hi = filtered[(idx + 1).coerceAtMost(last)].toInt()
        val frac = pos - idx
        out[i] = (lo + (hi - lo) * frac).roundToInt().coerceIn(-32768, 32767).toShort()
        pos += step
    }
    return out
}

private fun boxFilter(src: ShortArray, width: Int): ShortArray {
    if (width < 2) return src
    val out = ShortArray(src.size)
    var running = 0
    for (i in src.indices) {
        running += src[i].toInt()
        if (i >= width) running -= src[i - width].toInt()
        val span = if (i + 1 < width) i + 1 else width
        out[i] = (running / span).coerceIn(-32768, 32767).toShort()
    }
    return out
}

private fun tag(b: ByteArray, at: Int): String {
    if (at + 4 > b.size) return ""
    val chars = CharArray(4) { (b[at + it].toInt() and 0xFF).toChar() }
    return String(chars)
}

private fun le16(b: ByteArray, at: Int): Int {
    if (at + 2 > b.size) return 0
    return (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
}

private fun le16Signed(b: ByteArray, at: Int): Int = le16(b, at).toShort().toInt()

private fun le32(b: ByteArray, at: Int): Int {
    if (at + 4 > b.size) return -1
    return (b[at].toInt() and 0xFF) or
        ((b[at + 1].toInt() and 0xFF) shl 8) or
        ((b[at + 2].toInt() and 0xFF) shl 16) or
        ((b[at + 3].toInt() and 0xFF) shl 24)
}
