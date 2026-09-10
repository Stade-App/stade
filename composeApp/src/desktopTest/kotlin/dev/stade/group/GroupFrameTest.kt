package dev.stade.group

import dev.stade.crypto.CryptoApi
import dev.stade.crypto.PqCrypto
import dev.stade.crypto.platformCrypto
import dev.stade.crypto.platformPq
import dev.stade.identity.LocalIdentity
import dev.stade.identity.StadeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val crypto: CryptoApi = platformCrypto()
private val pq: PqCrypto = platformPq()

private class Signer(val identity: LocalIdentity) {
    fun sign(
        groupId: String,
        messageId: String,
        timestamp: Long,
        needsRelayTo: List<String>,
        payload: String
    ): String {
        val material = groupSigningMaterial(
            groupId, identity.stadeId, messageId, timestamp, needsRelayTo, payload
        )
        val ed = crypto.sign(identity.privateSigningKey, material)
        val dsa = if (needsRelayTo.isEmpty()) null
            else pq.signMlDsa(identity.privateMlDsaKey, identity.publicMlDsaKey, material)
        return encode(
            GroupFrame(
                groupId, identity.stadeId, messageId, timestamp, needsRelayTo, ed, dsa, payload
            )
        )
    }
}

private fun newSigner(): Signer {
    val signing = crypto.generateSigningKeyPair()
    val agreement = crypto.generateAgreementKeyPair()
    val kem = pq.generateMlKemKeyPair()
    val dsa = pq.generateMlDsaKeyPair()
    return Signer(
        LocalIdentity(
            id = StadeId.derive(signing.publicKey, dsa.publicKey, crypto::hash),
            nickname = "peer",
            publicSigningKey = signing.publicKey,
            privateSigningKey = signing.privateKey,
            publicHandshakeKey = agreement.publicKey,
            privateHandshakeKey = agreement.privateKey,
            publicMlKemKey = kem.publicKey,
            privateMlKemKey = kem.privateKey,
            publicMlDsaKey = dsa.publicKey,
            privateMlDsaKey = dsa.privateKey,
            createdAt = 0L
        )
    )
}

private fun encode(frame: GroupFrame): String =
    GRP_FRAME_PREFIX + frame.groupId + ":" + frame.senderId + ":" + frame.messageId + ":" +
        frame.timestamp.toString() + ":" + frame.needsRelayTo.joinToString(GRP_NEEDS_SEP) + ":" +
        b64(frame.signature) + ":" + (frame.pqSignature?.let { b64(it) } ?: "") +
        "\n" + frame.payload

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
private fun b64(bytes: ByteArray): String = kotlin.io.encoding.Base64.Default.encode(bytes)

private fun verify(
    frame: GroupFrame,
    signer: Signer,
    requirePostQuantum: Boolean
): Boolean {
    val material = groupSigningMaterial(
        frame.groupId, frame.senderId, frame.messageId, frame.timestamp,
        frame.needsRelayTo, frame.payload
    )
    if (!crypto.verify(signer.identity.publicSigningKey, material, frame.signature)) return false
    val pqSignature = frame.pqSignature ?: return !requirePostQuantum
    return runCatching {
        pq.verifyMlDsa(signer.identity.publicMlDsaKey, material, pqSignature)
    }.getOrDefault(false)
}

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
private fun parse(raw: String): GroupFrame? {
    if (!raw.startsWith(GRP_FRAME_PREFIX)) return null
    val stripped = raw.substring(GRP_FRAME_PREFIX.length)
    val newlineIdx = stripped.indexOf('\n')
    if (newlineIdx < 0) return null
    val parts = stripped.substring(0, newlineIdx).split(':')
    if (parts.size != 7) return null
    val timestamp = parts[3].toLongOrNull() ?: return null
    val needs = parts[4].split(GRP_NEEDS_SEP).filter { it.isNotBlank() }
    val sig = runCatching { kotlin.io.encoding.Base64.Default.decode(parts[5]) }.getOrNull() ?: return null
    val pqSig = if (parts[6].isEmpty()) null
        else runCatching { kotlin.io.encoding.Base64.Default.decode(parts[6]) }.getOrNull() ?: return null
    return GroupFrame(
        parts[0], parts[1], parts[2], timestamp, needs, sig, pqSig,
        stripped.substring(newlineIdx + 1)
    )
}

class GroupFrameTest {

    @Test
    fun aRelayedFrameCarriesBothSignatures() {
        val signer = newSigner()
        val raw = signer.sign("g1", "m1", 100L, listOf("STADE-AAAA-BBBB-CCCC"), "hello")
        val frame = assertNotNull(parse(raw))
        assertNotNull(frame.pqSignature, "a frame that will be relayed must carry an ML-DSA signature")
        assertEquals(3309, frame.pqSignature!!.size)
        assertTrue(verify(frame, signer, requirePostQuantum = true))
    }

    @Test
    fun aDirectOnlyFrameSkipsThePostQuantumSignature() {
        val signer = newSigner()
        val raw = signer.sign("g1", "m1", 100L, emptyList(), "hello")
        val frame = assertNotNull(parse(raw))
        assertNull(
            frame.pqSignature,
            "a frame nobody needs to relay should not pay for an ML-DSA signature"
        )
        assertTrue(verify(frame, signer, requirePostQuantum = false))
    }

    @Test
    fun aRelayedFrameStrippedOfItsPostQuantumSignatureIsRejected() {
        val signer = newSigner()
        val raw = signer.sign("g1", "m1", 100L, listOf("STADE-AAAA-BBBB-CCCC"), "hello")
        val frame = assertNotNull(parse(raw))
        val downgraded = frame.copy(pqSignature = null)
        assertTrue(
            verify(downgraded, signer, requirePostQuantum = false),
            "the classical signature should still be intact"
        )
        assertFalse(
            verify(downgraded, signer, requirePostQuantum = true),
            "stripping the ML-DSA signature off a relayed frame must be rejected"
        )
    }

    @Test
    fun aCorruptedPostQuantumSignatureIsRejectedEvenWhenNotRequired() {
        val signer = newSigner()
        val raw = signer.sign("g1", "m1", 100L, listOf("STADE-AAAA-BBBB-CCCC"), "hello")
        val frame = assertNotNull(parse(raw))
        val broken = frame.pqSignature!!.copyOf().also { it[0] = (it[0] + 1).toByte() }
        val tampered = frame.copy(pqSignature = broken)
        assertFalse(verify(tampered, signer, requirePostQuantum = true))
        assertFalse(
            verify(tampered, signer, requirePostQuantum = false),
            "an invalid ML-DSA signature must fail closed even when it was optional"
        )
    }

    @Test
    fun tamperingWithThePayloadBreaksBothSignatures() {
        val signer = newSigner()
        val raw = signer.sign("g1", "m1", 100L, listOf("STADE-AAAA-BBBB-CCCC"), "hello")
        val frame = assertNotNull(parse(raw))
        val edited = frame.copy(payload = "goodbye")
        assertFalse(verify(edited, signer, requirePostQuantum = false))
        assertFalse(verify(edited, signer, requirePostQuantum = true))
    }

    @Test
    fun tamperingWithTheRelayListBreaksTheSignature() {
        val signer = newSigner()
        val raw = signer.sign("g1", "m1", 100L, listOf("STADE-AAAA-BBBB-CCCC"), "hello")
        val frame = assertNotNull(parse(raw))
        val edited = frame.copy(needsRelayTo = listOf("STADE-ZZZZ-ZZZZ-ZZZZ"))
        assertFalse(
            verify(edited, signer, requirePostQuantum = true),
            "the relay list is signed and must not be editable in transit"
        )
    }

    @Test
    fun anotherMembersKeyCannotValidateTheFrame() {
        val signer = newSigner()
        val impostor = newSigner()
        val raw = signer.sign("g1", "m1", 100L, listOf("STADE-AAAA-BBBB-CCCC"), "hello")
        val frame = assertNotNull(parse(raw))
        assertFalse(verify(frame, impostor, requirePostQuantum = true))
    }

    @Test
    fun payloadsContainingSeparatorsSurviveTheRoundTrip() {
        val signer = newSigner()
        val payload = "a:b\nc:dR:x"
        val raw = signer.sign("g1", "m1", 100L, listOf("STADE-AAAA-BBBB-CCCC"), payload)
        val frame = assertNotNull(parse(raw))
        assertEquals(payload, frame.payload)
        assertTrue(verify(frame, signer, requirePostQuantum = true))
    }
}
