package com.nomisma.app.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignerResponseValidatorTest {
    private val pubkey = "ab".repeat(32)
    private val request = UnsignedNostrEvent(
        pubkey = null,
        createdAt = 123L,
        kind = 13,
        tags = listOf(NostrTag(listOf("p", "cd".repeat(32)))),
        content = "ciphertext"
    )

    @Test
    fun acceptsAnEventThatExactlyMatchesTheSigningRequest() {
        val event = signedEvent()
        assertEquals(event, SignerResponseValidator.requireMatchesRequest(request, pubkey, event))
    }

    @Test
    fun rejectsIdentityOrPayloadSubstitution() {
        assertTrue(
            runCatching {
                SignerResponseValidator.requireMatchesRequest(request, "cd".repeat(32), signedEvent())
            }.isFailure
        )
        assertTrue(
            runCatching {
                SignerResponseValidator.requireMatchesRequest(request, pubkey, signedEvent().copy(content = "altered"))
            }.isFailure
        )
        assertTrue(
            runCatching {
                SignerResponseValidator.requireMatchesRequest(request, pubkey, signedEvent().copy(kind = 1))
            }.isFailure
        )
    }

    private fun signedEvent() = NostrEvent(
        id = "00".repeat(32),
        pubkey = pubkey,
        createdAt = request.createdAt,
        kind = request.kind,
        tags = request.tags,
        content = request.content,
        sig = "00".repeat(64)
    )
}
