package app.stade.crypto

object Encoding {
    private val HEX = "0123456789abcdef".toCharArray()
    private const val B32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun toHex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xff
            out[i * 2] = HEX[v ushr 4]
            out[i * 2 + 1] = HEX[v and 0x0f]
        }
        return out.concatToString()
    }

    fun fromHex(hex: String): ByteArray {
        val s = hex.trim()
        require(s.length % 2 == 0) { "invalid hex length" }
        val out = ByteArray(s.length / 2)
        for (i in out.indices) {
            val hi = hexDigit(s[i * 2])
            val lo = hexDigit(s[i * 2 + 1])
            require(hi >= 0 && lo >= 0) { "invalid hex char" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun hexDigit(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> 10 + (c - 'a')
        in 'A'..'F' -> 10 + (c - 'A')
        else -> -1
    }

    fun toBase32(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                sb.append(B32_ALPHABET[(buffer ushr bits) and 0x1f])
            }
        }
        if (bits > 0) sb.append(B32_ALPHABET[(buffer shl (5 - bits)) and 0x1f])
        return sb.toString()
    }

    fun fromBase32(s: String): ByteArray {
        val clean = s.uppercase().filter { it != '=' && !it.isWhitespace() }
        val out = ArrayList<Byte>(clean.length * 5 / 8)
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val v = B32_ALPHABET.indexOf(c)
            require(v >= 0) { "invalid base32 char" }
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer ushr bits) and 0xff).toByte())
            }
        }
        return out.toByteArray()
    }
}
