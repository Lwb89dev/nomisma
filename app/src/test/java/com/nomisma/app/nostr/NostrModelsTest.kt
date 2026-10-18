package com.nomisma.app.nostr

import org.junit.Assert.assertEquals
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
    fun canonicalSerializationMatchesNip01ForSlashesAndEscapes() {
        val unsigned = UnsignedNostrEvent(
            pubkey = PUBKEY,
            createdAt = 1_700_000_000L,
            kind = NostrKinds.SEAL,
            tags = listOf(NostrTag(listOf("p", "a/b")), NostrTag(listOf("alt", "Nomisma \"v1\""))),
            content = "AqF/9+x/y== \"q\" \\ \n\t\r\b\u000C\u0001 é€ </script>"
        )

        // Reference produced with Python json.dumps(separators=(',', ':'), ensure_ascii=False).
        assertEquals(
            "[0,\"$PUBKEY\",1700000000,13,[[\"p\",\"a/b\"],[\"alt\",\"Nomisma \\\"v1\\\"\"]]," +
                "\"AqF/9+x/y== \\\"q\\\" \\\\ \\n\\t\\r\\b\\f\\u0001 é€ </script>\"]",
            unsigned.canonicalSerialization()
        )
        assertEquals(
            "3f4c1141b3981879bb5f1e76eebdd31eb798b5fdb71869b235e294af84b5e96f",
            NostrHash.sha256Hex(unsigned.canonicalSerialization())
        )
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

    private companion object {
        const val PUBKEY = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
    }
}
