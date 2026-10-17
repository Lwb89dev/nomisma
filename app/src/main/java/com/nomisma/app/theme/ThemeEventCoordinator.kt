package com.nomisma.app.theme

/**
 * ViewModel-owned semantic event queue. It survives Compose recreation and
 * collapses bursts/backlog into a single state reconciliation.
 */
class ThemeEventCoordinator(
    private val maxLiveContributions: Int = 3
) {
    var state: ThemeEventQueueState = ThemeEventQueueState()
        private set

    private var sequence = 0L

    fun accept(
        previous: ThemeSceneState?,
        current: ThemeSceneState,
        origin: ThemeEventOrigin,
        reconcile: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): ThemeEventQueueState {
        if (previous == null || previous.ledgerId != current.ledgerId) {
            state = ThemeEventQueueState(lastConsumedEventId = state.lastConsumedEventId)
            return state
        }

        if (reconcile || origin == ThemeEventOrigin.RECONCILIATION) {
            state = state.copy(
                pending = listOf(
                    envelope(
                        current,
                        ThemeEventOrigin.RECONCILIATION,
                        nowMillis,
                        ThemePresentationEvent.Reconciled(
                            fromFill = previous.normalizedFill,
                            targetFill = current.normalizedFill,
                            cycleVolumeMinor = current.cycleVolumeMinor
                        )
                    )
                )
            )
            return state
        }

        val resolved = ThemePresentationEventResolver.resolve(
            previous = previous,
            current = current,
            isRemote = origin == ThemeEventOrigin.REMOTE
        )
        if (resolved.isEmpty()) return state

        val settlement = resolved.firstOrNull { it is ThemePresentationEvent.SettlementCompleted }
        val themeChange = resolved.firstOrNull { it is ThemePresentationEvent.ThemeChanged }
        val events = when {
            settlement != null -> listOf(settlement)
            themeChange != null -> listOf(themeChange)
            else -> resolved
        }
        val additions = events.map { envelope(current, origin, nowMillis, it) }
        val candidate = state.pending + additions
        val liveContributionCount = candidate.count {
            it.event is ThemePresentationEvent.ContributionAdded
        }
        state = if (liveContributionCount > maxLiveContributions) {
            state.copy(
                pending = listOf(
                    envelope(
                        current,
                        ThemeEventOrigin.RECONCILIATION,
                        nowMillis,
                        ThemePresentationEvent.Reconciled(
                            fromFill = previous.normalizedFill,
                            targetFill = current.normalizedFill,
                            cycleVolumeMinor = current.cycleVolumeMinor
                        )
                    )
                )
            )
        } else {
            state.copy(pending = candidate)
        }
        return state
    }

    fun consume(eventId: String): ThemeEventQueueState {
        if (state.pending.none { it.id == eventId }) return state
        state = state.copy(
            pending = state.pending.filterNot { it.id == eventId },
            lastConsumedEventId = eventId
        )
        return state
    }

    fun collapseToReconciliation(
        current: ThemeSceneState,
        nowMillis: Long = System.currentTimeMillis()
    ): ThemeEventQueueState {
        if (state.pending.isEmpty()) return state
        state = state.copy(
            pending = listOf(
                envelope(
                    current,
                    ThemeEventOrigin.RECONCILIATION,
                    nowMillis,
                    ThemePresentationEvent.Reconciled(
                        fromFill = current.normalizedFill,
                        targetFill = current.normalizedFill,
                        cycleVolumeMinor = current.cycleVolumeMinor
                    )
                )
            )
        )
        return state
    }

    private fun envelope(
        current: ThemeSceneState,
        origin: ThemeEventOrigin,
        nowMillis: Long,
        event: ThemePresentationEvent
    ): ThemeEventEnvelope {
        sequence += 1
        val semanticId = when (event) {
            is ThemePresentationEvent.ContributionAdded -> event.contribution.id
            is ThemePresentationEvent.SettlementCompleted -> event.settlementId
            is ThemePresentationEvent.ThemeChanged -> event.themeId
            is ThemePresentationEvent.ParticipantThemeIdentityChanged -> event.participantId
            is ThemePresentationEvent.ParticipantColorChanged -> event.participantId
            is ThemePresentationEvent.ParticipantNameChanged -> event.participantId
            is ThemePresentationEvent.BalanceChanged -> event.creditorId ?: "balanced"
            is ThemePresentationEvent.Reconciled -> "${event.cycleVolumeMinor}"
            ThemePresentationEvent.CapacityReached -> "capacity"
            ThemePresentationEvent.CapacityExceeded -> "overflow"
        }
        return ThemeEventEnvelope(
            id = "${current.ledgerId}:${origin.name.lowercase()}:$semanticId:$sequence",
            ledgerId = current.ledgerId,
            origin = origin,
            createdAtMillis = nowMillis,
            event = event
        )
    }
}
