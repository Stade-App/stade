package dev.stade.contact

import dev.stade.crypto.CryptoApi
import dev.stade.crypto.PqCrypto
import dev.stade.crypto.platformCrypto
import dev.stade.crypto.platformPq
import dev.stade.identity.LocalIdentity
import dev.stade.identity.StadeId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val crypto: CryptoApi = platformCrypto()
private val pq: PqCrypto = platformPq()
private val handshake = HandshakeService(crypto, pq)

private fun newIdentity(nickname: String): LocalIdentity {
    val signing = crypto.generateSigningKeyPair()
    val agreement = crypto.generateAgreementKeyPair()
    val kem = pq.generateMlKemKeyPair()
    val dsa = pq.generateMlDsaKeyPair()
    return LocalIdentity(
        id = StadeId.derive(signing.publicKey, dsa.publicKey, crypto::hash),
        nickname = nickname,
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
}

private fun LocalIdentity.asInvite(): InvitePayload = InvitePayload(
    stadeId = id,
    nickname = nickname,
    signingPublicKey = publicSigningKey,
    handshakePublicKey = publicHandshakeKey,
    mlkemPublicKey = publicMlKemKey,
    mldsaPublicKey = publicMlDsaKey
)

private fun runHandshake(alice: LocalIdentity, bob: LocalIdentity): Pair<ByteArray, ByteArray> {
    val aliceEph = handshake.newEphemeral()
    val bobEph = handshake.newEphemeral()

    val enc = handshake.encapsulateForPeer(bobEph.kem.publicKey)
    val bobSs = handshake.decapsulate(bobEph, enc.ciphertext)
    assertContentEquals(enc.sharedSecret, bobSs, "ML-KEM encapsulation did not round-trip")

    val aliceRoot = handshake.deriveRootKey(
        alice, bob.asInvite(), aliceEph,
        PeerEphemeral(bobEph.dh.publicKey, bobEph.kem.publicKey),
        enc.ciphertext, enc.sharedSecret
    )
    val bobRoot = handshake.deriveRootKey(
        bob, alice.asInvite(), bobEph,
        PeerEphemeral(aliceEph.dh.publicKey, aliceEph.kem.publicKey),
        enc.ciphertext, bobSs
    )
    return aliceRoot to bobRoot
}

class HandshakeServiceTest {

    @Test
    fun bothPartiesDeriveTheSameRootKey() {
        val alice = newIdentity("alice")
        val bob = newIdentity("bob")
        val (aliceRoot, bobRoot) = runHandshake(alice, bob)
        assertContentEquals(aliceRoot, bobRoot, "the two sides derived different root keys")
        assertTrue(aliceRoot.size == 32)
    }

    @Test
    fun theSameIdentitiesProduceADifferentRootEachHandshake() {
        val alice = newIdentity("alice")
        val bob = newIdentity("bob")
        val first = runHandshake(alice, bob).first
        val second = runHandshake(alice, bob).first
        assertFalse(
            first.contentEquals(second),
            "two handshakes between the same identities produced the same root, so the " +
                "session key does not depend on ephemeral material"
        )
    }

    @Test
    fun rootDependsOnBothSidesEphemeralKeys() {
        val alice = newIdentity("alice")
        val bob = newIdentity("bob")
        val aliceEph = handshake.newEphemeral()
        val bobEph = handshake.newEphemeral()
        val impostorEph = handshake.newEphemeral()

        val enc = handshake.encapsulateForPeer(bobEph.kem.publicKey)
        val genuine = handshake.deriveRootKey(
            alice, bob.asInvite(), aliceEph,
            PeerEphemeral(bobEph.dh.publicKey, bobEph.kem.publicKey),
            enc.ciphertext, enc.sharedSecret
        )
        val swapped = handshake.deriveRootKey(
            alice, bob.asInvite(), aliceEph,
            PeerEphemeral(impostorEph.dh.publicKey, bobEph.kem.publicKey),
            enc.ciphertext, enc.sharedSecret
        )
        assertFalse(
            genuine.contentEquals(swapped),
            "substituting the peer's ephemeral key did not change the derived root"
        )
    }

    @Test
    fun rootDependsOnTheIdentitiesInvolved() {
        val alice = newIdentity("alice")
        val bob = newIdentity("bob")
        val mallory = newIdentity("mallory")

        val aliceEph = handshake.newEphemeral()
        val bobEph = handshake.newEphemeral()
        val enc = handshake.encapsulateForPeer(bobEph.kem.publicKey)
        val peer = PeerEphemeral(bobEph.dh.publicKey, bobEph.kem.publicKey)

        val withBob = handshake.deriveRootKey(
            alice, bob.asInvite(), aliceEph, peer, enc.ciphertext, enc.sharedSecret
        )
        val withMallory = handshake.deriveRootKey(
            alice, mallory.asInvite(), aliceEph, peer, enc.ciphertext, enc.sharedSecret
        )
        assertFalse(
            withBob.contentEquals(withMallory),
            "the root key is not bound to the peer identity"
        )
    }

    @Test
    fun aliceIsChosenDeterministicallyAndExactlyOnce() {
        repeat(20) {
            val one = newIdentity("one")
            val two = newIdentity("two")
            val oneThinksItIsAlice = handshake.isAlice(one, two.asInvite())
            val twoThinksItIsAlice = handshake.isAlice(two, one.asInvite())
            assertTrue(
                oneThinksItIsAlice != twoThinksItIsAlice,
                "both or neither party claimed the Alice role"
            )
        }
    }
}
