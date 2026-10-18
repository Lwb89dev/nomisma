package com.nomisma.app.nostr

import com.nomisma.app.domain.LedgerOperation
import fr.acinq.secp256k1.Secp256k1Jni
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
        val signerIdentity = signer.getIdentity()
        val rumor = UnsignedNostrEvent(
            pubkey = signerIdentity.pubkey,
            createdAt = operation.createdAtMillis / 1000L,
            kind = NostrKinds.LEDGER_OPERATION,
            tags = emptyList(),
            content = operationWithSigner(operation, signerIdentity.pubkey).toWireJson()
        )
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
            return publishWrappedSeal(seal, recipientPubkey, ephemeralPrivateKey)
        } finally {
            ephemeralPrivateKey.fill(0)
        }
    }

    private suspend fun publishWrappedSeal(
        seal: NostrEvent,
        recipientPubkey: String,
        ephemeralPrivateKey: ByteArray
    ): NostrEvent {
        val ephemeralPubkey = NostrHex.encode(
            Secp256k1Jni.pubkeyCreate(ephemeralPrivateKey).copyOfRange(1, 33)
        )
        val wrappedSeal = Nip44V2.encrypt(
            plaintext = seal.toJson(),
            privateKey = ephemeralPrivateKey,
            recipientPubkeyHex = recipientPubkey
        )
        val giftWrapUnsigned = UnsignedNostrEvent(
            pubkey = ephemeralPubkey,
            createdAt = randomizedPastTimestamp(),
            kind = NostrKinds.GIFT_WRAP,
            tags = listOf(NostrTag(listOf("p", recipientPubkey))),
            content = wrappedSeal
        )
        val giftWrapId = NostrHash.sha256(giftWrapUnsigned.canonicalSerialization(ephemeralPubkey))
        val giftWrapSignature = Secp256k1Jni.signSchnorr(
            ephemeralPrivateKey,
            giftWrapId,
            randomBytes(32)
        )
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
        relay.publishAndAwaitAck(giftWrap)
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
            if (Secp256k1Jni.secKeyVerify(key)) return key
            key.fill(0)
        }
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

    private fun randomizedPastTimestamp(): Long =
        (System.currentTimeMillis() / 1000L) - 3_600L - secureRandom.nextInt(172_800).toLong()

    private companion object {
        val secureRandom = SecureRandom()
    }
}
