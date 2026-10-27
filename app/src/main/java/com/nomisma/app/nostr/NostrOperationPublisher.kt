package com.nomisma.app.nostr

import com.nomisma.app.domain.LedgerOperation
import fr.acinq.secp256k1.Secp256k1
import java.security.SecureRandom

enum class OperationTransport {
    DIRECT_NIP44,
    NIP59_GIFT_WRAP
}

/**
 * Transport boundary for ledger operations. NIP-59 gift wrapping is the default
 * path; direct NIP-44 remains available for controlled relay smoke tests.
 */
class NostrOperationPublisher(
    private val signer: Signer,
    private val relay: NostrRelayClient,
    private val localAuthorId: String? = null
) {
    suspend fun publish(
        operation: LedgerOperation,
        recipientPubkey: String,
        transport: OperationTransport = OperationTransport.NIP59_GIFT_WRAP
    ): NostrEvent = when (transport) {
        OperationTransport.DIRECT_NIP44 -> publishDirectNip44(operation, recipientPubkey)
        OperationTransport.NIP59_GIFT_WRAP -> publishGiftWrap(operation, recipientPubkey)
    }

    suspend fun publishDirectNip44(operation: LedgerOperation, recipientPubkey: String): NostrEvent {
        val signerIdentity = signer.getIdentity()
        val encrypted = signer.nip44Encrypt(
            operationWithSigner(operation, signerIdentity.pubkey).toWireJson(),
            recipientPubkey
        )
        val unsigned = UnsignedNostrEvent(
            pubkey = null,
            createdAt = operation.createdAtMillis / 1000L,
            kind = NostrKinds.LEDGER_OPERATION,
            tags = listOf(
                NostrTag(listOf("p", recipientPubkey)),
                NostrTag(listOf("alt", "Nomisma encrypted ledger operation v1"))
            ),
            content = encrypted
        )
        val signed = signer.signEvent(unsigned)
        NostrEventValidator.validate(signed).getOrThrow()
        relay.publishAndAwaitAck(signed)
        return signed
    }

    suspend fun publishGiftWrap(operation: LedgerOperation, recipientPubkey: String): NostrEvent {
        val giftWrap = buildGiftWrap(operation, recipientPubkey)
        relay.publishAndAwaitAck(giftWrap)
        return giftWrap
    }

    suspend fun publishCheckpoint(digest: LedgerDigest, recipientPubkey: String): NostrEvent {
        val giftWrap = buildCheckpoint(digest, recipientPubkey)
        relay.publishAndAwaitAck(giftWrap, timeoutMillis = 5_000L)
        return giftWrap
    }

    /**
     * A ledger checkpoint is only meaningful for a few minutes, so its gift wrap carries a NIP-40
     * expiration and relays that honour it drop it afterwards. (Ephemeral kinds would be cleaner,
     * but nos.lol refuses them outright.)
     */
    internal suspend fun buildCheckpoint(digest: LedgerDigest, recipientPubkey: String): NostrEvent {
        val signerIdentity = signer.getIdentity()
        val rumor = UnsignedNostrEvent(
            pubkey = signerIdentity.pubkey,
            createdAt = digest.sentAtMillis / 1000L,
            kind = NostrKinds.LEDGER_CHECKPOINT,
            tags = emptyList(),
            content = digest.toJson()
        )
        val expiresAt = System.currentTimeMillis() / 1000L + CHECKPOINT_LIFETIME_SECONDS
        return wrapRumor(rumor, recipientPubkey, NostrKinds.GIFT_WRAP, listOf(NostrTag(listOf("expiration", expiresAt.toString()))))
    }

    /** Rumor → seal signed by the user's signer → gift wrap signed by a one-time key (NIP-59). */
    internal suspend fun buildGiftWrap(operation: LedgerOperation, recipientPubkey: String): NostrEvent {
        val signerIdentity = signer.getIdentity()
        val rumor = UnsignedNostrEvent(
            pubkey = signerIdentity.pubkey,
            createdAt = operation.createdAtMillis / 1000L,
            kind = NostrKinds.LEDGER_OPERATION,
            tags = emptyList(),
            content = operationWithSigner(operation, signerIdentity.pubkey).toWireJson()
        )
        return wrapRumor(rumor, recipientPubkey, NostrKinds.GIFT_WRAP)
    }

    internal suspend fun wrapRumor(
        rumor: UnsignedNostrEvent,
        recipientPubkey: String,
        wrapperKind: Int,
        extraTags: List<NostrTag> = emptyList()
    ): NostrEvent {
        val sealedContent = signer.nip44Encrypt(rumor.toRumorJson(), recipientPubkey)
        val seal = signer.signEvent(
            UnsignedNostrEvent(
                pubkey = null,
                createdAt = randomizedPastTimestamp(),
                kind = NostrKinds.SEAL,
                tags = emptyList(),
                content = sealedContent
            )
        )
        NostrEventValidator.validate(seal).getOrThrow()
        require(seal.tags.isEmpty()) { "NIP-59 seals must not contain tags" }

        val ephemeralPrivateKey = newPrivateKey()
        try {
            return wrapSeal(seal, recipientPubkey, ephemeralPrivateKey, wrapperKind, extraTags)
        } finally {
            ephemeralPrivateKey.fill(0)
        }
    }

    private fun wrapSeal(
        seal: NostrEvent,
        recipientPubkey: String,
        ephemeralPrivateKey: ByteArray,
        wrapperKind: Int,
        extraTags: List<NostrTag>
    ): NostrEvent {
        val ephemeralPubkey = NostrHex.encode(
            Secp256k1.pubkeyCreate(ephemeralPrivateKey).copyOfRange(1, 33)
        )
        val wrappedSeal = Nip44V2.encrypt(
            plaintext = seal.toJson(),
            privateKey = ephemeralPrivateKey,
            recipientPubkeyHex = recipientPubkey
        )
        val giftWrapUnsigned = UnsignedNostrEvent(
            pubkey = ephemeralPubkey,
            // Ephemeral wraps are never stored, so there is no history to blur; some relays drop
            // ephemeral events that look old.
            createdAt = if (wrapperKind == NostrKinds.GIFT_WRAP) randomizedPastTimestamp() else System.currentTimeMillis() / 1000L,
            kind = wrapperKind,
            tags = listOf(NostrTag(listOf("p", recipientPubkey))) + extraTags,
            content = wrappedSeal
        )
        val giftWrapId = NostrHash.sha256(giftWrapUnsigned.canonicalSerialization(ephemeralPubkey))
        val giftWrapSignature = Secp256k1.signSchnorr(giftWrapId, ephemeralPrivateKey, randomBytes(32))
        val giftWrap = NostrEvent(
            id = NostrHex.encode(giftWrapId),
            pubkey = ephemeralPubkey,
            createdAt = giftWrapUnsigned.createdAt,
            kind = giftWrapUnsigned.kind,
            tags = giftWrapUnsigned.tags,
            content = giftWrapUnsigned.content,
            sig = NostrHex.encode(giftWrapSignature)
        )
        NostrEventValidator.validate(giftWrap).getOrThrow()
        return giftWrap
    }

    private fun operationWithSigner(operation: LedgerOperation, signerPubkey: String): LedgerOperation {
        localAuthorId?.let {
            require(operation.authorId == it) {
                "Operation author ${operation.authorId} is not the configured local participant $it"
            }
        }
        return operation.copy(authorPubkey = signerPubkey)
    }

    private fun newPrivateKey(): ByteArray {
        while (true) {
            val key = randomBytes(32)
            if (Secp256k1.secKeyVerify(key)) return key
            key.fill(0)
        }
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

    private fun randomizedPastTimestamp(): Long =
        (System.currentTimeMillis() / 1000L) - 3_600L - secureRandom.nextInt(172_800).toLong()

    private companion object {
        const val CHECKPOINT_LIFETIME_SECONDS = 15 * 60L
        val secureRandom = SecureRandom()
    }
}
