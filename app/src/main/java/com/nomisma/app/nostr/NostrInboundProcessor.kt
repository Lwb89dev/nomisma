package com.nomisma.app.nostr

import com.nomisma.app.data.LocalLedgerRepository
import com.nomisma.app.data.RemoteOperationResult
import com.nomisma.app.data.RemoteOperationStatus
import com.nomisma.app.domain.LedgerOperation

enum class InboundProcessingStatus {
    IGNORED,
    APPLIED,
    DEFERRED,
    DUPLICATE,
    REJECTED,
    CHECKPOINT
}

data class InboundProcessingResult(
    val status: InboundProcessingStatus,
    val operationId: String? = null,
    val message: String? = null,
    val checkpoint: LedgerDigest? = null
)

/** What a gift wrap from the partner carries. */
sealed interface InboundPayload {
    data class Operation(val operation: LedgerOperation) : InboundPayload
    data class Checkpoint(val digest: LedgerDigest) : InboundPayload
}

/**
 * Converts an encrypted relay event back into a domain operation. The local
 * repository remains the authority for ledger validation, deduplication and
 * conflict handling; this class only owns the Nostr transport boundary.
 *
 * Inbound events pass structural and BIP-340 Schnorr verification before
 * decryption. The operation payload also carries the signing pubkey, which is
 * checked against the transport event before the repository is touched.
 */
class NostrInboundProcessor(
    signer: Signer,
    private val repository: LocalLedgerRepository,
    expectedAuthorByPubkey: Map<String, String> = emptyMap()
) {
    private val unwrapper = NostrOperationUnwrapper(signer, expectedAuthorByPubkey)

    suspend fun process(event: NostrEvent): InboundProcessingResult {
        if (event.kind !in INBOUND_KINDS) {
            return InboundProcessingResult(InboundProcessingStatus.IGNORED, message = "Unsupported event kind")
        }

        val validation = NostrEventValidator.validate(event)
        if (validation.isFailure) {
            return InboundProcessingResult(
                InboundProcessingStatus.REJECTED,
                message = validation.exceptionOrNull()?.message ?: "Invalid Nostr event"
            )
        }

        return runCatching {
            when (val payload = unwrapper.open(event)) {
                is InboundPayload.Checkpoint ->
                    InboundProcessingResult(InboundProcessingStatus.CHECKPOINT, checkpoint = payload.digest)
                is InboundPayload.Operation -> repository
                    .applyRemoteOperation(payload.operation, event.id)
                    .toInboundResult(payload.operation.operationId)
            }
        }.getOrElse {
            if (it is ForeignEventException) {
                return InboundProcessingResult(InboundProcessingStatus.IGNORED, message = it.message)
            }
            InboundProcessingResult(
                InboundProcessingStatus.REJECTED,
                message = it.message ?: "Could not process inbound operation"
            )
        }
    }
}

/** An event in the inbox that belongs to something else, typically a private message. */
class ForeignEventException(message: String) : IllegalArgumentException(message)

/** Opens an event addressed to this device and returns the ledger operation it carries. */
class NostrOperationUnwrapper(
    private val signer: Signer,
    private val expectedAuthorByPubkey: Map<String, String> = emptyMap()
) {
    suspend fun unwrap(event: NostrEvent): LedgerOperation =
        (open(event) as? InboundPayload.Operation)?.operation
            ?: throw ForeignEventException("Not a Nomisma operation")

    suspend fun open(event: NostrEvent): InboundPayload =
        if (event.kind == NostrKinds.GIFT_WRAP || event.kind == NostrKinds.EPHEMERAL_GIFT_WRAP) {
            unwrapGiftWrap(event)
        } else {
            InboundPayload.Operation(directOperation(event))
        }

    private suspend fun directOperation(event: NostrEvent): LedgerOperation =
        LedgerOperation.fromWireJson(signer.nip44Decrypt(event.content, event.pubkey)).also { operation ->
            require(operation.authorPubkey == event.pubkey) {
                "Operation author pubkey does not match event signer"
            }
            validateParticipantBinding(operation.authorId, event.pubkey)
        }

    private suspend fun unwrapGiftWrap(event: NostrEvent): InboundPayload {
        val localIdentity = signer.getIdentity()
        require(event.tags.any { tag -> tag[0] == "p" && tag[1] == localIdentity.pubkey }) {
            "Gift wrap is addressed to another recipient"
        }

        val seal = NostrEvent.fromJson(signer.nip44Decrypt(event.content, event.pubkey))
        require(seal.kind == NostrKinds.SEAL) { "Gift wrap does not contain a seal" }
        require(seal.tags.isEmpty()) { "NIP-59 seals must not contain tags" }
        NostrEventValidator.validate(seal).getOrThrow()
        // The same inbox carries the user's private messages from other apps. The seal names the
        // sender, so anything not from this pairing is dropped before its content is decrypted.
        if (expectedAuthorByPubkey.isNotEmpty() && seal.pubkey !in expectedAuthorByPubkey) {
            throw ForeignEventException("Gift wrap from a key outside this pairing")
        }

        val rumor = UnsignedNostrEvent.fromJson(signer.nip44Decrypt(seal.content, seal.pubkey))
        require(rumor.pubkey == seal.pubkey) { "Rumor author does not match seal signer" }
        return when (rumor.kind) {
            NostrKinds.LEDGER_OPERATION -> InboundPayload.Operation(
                LedgerOperation.fromWireJson(rumor.content).also { operation ->
                    require(operation.authorPubkey == rumor.pubkey) {
                        "Operation author pubkey does not match rumor signer"
                    }
                    validateParticipantBinding(operation.authorId, rumor.pubkey)
                }
            )
            NostrKinds.LEDGER_CHECKPOINT -> InboundPayload.Checkpoint(
                LedgerDigest.fromJson(rumor.content).also { validateParticipantBinding(it.authorId, rumor.pubkey) }
            )
            else -> throw ForeignEventException("Not a Nomisma operation")
        }
    }

    private fun validateParticipantBinding(authorId: String, signerPubkey: String) {
        if (expectedAuthorByPubkey.isEmpty()) return
        val expectedAuthor = expectedAuthorByPubkey[signerPubkey]
            ?: error("Signer pubkey is not associated with a configured participant")
        require(authorId == expectedAuthor) {
            "Operation author $authorId does not match configured participant $expectedAuthor"
        }
    }
}

private val INBOUND_KINDS = setOf(
    NostrKinds.LEDGER_OPERATION,
    NostrKinds.GIFT_WRAP,
    NostrKinds.EPHEMERAL_GIFT_WRAP
)

private fun RemoteOperationResult.toInboundResult(operationId: String): InboundProcessingResult =
    InboundProcessingResult(
        status = when (status) {
            RemoteOperationStatus.APPLIED -> InboundProcessingStatus.APPLIED
            RemoteOperationStatus.DEFERRED -> InboundProcessingStatus.DEFERRED
            RemoteOperationStatus.DUPLICATE -> InboundProcessingStatus.DUPLICATE
            RemoteOperationStatus.REJECTED -> InboundProcessingStatus.REJECTED
        },
        operationId = operationId,
        message = message
    )
