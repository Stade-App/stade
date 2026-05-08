package app.stade.crypto

/**
 * Post-quantum kriptografi yüzeyi.
 *
 * - **ML-KEM-768** (NIST FIPS 203, eski adıyla Kyber768) — anahtar paketleme
 *   (encapsulate / decapsulate). 32-byte simetrik ortak gizli üretir.
 *     • public key  ≈ 1184 B
 *     • private key ≈ 2400 B
 *     • ciphertext  ≈ 1088 B
 *     • shared secret = 32 B
 *
 * - **ML-DSA-65** (NIST FIPS 204, eski adıyla Dilithium3) — imza.
 *     • public key  ≈ 1952 B
 *     • private key ≈ 4032 B
 *     • signature   ≈ 3309 B
 *
 * Klasik X25519 / Ed25519 ile **hibrit** kullanılır: PQXDH benzeri akışta
 * (a) X25519 DH sonucu, (b) ML-KEM ortak gizli ve (c) imza zincirinde
 * Ed25519 + ML-DSA-65 doğrulamasının **ikisinin de** geçmesi gerekir.
 * Algoritmalardan biri ileride kırılsa bile diğeri ayakta olduğu sürece
 * gizlilik / kimlik doğrulama korunur.
 */
interface PqCrypto {
    fun generateMlKemKeyPair(): KeyPair
    fun generateMlDsaKeyPair(): KeyPair

    /** Bob için: peer'ın public key'iyle ortak gizli üretir, ct'yi gönderir. */
    fun mlkemEncapsulate(peerPublicKey: ByteArray): KemResult

    /** Alice için: gelen ct'den ortak gizli çıkarır. */
    fun mlkemDecapsulate(privateKey: ByteArray, ciphertext: ByteArray): ByteArray

    /**
     * ML-DSA imzası. BC lightweight API'da private key tek başına yeterli olmadığı
     * için public key de istenir (constructor (params, priv, pub) zorunlu).
     */
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

