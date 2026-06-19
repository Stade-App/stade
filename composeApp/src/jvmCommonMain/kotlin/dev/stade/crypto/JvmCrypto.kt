package dev.stade.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

private object Bc {
    val random = SecureRandom()
}

private class JvmCrypto : CryptoApi {

    override fun randomBytes(size: Int): ByteArray =
        ByteArray(size).also { Bc.random.nextBytes(it) }

    override fun generateSigningKeyPair(): KeyPair {
        val priv = Ed25519PrivateKeyParameters(Bc.random)
        val pub = priv.generatePublicKey()
        return KeyPair(pub.encoded, priv.encoded)
    }

    override fun generateAgreementKeyPair(): KeyPair {
        val priv = X25519PrivateKeyParameters(Bc.random)
        val pub = priv.generatePublicKey()
        return KeyPair(pub.encoded, priv.encoded)
    }

    override fun sign(privateKey: ByteArray, data: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateKey, 0))
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    override fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
        val signer = Ed25519Signer()
        signer.init(false, Ed25519PublicKeyParameters(publicKey, 0))
        signer.update(data, 0, data.size)
        return signer.verifySignature(signature)
    }

    override fun keyAgreement(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(privateKey, 0))
        val out = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(peerPublicKey, 0), out, 0)
        return out
    }

    override fun hash(data: ByteArray): ByteArray {
        val digest = Blake2bDigest(256)
        digest.update(data, 0, data.size)
        val out = ByteArray(digest.digestSize)
        digest.doFinal(out, 0)
        return out
    }

    override fun hkdf(secret: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val gen = HKDFBytesGenerator(Blake2bDigest(256))
        gen.init(HKDFParameters(secret, salt, info))
        val out = ByteArray(length)
        gen.generateBytes(out, 0, length)
        return out
    }

    override fun aeadSeal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = ChaCha20Poly1305()
        cipher.init(true, AEADParameters(KeyParameter(key), 128, nonce, associatedData))
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        val len = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        cipher.doFinal(out, len)
        return out
    }

    override fun aeadOpen(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, associatedData: ByteArray): ByteArray? {
        return runCatching {
            val cipher = ChaCha20Poly1305()
            cipher.init(false, AEADParameters(KeyParameter(key), 128, nonce, associatedData))
            val out = ByteArray(cipher.getOutputSize(ciphertext.size))
            val len = cipher.processBytes(ciphertext, 0, ciphertext.size, out, 0)
            val finalLen = cipher.doFinal(out, len)
            out.copyOfRange(0, len + finalLen)
        }.getOrNull()
    }
}

actual fun platformCrypto(): CryptoApi = JvmCrypto()
