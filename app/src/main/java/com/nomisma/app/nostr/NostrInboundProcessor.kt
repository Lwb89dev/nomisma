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
    REJECTED
}

data class InboundProcessingResult(
    val status: InboundProcessingStatus,
    val operationId: String? = null,
    val message: String? = null
)

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
    private val signer: Signer,
    private val repository: LocalLedgerRepository,
    private val expectedAuthorByPubkey: Map<String, String> = emptyMap()
) {
    suspend fun process(event: NostrEvent): InboundProcessingResult {
        if (event.kind != NostrKinds.LEDGER_OPERATION && event.kind != NostrKinds.GIFT_WRAP) {
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
            val operation = if (event.kind == NostrKinds.GIFT_WRAP) {
                processGiftWrap(event)
            } else {
                val plaintext = signer.nip44Decrypt(event.content, event.pubkey)
                LedgerOperation.fromWireJson(plaintext).also { operation ->
                    require(operation.authorPubkey == event.pubkey) {
                        "Operation author pubkey does not match event signer"
                    }
                    validateParticipantBinding(operation, event.pubkey)
                }
            }
            val applied = repository.applyRemoteOperation(operation, event.id)
            applied.toInboundResult(operation.operationId)
        }.getOrElse {
            InboundProcessingResult(
                InboundProcessingStatus.REJECTED,
                message = it.message ?: "Could not process inbound operation"
            )
        }
    }

    private suspend fun processGiftWrap(event: NostrEvent): LedgerOperation {
        val localIdentity = signer.getIdentity()
        require(event.tags.any { tag -> tag[0] == "p" && tag[1] == localIdentity.pubkey }) {
            "Gift wrap is addressed to another recipient"
        }

        val seal = NostrEvent.fromJson(signer.nip44Decrypt(event.content, event.pubkey))
        require(seal.kind == NostrKinds.SEAL) { "Gift wrap does not contain a seal" }
        require(seal.tags.isEmpty()) { "NIP-59 seals must not contain tags" }
        NostrEventValidator.validate(seal).getOrThrow()

        val rumor = UnsignedNostrEvent.fromJson(signer.nip44Decrypt(seal.content, seal.pubkey))
        require(rumor.pubkey == seal.pubkey) { "Rumor author does not match seal signer" }
        require(rumor.kind == NostrKinds.LEDGER_OPERATION) { "Unsupported rumor kind" }
        return LedgerOperation.fromWireJson(rumor.content).also { operation ->
            require(operation.authorPubkey == rumor.pubkey) {
                "Operation author pubkey does not match rumor signer"
            }
            validateParticipantBinding(operation, rumor.pubkey)
        }
    }

    private fun validateParticipantBinding(operation: LedgerOperation, signerPubkey: String) {
        if (expectedAuthorByPubkey.isEmpty()) return
        val expectedAuthor = expectedAuthorByPubkey[signerPubkey]
            ?: error("Signer pubkey is not associated with a configured participant")
        require(operation.authorId == expectedAuthor) {
            "Operation author ${operation.authorId} does not match configured participant $expectedAuthor"
        }
    }
}

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
