package com.nomisma.app.nostr

import org.junit.Assert.assertTrue
import org.junit.Test

class NostrModelsTest {
    @Test
    fun eventShapeRejectsOversizedContentBeforeCryptographicValidation() {
        val event = NostrEvent(
            id = "0".repeat(64),
            pubkey = "1".repeat(64),
            createdAt = 1L,
            kind = NostrKinds.GIFT_WRAP,
            tags = listOf(NostrTag(listOf("p", "2".repeat(64)))),
            content = "x".repeat(NostrLimits.MAX_EVENT_CONTENT_LENGTH + 1),
            sig = "3".repeat(128)
        )

        assertTrue(NostrEventValidator.validateShape(event).isFailure)
    }

    @Test
    fun tagConstructorRejectsUnboundedMetadata() {
        val tooManyValues = List(NostrLimits.MAX_TAG_VALUES + 1) { "value-$it" }

        assertTrue(runCatching { NostrTag(tooManyValues) }.isFailure)
        assertTrue(
            runCatching {
                NostrTag(listOf("x".repeat(NostrLimits.MAX_TAG_VALUE_LENGTH + 1)))
            }.isFailure
        )
    }
}
