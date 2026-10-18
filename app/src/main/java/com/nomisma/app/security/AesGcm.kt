package com.nomisma.app.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM blobs: 12-byte IV followed by ciphertext and the 16-byte tag.
 * The key never lives in this object. Callers supply it.
 */
internal object AesGcm {
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val random = SecureRandom()

    fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val body = cipher.doFinal(plaintext)
        return iv + body
    }

    fun decrypt(key: SecretKey, payload: ByteArray): ByteArray {
        require(payload.size > IV_BYTES + TAG_BITS / 8) { "Encrypted payload is too short" }
        val iv = payload.copyOfRange(0, IV_BYTES)
        val body = payload.copyOfRange(IV_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(body)
    }
}
