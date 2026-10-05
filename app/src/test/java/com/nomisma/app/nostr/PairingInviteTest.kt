package com.nomisma.app.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingInviteTest {
    private val pubkeyA = "ab".repeat(32)
    private val pubkeyB = "cd".repeat(32)

    @Test
    fun codeRoundTripNormalizesAndCanBeReadFromSharedText() {
        val invite = PairingInvite(
            ledgerId = " ledger-main ",
            relayUrl = " wss://relay.example.test/nostr ",
            senderPubkey = pubkeyA.uppercase(),
            senderParticipantId = " alice ",
            receiverParticipantId = " bob "
        )

        val code = PairingInviteCodec.encode(invite)
        val decoded = PairingInviteCodec.decode("Invito Nomisma\n\n$code\n")

        assertEquals("ledger-main", decoded.ledgerId)
        assertEquals("wss://relay.example.test/nostr", decoded.relayUrl)
        assertEquals(pubkeyA, decoded.senderPubkey)
        assertEquals("alice", decoded.senderParticipantId)
        assertEquals("bob", decoded.receiverParticipantId)
    }

    @Test
    fun malformedOrUnsafeInvitesAreRejected() {
        val invalidRelays = listOf(
            "https://relay.example.test",
            "ws://user:password@relay.example.test",
            "ws://relay.example.test/#fragment",
            "ws:///missing-host"
        )

        invalidRelays.forEach { relay ->
            val result = runCatching {
                PairingInvite("ledger", relay, pubkeyA, "alice", "bob").normalized()
            }
            assertTrue("Relay should be rejected: $relay", result.isFailure)
        }
        assertTrue(
            runCatching { PairingInvite("ledger", "wss://relay.test", "xyz", "alice", "bob").normalized() }.isFailure
        )
        assertTrue(
            runCatching { PairingInvite("ledger", "wss://relay.test", pubkeyA, "alice", "alice").normalized() }.isFailure
        )
        assertTrue(runCatching { PairingInviteCodec.decode("not-a-nomisma-code") }.isFailure)
    }

    @Test
    fun checksumRejectsATamperedCode() {
        val code = PairingInviteCodec.encode(
            PairingInvite("ledger", "wss://relay.test", pubkeyA, "alice", "bob")
        )
        val position = code.length / 2
        val replacement = if (code[position] == 'A') 'B' else 'A'
        val tampered = code.replaceRange(position, position + 1, replacement.toString())

        assertTrue(runCatching { PairingInviteCodec.decode(tampered) }.isFailure)
    }

    @Test
    fun readinessRequiresACompleteAndDistinctConfiguration() {
        val ready = PairingReadinessEvaluator.evaluate(
            relayUrl = "wss://relay.example.test",
            localPubkey = pubkeyA,
            recipientPubkey = pubkeyB,
            localParticipantId = "alice",
            remoteParticipantId = "bob"
        )
        assertTrue(ready.isReady)

        val sameKey = PairingReadinessEvaluator.evaluate(
            relayUrl = "wss://relay.example.test",
            localPubkey = pubkeyA,
            recipientPubkey = pubkeyA,
            localParticipantId = "alice",
            remoteParticipantId = "bob"
        )
        assertFalse(sameKey.keysAreDistinct)
        assertFalse(sameKey.isReady)

        val incomplete = PairingReadinessEvaluator.evaluate(
            relayUrl = "https://relay.example.test",
            localPubkey = null,
            recipientPubkey = "",
            localParticipantId = "alice",
            remoteParticipantId = "alice"
        )
        assertFalse(incomplete.relayReady)
        assertFalse(incomplete.identityReady)
        assertFalse(incomplete.recipientReady)
        assertFalse(incomplete.participantsReady)
        assertFalse(incomplete.isReady)
    }

    @Test
    fun transportPolicyAllowsTlsEverywhereAndCleartextOnlyForTheConfiguredLanRelay() {
        assertTrue(RelayTransportPolicy.isSupported("wss://relay.example.test/nostr"))
        assertTrue(RelayTransportPolicy.isSupported("ws://192.168.1.10:7777"))
        assertFalse(RelayTransportPolicy.isSupported("ws://192.168.1.20:7777"))
        assertFalse(RelayTransportPolicy.isSupported("https://relay.example.test"))
    }
}
