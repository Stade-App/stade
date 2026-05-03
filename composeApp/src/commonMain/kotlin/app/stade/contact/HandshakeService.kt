package app.stade.contact

import app.stade.crypto.CryptoApi
import app.stade.crypto.Encoding
import app.stade.identity.LocalIdentity

data class InvitePayload(
    val ownerId: String,
    val nickname: String,
    val signingPublicKey: ByteArray,
    val handshakePublicKey: ByteArray,
    val addresses: List<String> = emptyList()
)

class HandshakeService(private val crypto: CryptoApi) {

    fun createInvite(owner: LocalIdentity, addresses: List<String> = emptyList()): String {
        val sigPub = Encoding.toBase32(owner.publicSigningKey)
        val hsPub = Encoding.toBase32(owner.publicHandshakeKey)
        val nick = urlEncode(owner.nickname)
        val addrParams = addresses.filter { it.isNotBlank() }
            .joinToString("") { "&a=${urlEncode(it)}" }
        return "stade://contact?o=${owner.id}&n=$nick&s=$sigPub&h=$hsPub$addrParams"
    }

    fun parseInvite(link: String): InvitePayload? {
        if (!link.startsWith("stade://contact?")) return null
        return runCatching {
            val pairs = link.substringAfter('?').split('&').mapNotNull {
                val parts = it.split('=', limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            val params = pairs.toMap()
            val o = params["o"] ?: return null
            val n = urlDecode(params["n"] ?: return null)
            val s = params["s"] ?: return null
            val h = params["h"] ?: return null
            val addrs = pairs.filter { it.first == "a" }.map { urlDecode(it.second) }
            InvitePayload(
                o, n,
                Encoding.fromBase32(s),
                Encoding.fromBase32(h),
                addrs
            )
        }.getOrNull()
    }

    private fun urlEncode(s: String): String = buildString {
        for (c in s) {
            when {
                c.isLetterOrDigit() || c in "-._~" -> append(c)
                else -> {
                    val bytes = c.toString().encodeToByteArray()
                    for (b in bytes) append('%').append(Encoding.toHex(byteArrayOf(b)).uppercase())
                }
            }
        }
    }

    private fun urlDecode(s: String): String {
        val bytes = ArrayList<Byte>()
        var i = 0
        while (i < s.length) {
            if (s[i] == '%' && i + 2 < s.length) {
                val hi = hexDigit(s[i + 1])
                val lo = hexDigit(s[i + 2])
                if (hi >= 0 && lo >= 0) {
                    bytes.add(((hi shl 4) or lo).toByte())
                    i += 3
                    continue
                }
            }
            s[i].toString().encodeToByteArray().forEach { bytes.add(it) }
            i++
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun hexDigit(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> 10 + (c - 'a')
        in 'A'..'F' -> 10 + (c - 'A')
        else -> -1
    }

    fun deriveRootKey(owner: LocalIdentity, invite: InvitePayload): ByteArray {
        val shared = crypto.keyAgreement(owner.privateHandshakeKey, invite.handshakePublicKey)
        val ownerKeys = owner.publicSigningKey + owner.publicHandshakeKey
        val peerKeys = invite.signingPublicKey + invite.handshakePublicKey
        val salt = if (compareLex(ownerKeys, peerKeys) < 0) {
            crypto.hash(ownerKeys + peerKeys)
        } else {
            crypto.hash(peerKeys + ownerKeys)
        }
        return crypto.hkdf(shared, salt, "stade-root".encodeToByteArray(), 32)
    }

    fun isAlice(owner: LocalIdentity, invite: InvitePayload): Boolean {
        val ownerKeys = owner.publicSigningKey + owner.publicHandshakeKey
        val peerKeys = invite.signingPublicKey + invite.handshakePublicKey
        return compareLex(ownerKeys, peerKeys) < 0
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
