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
    val activeConfiguration: SyncConfiguration? = null
) {
    val isConnected: Boolean get() = status == SyncConnectionStatus.CONNECTED
}

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

    private val _remoteChanges = MutableSharedFlow<InboundProcessingResult>(extraBufferCapacity = 32)
    val remoteChanges: SharedFlow<InboundProcessingResult> = _remoteChanges.asSharedFlow()

    private var activeRelay: NostrRelayClient? = null
    private var sessionJob: Job? = null
    private var reconnectJob: Job? = null
    private var publishingJob: Job? = null
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
                activeConfiguration = null
            )
        }
    }

    fun publishPending() {
        if (publishingJob?.isActive == true) return
        val activeSigner = signer
        val relay = activeRelay
        val configuration = configuredSession
        if (activeSigner == null || relay == null || configuration == null || !_state.value.isConnected) {
            update { it.copy(error = "Connetti il relay prima di pubblicare l'outbox") }
            return
        }
        publishingJob = scope.launch {
            update { it.copy(isPublishing = true, error = null) }
            try {
                val processor = inboundProcessor(activeSigner, configuration)
                val report = NostrSyncEngine(
                    localRepository = repository,
                    publisher = NostrOperationPublisher(activeSigner, relay, configuration.localParticipantId),
                    inboundProcessor = processor
                ).publishPending(configuration.recipientPubkey)
                update {
                    it.copy(
                        message = "Outbox: ${report.published}/${report.attempted} pubblicate",
                        error = report.failures.firstOrNull()
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                update { it.copy(error = failure.message ?: "Sincronizzazione non riuscita") }
            } finally {
                update { it.copy(isPublishing = false) }
                refreshStats()
            }
        }
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
                                if (backlogHasChanges) {
                                    _remoteChanges.emit(
                                        InboundProcessingResult(
                                            status = InboundProcessingStatus.APPLIED,
                                            message = "Storico relay riconciliato"
                                        )
                                    )
                                    backlogHasChanges = false
                                }
                            }
                        }
                        is RelayMessage.Notice -> update { it.copy(message = "Relay: ${message.message}") }
                        is RelayMessage.Ack -> Unit
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
                relay.connectAndAwait()
                subscriptionId = relay.subscribe(
                    filter = RelayFilter(
                        kinds = listOf(NostrKinds.LEDGER_OPERATION, NostrKinds.GIFT_WRAP),
                        recipientPubkey = identity.pubkey
                    )
                )
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

    /** Returns true when the materialized ledger changed during backlog replay. */
    private suspend fun processInbound(processor: NostrInboundProcessor, event: NostrEvent): Boolean {
        val result = processor.process(event)
        var backlogChanged = false
        when (result.status) {
            InboundProcessingStatus.APPLIED -> {
                update { it.copy(message = result.message?.takeIf(String::isNotBlank) ?: "Operazione ricevuta e applicata") }
                if (_state.value.backlogComplete) {
                    _remoteChanges.emit(result)
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
}
