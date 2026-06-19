package dev.stade.crypto

interface CryptoApi {
    fun randomBytes(size: Int): ByteArray
    fun generateSigningKeyPair(): KeyPair
    fun generateAgreementKeyPair(): KeyPair
    fun sign(privateKey: ByteArray, data: ByteArray): ByteArray
    fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean
    fun keyAgreement(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray
    fun hash(data: ByteArray): ByteArray
    fun hkdf(secret: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray
    fun aeadSeal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray
    fun aeadOpen(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray?
}

data class KeyPair(val publicKey: ByteArray, val privateKey: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyPair) return false
        return publicKey.contentEquals(other.publicKey) && privateKey.contentEquals(other.privateKey)
    }
    override fun hashCode(): Int = publicKey.contentHashCode() * 31 + privateKey.contentHashCode()
}

expect fun platformCrypto(): CryptoApi
