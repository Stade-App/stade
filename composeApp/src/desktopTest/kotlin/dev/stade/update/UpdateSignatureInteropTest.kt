package dev.stade.update

import dev.stade.crypto.Encoding
import dev.stade.crypto.platformCrypto
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val RELEASE_077fe2f7c3e96b8888e4fdd28e870eabfd383f20d4076990a4e73c1321e17199LIC_KEY = "077fe2f7c3e96b8888e4fdd28e870eabfd383f20d4076990a4e73c1321e17199"
private const val RELEASE_8ebefffc1c2d7bc25ac5b5031fb4b7058ad7bbbbdeadd4dafb1e01491683713a = "8ebefffc1c2d7bc25ac5b5031fb4b7058ad7bbbbdeadd4dafb1e01491683713a"
private const val RELEASE_6594636b75473b349b38b5d9d9d835f7f85b895938bffb11f8a51b68d3076d75ec7d9f4056f7830b66f546bc13f5cf9f245ade0ad3a975560beaefb953d44f0eNATURE = "6594636b75473b349b38b5d9d9d835f7f85b895938bffb11f8a51b68d3076d75ec7d9f4056f7830b66f546bc13f5cf9f245ade0ad3a975560beaefb953d44f0e"

class UpdateSignatureInteropTest {

    private val crypto = platformCrypto()

    @Test
    fun theReleaseSignerProducesSignaturesTheAppAccepts() {
        assertTrue(
            crypto.verify(
                Encoding.fromHex(RELEASE_077fe2f7c3e96b8888e4fdd28e870eabfd383f20d4076990a4e73c1321e17199LIC_KEY),
                Encoding.fromHex(RELEASE_8ebefffc1c2d7bc25ac5b5031fb4b7058ad7bbbbdeadd4dafb1e01491683713a),
                Encoding.fromHex(RELEASE_6594636b75473b349b38b5d9d9d835f7f85b895938bffb11f8a51b68d3076d75ec7d9f4056f7830b66f546bc13f5cf9f245ade0ad3a975560beaefb953d44f0eNATURE)
            ),
            "a signature from .github/scripts/UpdateSigner.java must verify inside the app"
        )
    }

    @Test
    fun aTamperedDigestIsRejected() {
        val digest = Encoding.fromHex(RELEASE_8ebefffc1c2d7bc25ac5b5031fb4b7058ad7bbbbdeadd4dafb1e01491683713a)
        digest[0] = (digest[0] + 1).toByte()
        assertFalse(
            crypto.verify(
                Encoding.fromHex(RELEASE_077fe2f7c3e96b8888e4fdd28e870eabfd383f20d4076990a4e73c1321e17199LIC_KEY),
                digest,
                Encoding.fromHex(RELEASE_6594636b75473b349b38b5d9d9d835f7f85b895938bffb11f8a51b68d3076d75ec7d9f4056f7830b66f546bc13f5cf9f245ade0ad3a975560beaefb953d44f0eNATURE)
            )
        )
    }

    @Test
    fun anotherSigningKeyIsRejected() {
        val other = crypto.generateSigningKeyPair().publicKey
        assertFalse(
            crypto.verify(other, Encoding.fromHex(RELEASE_8ebefffc1c2d7bc25ac5b5031fb4b7058ad7bbbbdeadd4dafb1e01491683713a), Encoding.fromHex(RELEASE_6594636b75473b349b38b5d9d9d835f7f85b895938bffb11f8a51b68d3076d75ec7d9f4056f7830b66f546bc13f5cf9f245ade0ad3a975560beaefb953d44f0eNATURE))
        )
    }

    @Test
    fun ed25519MatchesTheRfc8032Vector() {
        val publicKey = Encoding.fromHex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        val signature = Encoding.fromHex(
            "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e0652249015" +
                "55fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"
        )
        assertTrue(crypto.verify(publicKey, ByteArray(0), signature), "RFC 8032 vector 1 must verify")
    }
}
