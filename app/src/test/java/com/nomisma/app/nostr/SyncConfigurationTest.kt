package com.nomisma.app.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncConfigurationTest {
    @Test
    fun configurationNormalizesRelayAndRecipient() {
        val recipient = "AB".repeat(32)
        val configuration = SyncConfiguration(
            relayUrl = "  wss://relay.example  ",
            recipientPubkey = " $recipient ",
            localParticipantId = "alice",
            remoteParticipantId = "bob"
        ).normalized()

        assertEquals("wss://relay.example", configuration.relayUrl)
        assertEquals(recipient.lowercase(), configuration.recipientPubkey)
    }

    @Test
    fun configurationRejectsInvalidOrAmbiguousBindings() {
        val invalidKey = runCatching {
            SyncConfiguration("wss://relay.example", "not-a-key", "alice", "bob").normalized()
        }
        val sameParticipant = runCatching {
            SyncConfiguration("wss://relay.example", "ab".repeat(32), "alice", "alice").normalized()
        }

        assertTrue(invalidKey.isFailure)
        assertTrue(sameParticipant.isFailure)
    }

    @Test
    fun configurationEnforcesTheApplicationTransportPolicy() {
        val key = "ab".repeat(32)
        val lan = SyncConfiguration(
            "ws://192.168.1.10:7777",
            key,
            "alice",
            "bob"
        ).normalized()
        assertEquals("ws://192.168.1.10:7777", lan.relayUrl)

        val unapprovedCleartext = runCatching {
            SyncConfiguration("ws://192.168.1.20:7777", key, "alice", "bob").normalized()
        }
        val credentialsInUrl = runCatching {
            SyncConfiguration("wss://user:password@relay.example", key, "alice", "bob").normalized()
        }
        assertTrue(unapprovedCleartext.isFailure)
        assertTrue(credentialsInUrl.isFailure)
    }
}
