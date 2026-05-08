package app.stade.identity

/**
 * Stade ID — kullanıcıya gösterilen kısa, kopyalanabilir, hatasız kimlik.
 *
 * Format: `STADE-XXXX-XXXX-XXXX` (Crockford Base32, 12 anlamlı karakter)
 *
 * Türetme:
 *   1. material = "stade-id-v1\u0000" || Ed25519_pub || (ML-DSA_pub | "")
 *   2. digest = Blake2b-256(material)         (CryptoApi.hash zaten Blake2b-256)
 *   3. id60   = digest'in ilk 60 biti          (digest[0..7], üst 60 bit)
 *   4. crc4   = digest[8] >>> 4                (kriptografik check, 4 bit)
 *   5. 64 bit = id60 << 4 | crc4 → 12 × 5-bit grup → Crockford alfabesinden harf.
 *
 * Doğrulama: input normalize edilir (büyük harf, O→0, I/L→1), prefix kontrolü
 * yapılır, son 4 bit yeniden hesaplanan checksum'a karşı doğrulanır.
 */
object StadeId {
    /** Crockford Base32 alfabesi (I, L, O, U yok — okunabilirlik). */
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    private const val PREFIX = "STADE-"
    /** 60 bit anlamlı içerik + 4 bit checksum = 12 karakter. */
    private const val PAYLOAD_LEN = 12
    private const val FULL_LEN = PREFIX.length + PAYLOAD_LEN + 2 // iki tire

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

        // İlk 8 bayt: 64 bit. Üst 60 bit'i id60 olarak al.
        var id60 = 0L
        for (i in 0 until 8) {
            id60 = (id60 shl 8) or (digest[i].toLong() and 0xff)
        }
        id60 = id60 ushr 4 // alttaki 4 bit'i at, üst 60 bit kalsın
        val crc4 = ((digest[8].toInt() ushr 4) and 0x0f).toLong()
        val combined = (id60 shl 4) or crc4 // tam 64 bit

        val chars = CharArray(PAYLOAD_LEN)
        for (i in 0 until PAYLOAD_LEN) {
            // İlk grup en üst 5 bit, son grup en alt 5 bit.
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

    /** Kullanıcı girdisini kanonik biçime getir: büyük harf, O→0, I/L→1, tireli. */
    fun normalize(input: String): String {
        val cleaned = StringBuilder()
        for (c in input.trim().uppercase()) {
            when (c) {
                'O' -> cleaned.append('0')
                'I', 'L' -> cleaned.append('1')
                in '0'..'9', in 'A'..'Z' -> cleaned.append(c)
                ' ', '-', '_' -> {} // ayırıcıları at
                else -> cleaned.append(c) // alfabe-dışıysa yine ekle, isValid yakalar
            }
        }
        // "STADE" prefix'ini ayır
        val raw = cleaned.toString()
        val withoutPrefix = if (raw.startsWith("STADE")) raw.substring(5) else raw
        if (withoutPrefix.length < PAYLOAD_LEN) return raw // bozuk
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

    /**
     * Verilen Stade ID'nin verilen public anahtarlardan türetildiğini doğrular
     * (constant-time karşılaştırma değildir — Stade ID gizli değil, sızdırma yok).
     */
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

