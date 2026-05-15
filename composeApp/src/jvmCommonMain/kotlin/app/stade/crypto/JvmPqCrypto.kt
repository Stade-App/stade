package app.stade.crypto


import org.bouncycastle.pqc.crypto.mldsa.MLDSAKeyGenerationParameters
import org.bouncycastle.pqc.crypto.mldsa.MLDSAKeyPairGenerator
import org.bouncycastle.pqc.crypto.mldsa.MLDSAParameters
import org.bouncycastle.pqc.crypto.mldsa.MLDSAPrivateKeyParameters
import org.bouncycastle.pqc.crypto.mldsa.MLDSAPublicKeyParameters
import org.bouncycastle.pqc.crypto.mldsa.MLDSASigner
import org.bouncycastle.pqc.crypto.mlkem.MLKEMExtractor
import org.bouncycastle.pqc.crypto.mlkem.MLKEMGenerator
import org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyGenerationParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyPairGenerator
import org.bouncycastle.pqc.crypto.mlkem.MLKEMParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPrivateKeyParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPublicKeyParameters
import java.security.SecureRandom

private object PqRng {
    val random = SecureRandom()
}

private class JvmPqCrypto : PqCrypto {

    override fun generateMlKemKeyPair(): KeyPair {
        val gen = MLKEMKeyPairGenerator()
        gen.init(MLKEMKeyGenerationParameters(PqRng.random, MLKEMParameters.ml_kem_768))
        val kp = gen.generateKeyPair()
        val pub = (kp.public as MLKEMPublicKeyParameters).encoded
        val priv = (kp.private as MLKEMPrivateKeyParameters).encoded
        return KeyPair(pub, priv)
    }

    override fun generateMlDsaKeyPair(): KeyPair {
        val gen = MLDSAKeyPairGenerator()
        gen.init(MLDSAKeyGenerationParameters(PqRng.random, MLDSAParameters.ml_dsa_65))
        val kp = gen.generateKeyPair()
        val pub = (kp.public as MLDSAPublicKeyParameters).encoded
        val priv = (kp.private as MLDSAPrivateKeyParameters).encoded
        return KeyPair(pub, priv)
    }

    override fun mlkemEncapsulate(peerPublicKey: ByteArray): KemResult {
        val pub = MLKEMPublicKeyParameters(MLKEMParameters.ml_kem_768, peerPublicKey)
        val gen = MLKEMGenerator(PqRng.random)
        val enc = gen.generateEncapsulated(pub)
        return KemResult(ciphertext = enc.encapsulation, sharedSecret = enc.secret)
    }

    override fun mlkemDecapsulate(privateKey: ByteArray, ciphertext: ByteArray): ByteArray {
        val priv = MLKEMPrivateKeyParameters(MLKEMParameters.ml_kem_768, privateKey)
        val ext = MLKEMExtractor(priv)
        return ext.extractSecret(ciphertext)
    }

    override fun signMlDsa(privateKey: ByteArray, publicKey: ByteArray, data: ByteArray): ByteArray {
        val priv = MLDSAPrivateKeyParameters(MLDSAParameters.ml_dsa_65, privateKey)
        val signer = MLDSASigner()
        signer.init(true, priv)
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    override fun verifyMlDsa(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
        return runCatching {
            val pub = MLDSAPublicKeyParameters(MLDSAParameters.ml_dsa_65, publicKey)
            val signer = MLDSASigner()
            signer.init(false, pub)
            signer.update(data, 0, data.size)
            signer.verifySignature(signature)
        }.getOrDefault(false)
    }
}

actual fun platformPq(): PqCrypto = JvmPqCrypto()
