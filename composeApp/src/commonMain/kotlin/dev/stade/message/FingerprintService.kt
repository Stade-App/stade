package dev.stade.message

import dev.stade.crypto.CryptoApi
import dev.stade.crypto.Encoding

class FingerprintService(private val crypto: CryptoApi) {
    fun fingerprint(publicKey: ByteArray): String {
        val h = crypto.hash(publicKey)
        return Encoding.toHex(h.copyOfRange(0, 16)).chunked(4).joinToString(" ")
    }

    fun safetyNumber(a: ByteArray, b: ByteArray): String {
        val joined = if (compareLex(a, b) < 0) a + b else b + a
        val digest = crypto.hash(joined)
        val numeric = StringBuilder()
        for (i in 0 until 12) {
            val chunk = digest.copyOfRange(i * 2, i * 2 + 2)
            val v = ((chunk[0].toInt() and 0xff) shl 8) or (chunk[1].toInt() and 0xff)
            numeric.append((v % 100000).toString().padStart(5, '0'))
            if (i % 4 == 3 && i != 11) numeric.append(' ')
        }
        return numeric.toString()
    }

    private fun compareLex(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a[i].toInt() and 0xff
            val y = b[i].toInt() and 0xff
            if (x != y) return x - y
        }
        return a.size - b.size
    }
}
