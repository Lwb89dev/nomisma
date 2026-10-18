package com.nomisma.app.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.spec.SecretKeySpec

class AesGcmTest {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

    @Test
    fun roundTripRestoresPlaintext() {
        val plain = "spesa di casa".toByteArray()
        val sealed = AesGcm.encrypt(key, plain)
        assertArrayEquals(plain, AesGcm.decrypt(key, sealed))
    }

    @Test
    fun twoSealsOfTheSameTextDiffer() {
        val plain = "relay".toByteArray()
        val first = AesGcm.encrypt(key, plain)
        val second = AesGcm.encrypt(key, plain)
        check(!first.contentEquals(second))
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val sealed = AesGcm.encrypt(key, "ledger".toByteArray())
        sealed[sealed.lastIndex] = (sealed[sealed.lastIndex] + 1).toByte()
        assertThrows(AEADBadTagException::class.java) {
            AesGcm.decrypt(key, sealed)
        }
    }
}
