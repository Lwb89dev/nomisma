package com.nomisma.app.nostr

import com.nomisma.app.data.LocalLedgerRepository
import com.nomisma.app.data.OperationStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

enum class SyncConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    BACKOFF
}

data class SyncConfiguration(
    val relayUrl: String,
    val recipientPubkey: String,
    val localParticipantId: String,
    val remoteParticipantId: String
) {
    fun normalized(): SyncConfiguration {
        val cleanRelay = relayUrl.trim()
        val cleanRecipient = recipientPubkey.trim().lowercase()
        require(RelayTransportPolicy.isSupported(cleanRelay)) {
            "Usa wss:// oppure il relay LAN autorizzato ws://${RelayTransportPolicy.ALLOWED_CLEARTEXT_HOST}"
        }
        require(cleanRecipient.length == 64 && cleanRecipient.all { it in "0123456789abcdef" }) {
            "La chiave destinatario deve essere un hex di 64 caratteri"
        }
        require(localParticipantId.isNotBlank() && remoteParticipantId.isNotBlank()) {
            "Seleziona entrambi i partecipanti"
        }
        require(localParticipantId != remoteParticipantId) {
            "Il partecipante locale e quello remoto devono essere diversi"
        }
        return copy(relayUrl = cleanRelay, recipientPubkey = cleanRecipient)
    }
}

enum class LedgerCheckStatus {
    /** Not connected, or no check has run in this session yet. */
    IDLE,
    CHECKING,
    /** Both devices folded in the same operations and hold the same financial state. */
    VERIFIED,
    /** One side is missing operations; they are being sent again. */
    REPAIRING,
    /** Same operations, different state: the ledgers disagree and need attention. */
    DIVERGED
}

data class LedgerCheckState(
    val status: LedgerCheckStatus = LedgerCheckStatus.IDLE,
    val fingerprint: String? = null,
    val checkedAtMillis: Long? = null,
    val detail: String? = null
)

data class NostrSessionState(
    val status: SyncConnectionStatus = SyncConnectionStatus.DISCONNECTED,
    val identity: SignerIdentity? = null,
    val backlogComplete: Boolean = false,
    val reconnectAttempt: Int = 0,
    val isPublishing: Boolean = false,
    val isVerifyingIdentity: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val operationStats: OperationStats = OperationStats(0, 0, 0, 0, 0),
    val activeConfiguration: SyncConfiguration? = null,
    val ledgerCheck: LedgerCheckState = LedgerCheckState()
) {
    val isConnected: Boolean get() = status == SyncConnectionStatus.CONNECTED
}

data class RemoteLedgerChange(
    val result: InboundProcessingResult,
    val isBacklogReconciliation: Boolean
)

/**
 * Application-scoped Nostr session. It deliberately lives outside Compose so
 * changing tab cannot close the relay or interrupt the inbound backlog.
 */
class NostrSessionController(
    private val signer: Signer?,
    private val repository: LocalLedgerRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(NostrSessionState())
    val state: StateFlow<NostrSessionState> = _state.asStateFlow()

    private val _remoteChanges = MutableSharedFlow<RemoteLedgerChange>(extraBufferCapacity = 32)
    val remoteChanges: SharedFlow<RemoteLedgerChange> = _remoteChanges.asSharedFlow()

    private var activeRelay: NostrRelayClient? = null
    private var sessionJob: Job? = null
    private var reconnectJob: Job? = null
    private var publishingJob: Job? = null
    private var publishAgain = false
    private var checkJob: Job? = null
    private var checkRetries = 0
    private var publishRetries = 0
    private var publishRetryJob: Job? = null
    private var configuredSession: SyncConfiguration? = null
    private var intentionalDisconnect = true

    init {
        refreshStats()
    }

    fun verifyIdentity() {
        if (_state.value.isVerifyingIdentity) return
        val activeSigner = signer
        if (activeSigner == null) {
            update { it.copy(error = "Signer Amber non configurato") }
            return
        }
        update { it.copy(isVerifyingIdentity = true, message = "Apertura di Amber…", error = null) }
        scope.launch {
            runCatching { activeSigner.refreshIdentity() }
                .onSuccess { identity ->
                    update {
                        it.copy(
                            identity = identity,
                            isVerifyingIdentity = false,
                            error = null,
                            message = "Identità Amber verificata"
                        )
                    }
                }
                .onFailure { failure ->
                    update {
                        it.copy(
                            isVerifyingIdentity = false,
                            error = failure.message ?: "Amber non disponibile"
                        )
                    }
                }
        }
    }

    fun connect(configuration: SyncConfiguration) {
        val normalized = runCatching(configuration::normalized).getOrElse { failure ->
            update { it.copy(error = failure.message ?: "Configurazione Nostr non valida") }
            return
        }
        if (signer == null) {
            update { it.copy(error = "Signer Amber non configurato") }
            return
        }
        if (configuredSession == normalized && sessionJob?.isActive == true) return

        intentionalDisconnect = false
        configuredSession = normalized
        reconnectJob?.cancel()
        reconnectJob = null
        sessionJob?.cancel()
        activeRelay?.close()
        activeRelay = null
        update {
            it.copy(
                status = SyncConnectionStatus.CONNECTING,
                backlogComplete = false,
                reconnectAttempt = 0,
                message = "Connessione al relay…",
                error = null,
                activeConfiguration = normalized
            )
        }
        launchSession(normalized)
    }

    fun disconnect() {
        intentionalDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        publishingJob?.cancel()
        publishingJob = null
        checkJob?.cancel()
        checkJob = null
        publishRetryJob?.cancel()
        publishRetryJob = null
        sessionJob?.cancel()
        sessionJob = null
        activeRelay?.close()
        activeRelay = null
        configuredSession = null
        update {
            it.copy(
                status = SyncConnectionStatus.DISCONNECTED,
                backlogComplete = false,
                reconnectAttempt = 0,
                isPublishing = false,
                isVerifyingIdentity = false,
                message = "Relay disconnesso",
                error = null,
                activeConfiguration = null,
                ledgerCheck = LedgerCheckState()
            )
        }
    }

    /** Manual "sync now"; the same work also runs by itself after every change. */
    fun publishPending() {
        if (!_state.value.isConnected) {
            update { it.copy(error = "Connetti il relay prima di pubblicare l'outbox") }
            return
        }
        requestPublish()
    }

    /** A local change was saved: send it as soon as the relay is reachable. */
    fun onLocalChange() = requestPublish()

    private fun requestPublish() {
        if (!_state.value.isConnected || activeRelay == null || configuredSession == null || signer == null) return
        if (publishingJob?.isActive == true) {
            publishAgain = true
            return
        }
        publishingJob = scope.launch {
            update { it.copy(isPublishing = true) }
            try {
                do {
                    publishAgain = false
                    publishOutboxOnce()
                } while (publishAgain)
            } finally {
                update { it.copy(isPublishing = false) }
                refreshStats()
            }
            if (repository.pendingOperations().isEmpty()) {
                publishRetries = 0
                scheduleCheck()
            } else {
                retryPublishLater()
            }
        }
    }

    /** Relays rate-limit and drop: whatever is left in the outbox is tried again, more slowly each time. */
    private fun retryPublishLater() {
        publishRetryJob?.cancel()
        publishRetryJob = scope.launch {
            delay(RelayReconnectPolicy.delayMillis(++publishRetries).coerceAtLeast(MIN_RETRY_MILLIS))
            requestPublish()
        }
    }

    private suspend fun publishOutboxOnce() {
        val activeSigner = signer ?: return
        val relay = activeRelay ?: return
        val configuration = configuredSession ?: return
        try {
            val report = NostrSyncEngine(
                localRepository = repository,
                publisher = NostrOperationPublisher(activeSigner, relay, configuration.localParticipantId),
                inboundProcessor = inboundProcessor(activeSigner, configuration)
            ).publishPending(configuration.recipientPubkey)
            if (report.attempted > 0) {
                update {
                    it.copy(
                        message = "Outbox: ${report.published}/${report.attempted} pubblicate",
                        error = report.failures.firstOrNull()
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            update { it.copy(error = failure.message ?: "Sincronizzazione non riuscita") }
        }
    }

    /**
     * Asks the partner to compare ledgers once things have settled. Debounced, so a burst of
     * changes or incoming operations ends up as a single check.
     */
    private fun scheduleCheck(delayMillis: Long = CHECK_DEBOUNCE_MILLIS) {
        checkJob?.cancel()
        checkJob = scope.launch {
            delay(delayMillis)
            sendCheckpoint(reply = false)
        }
    }

    private suspend fun sendCheckpoint(reply: Boolean) {
        val activeSigner = signer ?: return
        val relay = activeRelay ?: return
        val configuration = configuredSession ?: return
        if (!_state.value.isConnected || !_state.value.backlogComplete) return
        try {
            val digest = repository.ledgerDigest(configuration.localParticipantId, reply)
            NostrOperationPublisher(activeSigner, relay, configuration.localParticipantId)
                .publishCheckpoint(digest, configuration.recipientPubkey)
            checkRetries = 0
            if (!reply) updateCheck { current ->
                // A verified state stays verified until the partner says otherwise.
                if (current.status == LedgerCheckStatus.VERIFIED && current.fingerprint == digest.fingerprint) current
                else LedgerCheckState(LedgerCheckStatus.CHECKING, digest.fingerprint, current.checkedAtMillis)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            updateCheck { it.copy(detail = failure.message ?: "Verifica non inviata") }
            // A lost reply would leave the partner waiting, so any failure becomes a fresh check later.
            if (checkRetries < MAX_CHECK_RETRIES) {
                scheduleCheck(RelayReconnectPolicy.delayMillis(++checkRetries).coerceAtLeast(MIN_RETRY_MILLIS))
            }
        }
    }

    /**
     * The partner's view of the ledger arrived. Compare, repair what is ours to repair, and answer
     * with our own view so the partner reaches the same conclusion: the check is two-way.
     */
    private suspend fun onPartnerCheckpoint(remote: LedgerDigest) {
        val configuration = configuredSession ?: return
        if (System.currentTimeMillis() - remote.sentAtMillis > MAX_CHECKPOINT_AGE_MILLIS) return
        val local = repository.ledgerDigest(configuration.localParticipantId, reply = true)
        val now = System.currentTimeMillis()
        when (val verdict = LedgerConsistency.compare(local, remote)) {
            is ConsistencyVerdict.Verified ->
                updateCheck { LedgerCheckState(LedgerCheckStatus.VERIFIED, verdict.fingerprint, now) }
            is ConsistencyVerdict.Diverged -> updateCheck {
                LedgerCheckState(
                    LedgerCheckStatus.DIVERGED,
                    local.fingerprint,
                    now,
                    "Impronte ${local.fingerprint} / ${remote.fingerprint} · cicli ${verdict.cycleIds.joinToString()}"
                )
            }
            is ConsistencyVerdict.Syncing -> {
                val resent = verdict.resendAfter
                    ?.let { repository.requeueOwnOperationsAfter(configuration.localParticipantId, it) } ?: 0
                if (resent > 0) {
                    refreshStats()
                    requestPublish()
                }
                updateCheck {
                    LedgerCheckState(
                        if (resent > 0) LedgerCheckStatus.REPAIRING else LedgerCheckStatus.CHECKING,
                        local.fingerprint,
                        it.checkedAtMillis,
                        if (resent > 0) "$resent operazioni reinviate" else null
                    )
                }
            }
            ConsistencyVerdict.OtherLedger -> updateCheck {
                LedgerCheckState(LedgerCheckStatus.DIVERGED, local.fingerprint, now, "L'altro dispositivo usa un altro ledger")
            }
        }
        if (!remote.reply) sendCheckpoint(reply = true)
    }

    private inline fun updateCheck(transform: (LedgerCheckState) -> LedgerCheckState) {
        update { it.copy(ledgerCheck = transform(it.ledgerCheck)) }
    }

    fun refreshStats() {
        scope.launch {
            runCatching { repository.operationStats() }
                .onSuccess { stats -> update { it.copy(operationStats = stats) } }
        }
    }

    fun close() = disconnect()

    private fun launchSession(configuration: SyncConfiguration) {
        sessionJob = scope.launch {
            val activeSigner = signer ?: return@launch
            val relay = runCatching { NostrRelayClient(configuration.relayUrl) }
                .getOrElse { failure ->
                    handleConnectionLoss(failure.message ?: "URL relay non valido")
                    return@launch
                }
            activeRelay = relay
            var subscriptionId: String? = null
            var subscriptionFilter: RelayFilter? = null
            var answeredChallenge: String? = null
            var processor: NostrInboundProcessor? = null
            var backlogHasChanges = false
            val collector = launch {
                relay.messages.collect { message ->
                    when (message) {
                        RelayMessage.Connected -> update {
                            it.copy(
                                status = SyncConnectionStatus.CONNECTED,
                                backlogComplete = false,
                                message = "Connesso · recupero storico relay",
                                error = null
                            )
                        }
                        RelayMessage.Closed -> handleConnectionLoss("Connessione relay chiusa")
                        is RelayMessage.Failure -> handleConnectionLoss(message.message)
                        is RelayMessage.ProtocolError -> update { it.copy(error = message.message) }
                        is RelayMessage.Event -> {
                            if (message.subscriptionId == subscriptionId) {
                                processor?.let {
                                    backlogHasChanges = processInbound(it, message.event) || backlogHasChanges
                                }
                            }
                        }
                        is RelayMessage.EndOfStoredEvents -> {
                            if (message.subscriptionId == subscriptionId) {
                                update {
                                    it.copy(
                                        status = SyncConnectionStatus.CONNECTED,
                                        backlogComplete = true,
                                        reconnectAttempt = 0,
                                        message = "Storico relay allineato (EOSE)"
                                    )
                                }
                                requestPublish()
                                scheduleCheck()
                                if (backlogHasChanges) {
                                    _remoteChanges.emit(
                                        RemoteLedgerChange(
                                            result = InboundProcessingResult(
                                                status = InboundProcessingStatus.APPLIED,
                                                message = "Storico relay riconciliato"
                                            ),
                                            isBacklogReconciliation = true
                                        )
                                    )
                                    backlogHasChanges = false
                                }
                            }
                        }
                        is RelayMessage.Notice -> update { it.copy(message = "Relay: ${message.message}") }
                        is RelayMessage.Ack -> Unit
                        is RelayMessage.AuthChallenge -> if (message.challenge != answeredChallenge) {
                            answeredChallenge = message.challenge
                            launch {
                                answerChallenge(activeSigner, relay, configuration.relayUrl, message.challenge) {
                                    subscriptionId?.let { id -> subscriptionFilter?.let { relay.subscribe(id, it) } }
                                }
                            }
                        }
                        is RelayMessage.SubscriptionClosed -> {
                            val waitingForAuth = message.message.startsWith("auth-required")
                            if (message.subscriptionId == subscriptionId && !waitingForAuth) {
                                update { it.copy(error = "Relay: ${message.message}") }
                            }
                        }
                    }
                }
            }

            try {
                update {
                    it.copy(
                        status = SyncConnectionStatus.CONNECTING,
                        backlogComplete = false,
                        message = "Connessione al relay…",
                        error = null
                    )
                }
                val identity = activeSigner.getIdentity()
                require(identity.pubkey != configuration.recipientPubkey) {
                    "La chiave destinataria deve essere diversa da quella locale"
                }
                processor = inboundProcessor(activeSigner, configuration, identity)
                update { it.copy(identity = identity) }
                if (repository.requeueForSyncTarget(configuration.relayUrl, configuration.recipientPubkey) > 0) {
                    refreshStats()
                }
                relay.connectAndAwait()
                val filter = RelayFilter(
                    kinds = listOf(NostrKinds.LEDGER_OPERATION, NostrKinds.GIFT_WRAP, NostrKinds.EPHEMERAL_GIFT_WRAP),
                    recipientPubkey = identity.pubkey
                )
                subscriptionFilter = filter
                subscriptionId = relay.subscribe(filter = filter)
                update {
                    it.copy(
                        status = SyncConnectionStatus.CONNECTED,
                        message = "In ascolto sul relay",
                        error = null
                    )
                }
                awaitCancellation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                handleConnectionLoss(failure.message ?: "Connessione relay non riuscita")
            } finally {
                collector.cancel()
                relay.close()
                if (activeRelay === relay) activeRelay = null
            }
        }
    }

    /**
     * NIP-42: some relays serve gift wraps only to the key they are addressed to. The challenge
     * is signed with the user's key and, once accepted, [onAuthenticated] repeats the REQ that
     * the relay closed with "auth-required".
     */
    private suspend fun answerChallenge(
        activeSigner: Signer,
        relay: NostrRelayClient,
        relayUrl: String,
        challenge: String,
        onAuthenticated: () -> Unit
    ) {
        try {
            val now = System.currentTimeMillis() / 1000L
            relay.authenticate(activeSigner.signEvent(RelayAuth.unsignedEvent(relayUrl, challenge, now)))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            update { it.copy(error = "Autenticazione relay non riuscita: ${failure.message ?: "errore sconosciuto"}") }
            return
        }
        onAuthenticated()
    }

    /** Returns true when the materialized ledger changed during backlog replay. */
    private suspend fun processInbound(processor: NostrInboundProcessor, event: NostrEvent): Boolean {
        val result = processor.process(event)
        var backlogChanged = false
        when (result.status) {
            // Checkpoints replayed from the relay's history describe states that are gone.
            InboundProcessingStatus.CHECKPOINT -> if (_state.value.backlogComplete) {
                result.checkpoint?.let { onPartnerCheckpoint(it) }
            }
            InboundProcessingStatus.APPLIED -> {
                update { it.copy(message = result.message?.takeIf(String::isNotBlank) ?: "Operazione ricevuta e applicata") }
                if (_state.value.backlogComplete) {
                    scheduleCheck()
                    _remoteChanges.emit(
                        RemoteLedgerChange(
                            result = result,
                            isBacklogReconciliation = false
                        )
                    )
                } else {
                    backlogChanged = true
                }
            }
            InboundProcessingStatus.DEFERRED -> update { it.copy(message = "Operazione in attesa della sequenza precedente") }
            InboundProcessingStatus.DUPLICATE -> update { it.copy(message = "Operazione già presente") }
            InboundProcessingStatus.REJECTED -> update { it.copy(message = "Operazione rifiutata: ${result.message}") }
            InboundProcessingStatus.IGNORED -> Unit
        }
        refreshStats()
        return backlogChanged
    }

    private suspend fun inboundProcessor(
        activeSigner: Signer,
        configuration: SyncConfiguration,
        knownIdentity: SignerIdentity? = null
    ): NostrInboundProcessor {
        val identity = knownIdentity ?: activeSigner.getIdentity()
        val expectedAuthors = mapOf(
            identity.pubkey to configuration.localParticipantId,
            configuration.recipientPubkey to configuration.remoteParticipantId
        )
        return NostrInboundProcessor(activeSigner, repository, expectedAuthors)
    }

    private fun handleConnectionLoss(reason: String) {
        if (intentionalDisconnect || configuredSession == null) return
        val attempt = (_state.value.reconnectAttempt + 1).coerceAtMost(1_000)
        val retryDelayMillis = RelayReconnectPolicy.delayMillis(attempt)
        update {
            it.copy(
                status = SyncConnectionStatus.BACKOFF,
                backlogComplete = false,
                reconnectAttempt = attempt,
                message = "Connessione persa · nuovo tentativo tra ${retryDelayMillis / 1_000}s",
                error = reason
            )
        }
        sessionJob?.cancel()
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(retryDelayMillis)
            val configuration = configuredSession
            if (!intentionalDisconnect && configuration != null) {
                reconnectJob = null
                launchSession(configuration)
            }
        }
    }

    private inline fun update(transform: (NostrSessionState) -> NostrSessionState) {
        _state.value = transform(_state.value)
    }

    private companion object {
        const val CHECK_DEBOUNCE_MILLIS = 2_500L
        const val MIN_RETRY_MILLIS = 5_000L
        const val MAX_CHECK_RETRIES = 6
        /** Checkpoints are live-only; an old one describes a state that no longer exists. */
        const val MAX_CHECKPOINT_AGE_MILLIS = 10 * 60 * 1_000L
    }
}
