package com.nomisma.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nomisma.app.data.LocalLedgerRepository
import com.nomisma.app.domain.AnalyticsCalculator
import com.nomisma.app.domain.Expense
import com.nomisma.app.domain.ExpenseFilter
import com.nomisma.app.domain.LedgerSnapshot
import com.nomisma.app.domain.SettlementMethod
import com.nomisma.app.nostr.NostrSessionController
import com.nomisma.app.nostr.NostrSessionState
import com.nomisma.app.nostr.Signer
import com.nomisma.app.nostr.SyncConfiguration
import com.nomisma.app.notifications.ExpenseNotificationPolicy
import com.nomisma.app.notifications.ExpenseNotifier
import com.nomisma.app.theme.ThemeRegistry
import com.nomisma.app.theme.ThemeEventCoordinator
import com.nomisma.app.theme.ThemeEventOrigin
import com.nomisma.app.theme.ThemeEventQueueState
import com.nomisma.app.theme.ThemeStateMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NomismaViewModel(
    private val repository: LocalLedgerRepository,
    signer: Signer?,
    private val expenseNotifier: ExpenseNotifier? = null
) : ViewModel() {
    private val syncController = NostrSessionController(signer, repository, viewModelScope)
    private val repositoryMutex = Mutex()
    private var visibleWorkCount = 0
    private val themeEventCoordinator = ThemeEventCoordinator()

    private val _snapshot = MutableStateFlow(LedgerSnapshot.empty())
    val snapshot: StateFlow<LedgerSnapshot> = _snapshot.asStateFlow()

    private val _themeEvents = MutableStateFlow(ThemeEventQueueState())
    val themeEvents: StateFlow<ThemeEventQueueState> = _themeEvents.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()
    val syncState: StateFlow<NostrSessionState> = syncController.state

    init {
        refresh()
        viewModelScope.launch {
            syncController.remoteChanges.collect { change ->
                reloadSnapshot(
                    showBusy = false,
                    origin = if (change.isBacklogReconciliation) {
                        ThemeEventOrigin.RECONCILIATION
                    } else {
                        ThemeEventOrigin.REMOTE
                    },
                    reconcile = change.isBacklogReconciliation
                )
            }
        }
    }

    fun refresh() {
        mutate { repository.loadSnapshot() }
    }

    fun connectSync(configuration: SyncConfiguration) = syncController.connect(configuration)

    fun disconnectSync() = syncController.disconnect()

    fun publishPendingOperations() = syncController.publishPending()

    fun verifySignerIdentity() = syncController.verifyIdentity()

    fun addExpense(
        amountMinor: Long,
        description: String,
        categoryId: String,
        tags: List<String>,
        merchant: String,
        notes: String,
        occurredAtMillis: Long,
        payerId: String,
        operationAuthorId: String,
        sharedEvenly: Boolean = true
    ) {
        mutate {
            repository.addExpense(
                amountMinor,
                description,
                categoryId,
                tags,
                merchant,
                notes,
                occurredAtMillis,
                payerId,
                operationAuthorId,
                sharedEvenly
            )
        }
    }

    fun updateExpense(
        expense: Expense,
        amountMinor: Long,
        description: String,
        categoryId: String,
        tags: List<String>,
        merchant: String,
        notes: String,
        occurredAtMillis: Long,
        payerId: String,
        operationAuthorId: String,
        sharedEvenly: Boolean = true
    ) {
        mutate {
            repository.updateExpense(
                expenseId = expense.id,
                amountMinor = amountMinor,
                description = description,
                categoryId = categoryId,
                tags = tags,
                merchant = merchant,
                notes = notes,
                occurredAtMillis = occurredAtMillis,
                payerId = payerId,
                editorAuthorId = operationAuthorId,
                sharedEvenly = sharedEvenly
            )
        }
    }

    fun deleteExpense(expense: Expense, operationAuthorId: String) {
        mutate { repository.deleteExpense(expense.id, operationAuthorId) }
    }

    fun createTag(name: String, operationAuthorId: String) {
        mutate { repository.createTag(name, operationAuthorId) }
    }

    fun renameTag(tagId: String, newName: String, operationAuthorId: String) {
        mutate { repository.renameTag(tagId, newName, operationAuthorId) }
    }

    fun archiveTag(tagId: String, operationAuthorId: String) {
        mutate { repository.archiveTag(tagId, operationAuthorId) }
    }

    fun createCategory(name: String, operationAuthorId: String) {
        mutate { repository.createCategory(name, operationAuthorId) }
    }

    fun renameCategory(categoryId: String, newName: String, operationAuthorId: String) {
        mutate { repository.renameCategory(categoryId, newName, operationAuthorId) }
    }

    fun archiveCategory(categoryId: String, operationAuthorId: String) {
        mutate { repository.archiveCategory(categoryId, operationAuthorId) }
    }

    fun settleCurrentCycle(
        operationAuthorId: String,
        method: SettlementMethod,
        note: String
    ) {
        mutate { repository.settleCurrentCycle(operationAuthorId, method, note) }
    }

    fun selectTheme(themeId: String, operationAuthorId: String) {
        if (!ThemeRegistry.contains(themeId)) return
        mutate { repository.changeTheme(themeId, operationAuthorId) }
    }

    fun changeCapacity(capacityMinor: Long, operationAuthorId: String) {
        mutate { repository.changeCapacity(capacityMinor, operationAuthorId) }
    }

    fun changeLedgerMetadata(title: String, currency: String, operationAuthorId: String) {
        mutate { repository.changeLedgerMetadata(title, currency, operationAuthorId) }
    }

    fun changeParticipantThemeIdentity(
        participantId: String,
        identityId: String,
        operationAuthorId: String
    ) {
        mutate {
            repository.changeParticipantThemeIdentity(participantId, identityId, operationAuthorId)
        }
    }

    fun changeParticipantColor(
        participantId: String,
        colorHex: String,
        operationAuthorId: String
    ) {
        mutate {
            repository.changeParticipantColor(participantId, colorHex, operationAuthorId)
        }
    }

    fun changeParticipantName(
        participantId: String,
        name: String,
        operationAuthorId: String
    ) {
        mutate {
            repository.changeParticipantName(participantId, name, operationAuthorId)
        }
    }

    fun adoptPairingLedger(ledgerId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            beginVisibleWork()
            try {
                val previous = _snapshot.value
                val current = repositoryMutex.withLock {
                    repository.adoptPairingLedger(ledgerId)
                }
                publishSnapshotTransition(previous, current, ThemeEventOrigin.RECONCILIATION, reconcile = true)
                onSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _uiMessage.value = failure.message ?: "Impossibile adottare il ledger dell'invito"
            } finally {
                endVisibleWork()
                syncController.refreshStats()
            }
        }
    }

    fun consumeMessage() {
        _uiMessage.value = null
    }

    fun consumeThemeEvent(eventId: String) {
        _themeEvents.value = themeEventCoordinator.consume(eventId)
    }

    fun reconcileStaleThemeEvents() {
        val current = ThemeStateMapper.map(_snapshot.value)
        _themeEvents.value = themeEventCoordinator.collapseToReconciliation(current)
    }

    private fun mutate(block: suspend () -> LedgerSnapshot) {
        viewModelScope.launch {
            beginVisibleWork()
            try {
                val previous = _snapshot.value
                val current = repositoryMutex.withLock { block() }
                publishSnapshotTransition(previous, current, ThemeEventOrigin.LOCAL)
                syncController.onLocalChange()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _uiMessage.value = failure.message ?: "Operazione non riuscita"
            } finally {
                endVisibleWork()
                syncController.refreshStats()
            }
        }
    }

    private fun reloadSnapshot(
        showBusy: Boolean,
        origin: ThemeEventOrigin = ThemeEventOrigin.LOCAL,
        reconcile: Boolean = false
    ) {
        viewModelScope.launch {
            if (showBusy) beginVisibleWork()
            try {
                val previous = _snapshot.value
                val current = repositoryMutex.withLock { repository.loadSnapshot() }
                if (origin != ThemeEventOrigin.LOCAL) {
                    expenseNotifier?.notifyNewExpenses(
                        ExpenseNotificationPolicy.addedExpenses(previous, current),
                        current
                    )
                }
                publishSnapshotTransition(previous, current, origin, reconcile)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _uiMessage.value = failure.message ?: "Aggiornamento non riuscito"
            } finally {
                if (showBusy) endVisibleWork()
            }
        }
    }

    private fun publishSnapshotTransition(
        previous: LedgerSnapshot,
        current: LedgerSnapshot,
        origin: ThemeEventOrigin,
        reconcile: Boolean = false
    ) {
        _snapshot.value = current
        _themeEvents.value = themeEventCoordinator.accept(
            previous = ThemeStateMapper.map(previous),
            current = ThemeStateMapper.map(current),
            origin = origin,
            reconcile = reconcile
        )
    }

    private fun beginVisibleWork() {
        visibleWorkCount += 1
        _isBusy.value = true
    }

    private fun endVisibleWork() {
        visibleWorkCount = (visibleWorkCount - 1).coerceAtLeast(0)
        _isBusy.value = visibleWorkCount > 0
    }

    fun analytics(filter: ExpenseFilter = ExpenseFilter()) =
        AnalyticsCalculator.calculate(_snapshot.value, filter)

    fun themeState() = ThemeStateMapper.map(_snapshot.value)

    override fun onCleared() {
        syncController.close()
        super.onCleared()
    }

    class Factory(
        private val repository: LocalLedgerRepository,
        private val signer: Signer?,
        private val expenseNotifier: ExpenseNotifier? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(NomismaViewModel::class.java))
            return NomismaViewModel(repository, signer, expenseNotifier) as T
        }
    }
}
