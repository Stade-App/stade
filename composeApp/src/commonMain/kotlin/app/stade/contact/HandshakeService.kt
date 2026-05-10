package app.stade.contact

import app.stade.crypto.CryptoApi
import app.stade.crypto.Encoding
import app.stade.crypto.KemResult
import app.stade.crypto.PqCrypto
import app.stade.identity.LocalIdentity
import app.stade.identity.StadeId

/**
 * Davet (invite) — kullanıcıya `STADE2-…` opak bir Crockford Base32 dizisi olarak
 * gösterilir. İçeride binary bir paket vardır; tor:// / lan:// gibi transport
 * URI'leri kesinlikle UI'da görünmez, sadece payload'ta gezer.
 *
 * Wire format (binary, big-endian):
 *
 *   offset  size  field
 *   ------  ----  ----------------------------------------------------------
 *   0       4     magic = 'S' 'T' 'D' '2'
 *   4       1     version = 0x02
 *   5       1     flags     (rezerve, şu an 0)
 *   6       2     nicknameLen (UTF-8)
 *   8       N     nickname (UTF-8)
 *   8+N     32    Ed25519 public
 *   40+N    32    X25519  public
 *   72+N    1184  ML-KEM-768 public
 *   1256+N  1952  ML-DSA-65  public
 *   3208+N  2     addressBlobLen
 *   3210+N  M     addressBlob = utf8("addr1\naddr2\n…")
 *   3210+N+M 64   Ed25519 imza( TBS )
 *   3274+N+M 3309 ML-DSA-65 imza( TBS )
 *
 *   TBS = magic || version || flags || nicknameLen || nickname ||
 *         Ed25519pub || X25519pub || MLKEMpub || MLDSApub ||
 *         addressBlobLen || addressBlob
 *
 * Stade ID byte stream'inde **saklanmaz**; alıcı public key'lerden yeniden
 * türetir (StadeId.derive).
 */
data class InvitePayload(
    val stadeId: String,
    val nickname: String,
    val signingPublicKey: ByteArray,
    val handshakePublicKey: ByteArray,
    val mlkemPublicKey: ByteArray,
    val mldsaPublicKey: ByteArray,
    val addresses: List<String> = emptyList()
)

data class InviteCode(
    /** Kullanıcıya gösterilecek opak string (`STADE2-…`). */
    val display: String,
    /** İç temsil — QR kütüphanesi de bunu alır. */
    val raw: ByteArray
) {
    override fun equals(other: Any?): Boolean =
        other is InviteCode && display == other.display && raw.contentEquals(other.raw)
    override fun hashCode(): Int = display.hashCode() * 31 + raw.contentHashCode()
}

class HandshakeService(
    private val crypto: CryptoApi,
    private val pq: PqCrypto
) {

    private val magic = byteArrayOf(0x53, 0x54, 0x44, 0x32) // "STD2"
    private val version: Byte = 0x02

    // ── Davet üretimi ─────────────────────────────────────────────────────────

    fun createInvite(owner: LocalIdentity, addresses: List<String> = emptyList()): InviteCode {
        val cleanAddrs = addresses.filter { it.isNotBlank() }.distinct()
        val tbs = composeTbs(
            nickname = owner.nickname,
            edPub = owner.publicSigningKey,
            xPub = owner.publicHandshakeKey,
            kemPub = owner.publicMlKemKey,
            dsaPub = owner.publicMlDsaKey,
            addresses = cleanAddrs
        )
        val edSig = crypto.sign(owner.privateSigningKey, tbs)
        val dsaSig = pq.signMlDsa(owner.privateMlDsaKey, owner.publicMlDsaKey, tbs)
        val raw = tbs + edSig + dsaSig
        val b32 = Encoding.toBase32(raw)
        return InviteCode(display = "STADE2-$b32", raw = raw)
    }

    // ── QR chunk kodlama ──────────────────────────────────────────────────────

    /**
     * Davet kodunu QR kodu kapasitesine sığan parçalara böler.
     * Her parça "STDP/<idx>/<toplam>/<veri>" formatındadır.
     * Alıcı taraf parçaları toplayıp birleştirerek orijinal STADE2-… kodunu elde eder.
     *
     * QR alfanümerik mod (Level L) max kapasitesi ≈ 4296 karakter.
     * Güvenli sınır olarak 4100 kullanılıyor.
     */
    fun createQrChunks(invite: InviteCode): List<String> {
        val b32 = invite.display.removePrefix("STADE2-")
        val chunkSize = 4100
        val total = (b32.length + chunkSize - 1) / chunkSize
        return (0 until total).map { i ->
            val from = i * chunkSize
            val to = minOf(from + chunkSize, b32.length)
            "STDP/${i + 1}/$total/${b32.substring(from, to)}"
        }
    }

    // ── Davet ayrıştırma + doğrulama ─────────────────────────────────────────

    fun parseInvite(code: String): InvitePayload? = runCatching {
        // Tüm boşluk ve alfanümerik-olmayan karakterleri sil (em-dash, sıfır genişlik boşluk,
        // akıllı tire, satır sonu, vb. transfer bozulmalarına karşı savunma).
        val stripped = code.replace(Regex("[^A-Za-z0-9]"), "").uppercase()
        val b32 = when {
            stripped.startsWith("STADE2") -> stripped.substring(6)
            else -> return@runCatching null
        }
        // Geçersiz base32 karakterler kaldıysa temizle
        val cleanB32 = b32.filter { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567" }
        val raw = Encoding.fromBase32(cleanB32)
        return@runCatching parseRaw(raw)
    }.getOrNull()

    private fun parseRaw(raw: ByteArray): InvitePayload? {
        // En az: magic(4)+ver(1)+flags(1)+nickLen(2)+0+ed(32)+x(32)+kem(1184)+dsa(1952)+addrLen(2)+0+edsig(64)+dsasig(3309)
        if (raw.size < 6 + 2 + 32 + 32 + 1184 + 1952 + 2 + 64 + 3309) return null
        var off = 0
        for (i in 0 until 4) if (raw[off + i] != magic[i]) return null
        off += 4
        if (raw[off] != version) return null
        off += 1
        // flags
        off += 1
        val nickLen = readU16(raw, off); off += 2
        if (nickLen < 0 || nickLen > 1024 || off + nickLen > raw.size) return null
        val nickname = raw.copyOfRange(off, off + nickLen).decodeToString()
        off += nickLen
        if (off + 32 + 32 + 1184 + 1952 + 2 > raw.size) return null
        val edPub = raw.copyOfRange(off, off + 32); off += 32
        val xPub = raw.copyOfRange(off, off + 32); off += 32
        val kemPub = raw.copyOfRange(off, off + 1184); off += 1184
        val dsaPub = raw.copyOfRange(off, off + 1952); off += 1952
        val addrLen = readU16(raw, off); off += 2
        if (addrLen < 0 || addrLen > 64 * 1024 || off + addrLen > raw.size) return null
        val addrBlob = raw.copyOfRange(off, off + addrLen)
        off += addrLen
        if (off + 64 + 3309 > raw.size) return null
        val edSig = raw.copyOfRange(off, off + 64); off += 64
        val dsaSig = raw.copyOfRange(off, off + 3309); off += 3309

        val tbs = raw.copyOfRange(0, raw.size - 64 - 3309)
        // Hibrit doğrulama: İKİ İMZA DA geçmek zorunda.
        if (!crypto.verify(edPub, tbs, edSig)) return null
        if (!pq.verifyMlDsa(dsaPub, tbs, dsaSig)) return null

        val addrs = if (addrLen == 0) emptyList()
        else addrBlob.decodeToString().split('\n').filter { it.isNotBlank() }
        val stadeId = StadeId.derive(edPub, dsaPub, crypto::hash)
        return InvitePayload(
            stadeId = stadeId,
            nickname = nickname,
            signingPublicKey = edPub,
            handshakePublicKey = xPub,
            mlkemPublicKey = kemPub,
            mldsaPublicKey = dsaPub,
            addresses = addrs
        )
    }

    // ── PQXDH hibrit anahtar türetimi ─────────────────────────────────────────

    /**
     * Yeni kişi tanışmasında kullanılan hibrit kök anahtar türetimi.
     *
     * - x25519 = X25519(ownerPriv, peerPub)
     * - kemSs  = ML-KEM-768 ortak gizli (Alice encapsulate eder, Bob decapsulate)
     * - rootKey = HKDF(secret = x25519 || kemSs,
     *                  salt   = Blake2b(transcript),
     *                  info   = "stade-pqxdh-root-v2",
     *                  L      = 32)
     *
     * Transcript downgrade attack koruması için içerir:
     *   "stade-pqxdh-v2" || min(ownerStadeId, peerStadeId) || max(...) ||
     *   loEdPub || hiEdPub || loXPub || hiXPub ||
     *   loKemPub || hiKemPub || kemCiphertext
     *   (lo/hi = stadeId leksikografik sırasına göre — her iki tarafta kanonik)
     */
    fun deriveRootKey(
        owner: LocalIdentity,
        peer: InvitePayload,
        kemCiphertext: ByteArray,
        kemSharedSecret: ByteArray
    ): ByteArray {
        val dh = crypto.keyAgreement(owner.privateHandshakeKey, peer.handshakePublicKey)
        val ownerId = owner.stadeId.encodeToByteArray()
        val peerId = peer.stadeId.encodeToByteArray()
        // Transkript her iki tarafta da aynı olsun: her şeyi stadeId sıralamasına göre kanonik hale getir.
        val ownerIsLo = compareLex(ownerId, peerId) <= 0
        val (lo, hi) = if (ownerIsLo) ownerId to peerId else peerId to ownerId
        val (loSigningPub, hiSigningPub) =
            if (ownerIsLo) owner.publicSigningKey to peer.signingPublicKey
            else peer.signingPublicKey to owner.publicSigningKey
        val (loHandshakePub, hiHandshakePub) =
            if (ownerIsLo) owner.publicHandshakeKey to peer.handshakePublicKey
            else peer.handshakePublicKey to owner.publicHandshakeKey
        val (loMlKemPub, hiMlKemPub) =
            if (ownerIsLo) owner.publicMlKemKey to peer.mlkemPublicKey
            else peer.mlkemPublicKey to owner.publicMlKemKey
        val transcript = "stade-pqxdh-v2".encodeToByteArray() +
            lo + hi +
            loSigningPub + hiSigningPub +
            loHandshakePub + hiHandshakePub +
            loMlKemPub + hiMlKemPub +
            kemCiphertext
        val salt = crypto.hash(transcript)
        val secret = dh + kemSharedSecret
        return crypto.hkdf(secret, salt, "stade-pqxdh-root-v2".encodeToByteArray(), 32)
    }

    /** Bob'un peer'a gönderdiği KEM ciphertext'i + Bob tarafındaki ortak gizli. */
    fun encapsulateForPeer(peer: InvitePayload): KemResult =
        pq.mlkemEncapsulate(peer.mlkemPublicKey)

    /** Alice tarafı: gelen ct'den ortak gizliyi çıkarır. */
    fun decapsulate(owner: LocalIdentity, ciphertext: ByteArray): ByteArray =
        pq.mlkemDecapsulate(owner.privateMlKemKey, ciphertext)

    /**
     * "Alice" kim? Stade ID'leri leksikografik karşılaştırarak deterministik karar.
     * Alice rolü ML-KEM şifreyi göndermekten sorumlu olabilir; ancak SyncEngine
     * KEM_OFFER yönünü kendi belirler (her iki tarafta tutarlı olduğu sürece).
     */
    fun isAlice(owner: LocalIdentity, peer: InvitePayload): Boolean =
        compareLex(owner.stadeId.encodeToByteArray(), peer.stadeId.encodeToByteArray()) < 0

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun composeTbs(
        nickname: String,
        edPub: ByteArray,
        xPub: ByteArray,
        kemPub: ByteArray,
        dsaPub: ByteArray,
        addresses: List<String>
    ): ByteArray {
        val nick = nickname.encodeToByteArray()
        require(nick.size <= 1024) { "nickname çok uzun" }
        require(edPub.size == 32) { "Ed25519 pub yanlış boyutta" }
        require(xPub.size == 32) { "X25519 pub yanlış boyutta" }
        require(kemPub.size == 1184) { "ML-KEM pub yanlış boyutta: ${kemPub.size}" }
        require(dsaPub.size == 1952) { "ML-DSA pub yanlış boyutta: ${dsaPub.size}" }
        val addrBytes = addresses.joinToString("\n").encodeToByteArray()
        require(addrBytes.size <= 64 * 1024) { "addresses çok uzun" }

        val total = 4 + 1 + 1 + 2 + nick.size + 32 + 32 + 1184 + 1952 + 2 + addrBytes.size
        val out = ByteArray(total)
        var o = 0
        magic.copyInto(out, o); o += 4
        out[o++] = version
        out[o++] = 0
        writeU16(out, o, nick.size); o += 2
        nick.copyInto(out, o); o += nick.size
        edPub.copyInto(out, o); o += 32
        xPub.copyInto(out, o); o += 32
        kemPub.copyInto(out, o); o += 1184
        dsaPub.copyInto(out, o); o += 1952
        writeU16(out, o, addrBytes.size); o += 2
        addrBytes.copyInto(out, o); o += addrBytes.size
        check(o == total) { "TBS uzunluk uyumsuz: $o != $total" }
        return out
    }

    private fun writeU16(buf: ByteArray, off: Int, v: Int) {
        buf[off] = ((v ushr 8) and 0xff).toByte()
        buf[off + 1] = (v and 0xff).toByte()
    }
    private fun readU16(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xff) shl 8) or (buf[off + 1].toInt() and 0xff)

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
