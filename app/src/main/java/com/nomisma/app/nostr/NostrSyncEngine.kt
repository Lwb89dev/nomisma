package com.nomisma.app.nostr

import com.nomisma.app.data.LocalLedgerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

data class SyncReport(
    val attempted: Int,
    val published: Int,
    val failures: List<String>,
    /** The relay stopped acknowledging: the rest of the outbox was left for after a reconnect. */
    val relayUnresponsive: Boolean = false
)

/**
 * Sync boundary for the local outbox and relay inbox. NIP-59 is the default
 * transport, while the direct NIP-44 path remains injectable for diagnostics.
 */
class NostrSyncEngine(
    private val localRepository: LocalLedgerRepository,
    private val publisher: NostrOperationPublisher,
    private val inboundProcessor: NostrInboundProcessor
) {
    suspend fun publishPending(
        recipientPubkey: String,
        transport: OperationTransport = OperationTransport.NIP59_GIFT_WRAP
    ): SyncReport {
        val pending = localRepository.pendingOperations()
        var published = 0
        val failures = mutableListOf<String>()
        for (operation in pending) {
            try {
                val event = publisher.publish(operation, recipientPubkey, transport)
                localRepository.markOperationPublished(operation.operationId, event.id)
                published += 1
            } catch (timeout: TimeoutCancellationException) {
                // No acknowledgement: the connection is most likely gone. Waiting out every
                // remaining operation would only stall the session.
                failures += "${operation.operationId}: ${timeout.message ?: "relay timeout"}"
                return SyncReport(pending.size, published, failures, relayUnresponsive = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                failures += "${operation.operationId}: ${failure.message ?: "unknown error"}"
            }
        }
        return SyncReport(pending.size, published, failures)
    }

    suspend fun openInboundSubscription(
        relay: NostrRelayClient,
        localRecipientPubkey: String,
        sinceSeconds: Long? = null
    ): String {
        relay.connectAndAwait()
        return relay.subscribe(
            filter = RelayFilter(
                kinds = listOf(NostrKinds.LEDGER_OPERATION, NostrKinds.GIFT_WRAP),
                recipientPubkey = localRecipientPubkey,
                since = sinceSeconds
            )
        )
    }

    fun inboundResults(relay: NostrRelayClient, subscriptionId: String): Flow<InboundProcessingResult> =
        relay.messages
            .filter { message ->
                message is RelayMessage.Event && message.subscriptionId == subscriptionId
            }
            .map { message -> inboundProcessor.process((message as RelayMessage.Event).event) }
}
