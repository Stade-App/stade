package app.stade.crypto


import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumKeyGenerationParameters
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumKeyPairGenerator
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumParameters
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumPrivateKeyParameters
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumPublicKeyParameters
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumSigner
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberKEMExtractor
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberKEMGenerator
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberKeyGenerationParameters
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberKeyPairGenerator
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberParameters
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberPrivateKeyParameters
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberPublicKeyParameters
import java.security.SecureRandom

private object PqRng {
    val random = SecureRandom()
}

private class JvmPqCrypto : PqCrypto {

    override fun generateMlKemKeyPair(): KeyPair {
        val gen = KyberKeyPairGenerator()
        gen.init(KyberKeyGenerationParameters(PqRng.random, KyberParameters.kyber768))
        val kp = gen.generateKeyPair()
        val pub = (kp.public as KyberPublicKeyParameters).encoded
        val priv = (kp.private as KyberPrivateKeyParameters).encoded
        return KeyPair(pub, priv)
    }

    override fun generateMlDsaKeyPair(): KeyPair {
        val gen = DilithiumKeyPairGenerator()
        gen.init(DilithiumKeyGenerationParameters(PqRng.random, DilithiumParameters.dilithium3))
        val kp = gen.generateKeyPair()
        val pub = (kp.public as DilithiumPublicKeyParameters).encoded
        val priv = (kp.private as DilithiumPrivateKeyParameters).encoded
        return KeyPair(pub, priv)
    }

    override fun mlkemEncapsulate(peerPublicKey: ByteArray): KemResult {
        val pub = KyberPublicKeyParameters(KyberParameters.kyber768, peerPublicKey)
        val gen = KyberKEMGenerator(PqRng.random)
        val enc = gen.generateEncapsulated(pub)
        return KemResult(ciphertext = enc.encapsulation, sharedSecret = enc.secret)
    }

    override fun mlkemDecapsulate(privateKey: ByteArray, ciphertext: ByteArray): ByteArray {
        val priv = KyberPrivateKeyParameters(KyberParameters.kyber768, privateKey)
        val ext = KyberKEMExtractor(priv)
        return ext.extractSecret(ciphertext)
    }

    override fun signMlDsa(privateKey: ByteArray, publicKey: ByteArray, data: ByteArray): ByteArray {
        val pub = DilithiumPublicKeyParameters(DilithiumParameters.dilithium3, publicKey)
        val priv = DilithiumPrivateKeyParameters(DilithiumParameters.dilithium3, privateKey, pub)
        val signer = DilithiumSigner()
        signer.init(true, priv)
        return signer.generateSignature(data)
    }

    override fun verifyMlDsa(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
        return runCatching {
            val pub = DilithiumPublicKeyParameters(DilithiumParameters.dilithium3, publicKey)
            val signer = DilithiumSigner()
            signer.init(false, pub)
            signer.verifySignature(data, signature)
        }.getOrDefault(false)
    }
}

actual fun platformPq(): PqCrypto = JvmPqCrypto()

