package com.nomisma.app.nostr

import com.nomisma.app.domain.LedgerOperation
import com.nomisma.app.domain.OperationType
import fr.acinq.secp256k1.Secp256k1
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs against the real native secp256k1 library (jni-jvm), not a mock. */
class NostrCryptoTest {
    private fun hex(value: String) = NostrHex.decode(value)

    @Test
    fun conversationKeyMatchesOfficialNip44Vectors() {
        // References recomputed in pure Python: HMAC-SHA256("nip44-v2", x(sec · P)).
        assertEquals(
            "c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d",
            NostrHex.encode(Nip44V2.conversationKey(hex("0".repeat(63) + "1"), "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"))
        )
        assertEquals(
            "3dfef0ce2a4d80a25e7a328accf73448ef67096f65f79588e358d9a0eb9013f1",
            NostrHex.encode(
                Nip44V2.conversationKey(
                    hex("315e59ff51cb9209768cf7da80791ddcaae56ac9775eb25b6dee1234bc5d2268"),
                    "c2f9d9948dc8c7c38321e4b85c8558872eafa0641cd269db76848a6073e69133"
                )
            )
        )
    }

    @Test
    fun giftWrapBuiltByOneDeviceOpensOnTheOtherWithTheSameOperation() = runBlocking {
        val alice = LocalKeySigner(NostrHash.sha256("alice test key"))
        val bob = LocalKeySigner(NostrHash.sha256("bob test key"))
        val roles = mapOf(alice.pubkey to "alice", bob.pubkey to "bob")
        val operation = LedgerOperation(
            operationId = "op-1",
            ledgerId = "ledger-1",
            authorId = "alice",
            authorSequence = 1L,
            type = OperationType.ENTRY_CREATE,
            createdAtMillis = 1_760_000_000_000L,
            payloadJson = """{"entry_id":"e-1","amount_minor":500,"currency":"EUR","description":"Test / sync"}"""
        )

        val giftWrap = NostrOperationPublisher(alice, NostrRelayClient("wss://relay.example.com"), "alice")
            .buildGiftWrap(operation, bob.pubkey)

        assertTrue(NostrEventValidator.validate(giftWrap).isSuccess)
        assertEquals(NostrKinds.GIFT_WRAP, giftWrap.kind)
        assertEquals(listOf(NostrTag(listOf("p", bob.pubkey))), giftWrap.tags)
        assertNotEquals(alice.pubkey, giftWrap.pubkey)

        val received = NostrOperationUnwrapper(bob, roles).unwrap(giftWrap)
        assertEquals(operation.operationId, received.operationId)
        assertEquals(operation.authorSequence, received.authorSequence)
        assertEquals(operation.type, received.type)
        assertEquals(alice.pubkey, received.authorPubkey)
        assertEquals(500L, org.json.JSONObject(received.payloadJson).getLong("amount_minor"))
    }

    @Test
    fun giftWrapIsUnreadableForAThirdKeyAndTamperingBreaksTheSignature() = runBlocking {
        val alice = LocalKeySigner(NostrHash.sha256("alice test key"))
        val bob = LocalKeySigner(NostrHash.sha256("bob test key"))
        val mallory = LocalKeySigner(NostrHash.sha256("mallory test key"))
        val operation = LedgerOperation("op-2", "ledger-1", "alice", 2L, OperationType.THEME_SET, 1_760_000_000_000L, """{"theme_id":"vessel"}""")
        val giftWrap = NostrOperationPublisher(alice, NostrRelayClient("wss://relay.example.com"), "alice")
            .buildGiftWrap(operation, bob.pubkey)

        assertTrue(runCatching { NostrOperationUnwrapper(mallory).unwrap(giftWrap) }.isFailure)
        assertTrue(NostrEventValidator.validate(giftWrap.copy(content = giftWrap.content.reversed())).isFailure)
        assertTrue(NostrEventValidator.validate(giftWrap.copy(createdAt = giftWrap.createdAt + 1)).isFailure)
    }

    @Test
    fun giftWrapFromOutsideThePairingIsIgnoredWithoutOpeningItsContent() = runBlocking {
        val alice = LocalKeySigner(NostrHash.sha256("alice test key"))
        val bob = CountingSigner(LocalKeySigner(NostrHash.sha256("bob test key")))
        val stranger = LocalKeySigner(NostrHash.sha256("stranger test key"))
        val bobPubkey = bob.getIdentity().pubkey
        val operation = LedgerOperation("op-3", "ledger-1", "carol", 1L, OperationType.THEME_SET, 1_760_000_000_000L, """{"theme_id":"vessel"}""")
        val fromStranger = NostrOperationPublisher(stranger, NostrRelayClient("wss://relay.example.com"))
            .buildGiftWrap(operation, bobPubkey)

        val failure = runCatching {
            NostrOperationUnwrapper(bob, mapOf(alice.pubkey to "alice", bobPubkey to "bob")).unwrap(fromStranger)
        }.exceptionOrNull()
        assertTrue(failure is ForeignEventException)
        assertEquals("only the outer layer may be decrypted", 1, bob.decryptions)
    }

    @Test
    fun checkpointTravelsInAnExpiringWrapAndCannotImpersonateTheOtherParticipant() = runBlocking {
        val alice = LocalKeySigner(NostrHash.sha256("alice test key"))
        val bob = LocalKeySigner(NostrHash.sha256("bob test key"))
        val roles = mapOf(alice.pubkey to "alice", bob.pubkey to "bob")
        val digest = LedgerDigest("ledger-1", "alice", mapOf("alice" to 2L), "ab".repeat(32), emptyMap(), null, 0L, false, 1_760_000_000_000L)
        val publisher = NostrOperationPublisher(alice, NostrRelayClient("wss://relay.example.com"), "alice")

        val wrap = publisher.buildCheckpoint(digest, bob.pubkey)

        assertEquals(NostrKinds.GIFT_WRAP, wrap.kind)
        assertTrue(NostrEventValidator.validate(wrap).isSuccess)
        val expiration = wrap.tags.single { it[0] == "expiration" }[1]!!.toLong()
        assertTrue(expiration > System.currentTimeMillis() / 1000L)
        assertEquals(InboundPayload.Checkpoint(digest), NostrOperationUnwrapper(bob, roles).open(wrap))

        val impersonating = publisher.buildCheckpoint(digest.copy(authorId = "bob"), bob.pubkey)
        assertTrue(runCatching { NostrOperationUnwrapper(bob, roles).open(impersonating) }.isFailure)
    }
}

/** Counts how many layers the receiver asked its signer to decrypt. */
private class CountingSigner(private val inner: Signer) : Signer by inner {
    var decryptions = 0
    override suspend fun nip44Decrypt(ciphertext: String, senderPubkey: String): String {
        decryptions += 1
        return inner.nip44Decrypt(ciphertext, senderPubkey)
    }
}

/** Stands in for Amber: same NIP-01 signing and NIP-44 encryption, with a key held in memory. */
private class LocalKeySigner(private val secretKey: ByteArray) : Signer {
    val pubkey: String = NostrHex.encode(Secp256k1.pubkeyCreate(secretKey).copyOfRange(1, 33))

    override suspend fun getIdentity() = SignerIdentity(pubkey, "test")

    override suspend fun signEvent(event: UnsignedNostrEvent): NostrEvent {
        val unsigned = event.copy(pubkey = pubkey)
        val id = NostrHash.sha256(unsigned.canonicalSerialization(pubkey))
        return NostrEvent(
            id = NostrHex.encode(id),
            pubkey = pubkey,
            createdAt = unsigned.createdAt,
            kind = unsigned.kind,
            tags = unsigned.tags,
            content = unsigned.content,
            sig = NostrHex.encode(Secp256k1.signSchnorr(id, secretKey, null))
        )
    }

    override suspend fun nip44Encrypt(plaintext: String, recipientPubkey: String): String =
        Nip44V2.encrypt(plaintext, secretKey, recipientPubkey)

    override suspend fun nip44Decrypt(ciphertext: String, senderPubkey: String): String =
        Nip44V2.decryptWithConversationKey(ciphertext, Nip44V2.conversationKey(secretKey, senderPubkey))
}
