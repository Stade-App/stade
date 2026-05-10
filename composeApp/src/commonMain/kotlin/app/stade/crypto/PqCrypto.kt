package app.stade.crypto

interface PqCrypto {
    fun generateMlKemKeyPair(): KeyPair
    fun generateMlDsaKeyPair(): KeyPair

    fun mlkemEncapsulate(peerPublicKey: ByteArray): KemResult

    fun mlkemDecapsulate(privateKey: ByteArray, ciphertext: ByteArray): ByteArray

    fun signMlDsa(privateKey: ByteArray, publicKey: ByteArray, data: ByteArray): ByteArray
    fun verifyMlDsa(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean
}

data class KemResult(val ciphertext: ByteArray, val sharedSecret: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is KemResult &&
            ciphertext.contentEquals(other.ciphertext) &&
            sharedSecret.contentEquals(other.sharedSecret)
    override fun hashCode(): Int =
        ciphertext.contentHashCode() * 31 + sharedSecret.contentHashCode()
}

expect fun platformPq(): PqCrypto

