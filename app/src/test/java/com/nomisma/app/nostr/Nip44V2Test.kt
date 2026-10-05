package com.nomisma.app.nostr

import org.junit.Assert.assertEquals
import org.junit.Test

class Nip44V2Test {
    @Test
    fun encryptsOfficialVector() {
        val conversationKey = decodeHex("c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d")
        val nonce = decodeHex("0000000000000000000000000000000000000000000000000000000000000001")
        val expectedPayload = "AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABee0G5VSK0/9YypIObAtDKfYEAjD35uVkHyB0F4DwrcNaCXlCWZKaArsGrY6M9wnuTMxWfp1RTN9Xga8no+kF5Vsb"

        val payload = Nip44V2.encryptWithConversationKey("a", conversationKey, nonce)

        assertEquals(expectedPayload, payload)
        assertEquals("a", Nip44V2.decryptWithConversationKey(expectedPayload, conversationKey))
    }

    private fun decodeHex(value: String): ByteArray = ByteArray(value.length / 2) { index ->
        val high = value[index * 2].digitToInt(16)
        val low = value[index * 2 + 1].digitToInt(16)
        ((high shl 4) or low).toByte()
    }
}
