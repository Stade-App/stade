package dev.stade.identity

object StadeId {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    private const val PREFIX = "STADE-"
    private const val PAYLOAD_LEN = 12
    private const val FULL_LEN = PREFIX.length + PAYLOAD_LEN + 2

    fun derive(
        publicSigningKey: ByteArray,
        mldsaPublicKey: ByteArray? = null,
        hash: (ByteArray) -> ByteArray
    ): String {
        val prefix = "stade-id-v1\u0000".encodeToByteArray()
        val mldsa = mldsaPublicKey ?: ByteArray(0)
        val material = ByteArray(prefix.size + publicSigningKey.size + mldsa.size).also {
            prefix.copyInto(it, 0)
            publicSigningKey.copyInto(it, prefix.size)
            mldsa.copyInto(it, prefix.size + publicSigningKey.size)
        }
        val digest = hash(material)
        require(digest.size >= 9) { "hash too short: ${digest.size}" }

        var id60 = 0L
        for (i in 0 until 8) {
            id60 = (id60 shl 8) or (digest[i].toLong() and 0xff)
        }
        id60 = id60 ushr 4
        val crc4 = ((digest[8].toInt() ushr 4) and 0x0f).toLong()
        val combined = (id60 shl 4) or crc4

        val chars = CharArray(PAYLOAD_LEN)
        for (i in 0 until PAYLOAD_LEN) {
            val shift = 64 - 5 * (i + 1)
            val v = ((combined ushr shift) and 0x1fL).toInt()
            chars[i] = ALPHABET[v]
        }
        val sb = StringBuilder(FULL_LEN)
        sb.append(PREFIX)
        sb.append(chars, 0, 4); sb.append('-')
        sb.append(chars, 4, 4); sb.append('-')
        sb.append(chars, 8, 4)
        return sb.toString()
    }

    fun normalize(input: String): String {
        val cleaned = StringBuilder()
        for (c in input.trim().uppercase()) {
            when (c) {
                'O' -> cleaned.append('0')
                'I', 'L' -> cleaned.append('1')
                in '0'..'9', in 'A'..'Z' -> cleaned.append(c)
                ' ', '-', '_' -> {}
                else -> cleaned.append(c)
            }
        }
        val raw = cleaned.toString()
        val withoutPrefix = if (raw.startsWith("STADE")) raw.substring(5) else raw
        if (withoutPrefix.length < PAYLOAD_LEN) return raw
        val payload = withoutPrefix.takeLast(PAYLOAD_LEN)
        return PREFIX + payload.substring(0, 4) + "-" +
            payload.substring(4, 8) + "-" + payload.substring(8, 12)
    }

    fun isValid(id: String): Boolean {
        val n = normalize(id)
        if (n.length != FULL_LEN) return false
        if (!n.startsWith(PREFIX)) return false
        val payload = n.substring(PREFIX.length).replace("-", "")
        if (payload.length != PAYLOAD_LEN) return false
        for (c in payload) if (ALPHABET.indexOf(c) < 0) return false
        return true
    }

    fun matches(
        id: String,
        publicSigningKey: ByteArray,
        mldsaPublicKey: ByteArray?,
        hash: (ByteArray) -> ByteArray
    ): Boolean {
        if (!isValid(id)) return false
        val expected = derive(publicSigningKey, mldsaPublicKey, hash)
        return normalize(id) == expected
    }
}

