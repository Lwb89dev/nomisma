package com.nomisma.app.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeEventCoordinatorTest {
    @Test
    fun backlogAlwaysProducesExactlyOneReconciliation() {
        val coordinator = ThemeEventCoordinator()

        val result = coordinator.accept(
            previous = state(volume = 1_000L, fill = 0.1f, contributionId = "old"),
            current = state(volume = 8_000L, fill = 0.8f, contributionId = "latest"),
            origin = ThemeEventOrigin.RECONCILIATION,
            reconcile = true,
            nowMillis = 42L
        )

        assertEquals(1, result.pending.size)
        assertTrue(result.pending.single().event is ThemePresentationEvent.Reconciled)
        assertEquals(ThemeEventOrigin.RECONCILIATION, result.pending.single().origin)
    }

    @Test
    fun moreThanThreeLiveContributionsCollapseIntoReconciliation() {
        val coordinator = ThemeEventCoordinator(maxLiveContributions = 3)
        var previous = state(volume = 0L, fill = 0f, contributionId = null)

        repeat(4) { index ->
            val current = state(
                volume = (index + 1) * 1_000L,
                fill = (index + 1) / 10f,
                contributionId = "expense-$index"
            )
            coordinator.accept(
                previous = previous,
                current = current,
                origin = ThemeEventOrigin.REMOTE,
                nowMillis = index.toLong()
            )
            previous = current
        }

        assertEquals(1, coordinator.state.pending.size)
        assertTrue(coordinator.state.pending.single().event is ThemePresentationEvent.Reconciled)
    }

    @Test
    fun consumingAnEventAdvancesTheDurableCursor() {
        val coordinator = ThemeEventCoordinator()
        val result = coordinator.accept(
            previous = state(volume = 0L, fill = 0f, contributionId = null),
            current = state(volume = 1_000L, fill = 0.1f, contributionId = "expense"),
            origin = ThemeEventOrigin.LOCAL,
            nowMillis = 7L
        )
        val eventId = result.pending.first().id

        val consumed = coordinator.consume(eventId)

        assertTrue(consumed.pending.none { it.id == eventId })
        assertEquals(eventId, consumed.lastConsumedEventId)
    }

    @Test
    fun contributionCarriesItsOwnFillIntervalAndParticipant() {
        val coordinator = ThemeEventCoordinator()
        val result = coordinator.accept(
            previous = state(volume = 2_000L, fill = 0.2f, contributionId = "old"),
            current = state(volume = 4_500L, fill = 0.45f, contributionId = "new"),
            origin = ThemeEventOrigin.LOCAL,
            nowMillis = 9L
        )

        val event = result.pending.first().event as ThemePresentationEvent.ContributionAdded

        assertEquals(0.2f, event.fromFill)
        assertEquals(0.45f, event.targetFill)
        assertEquals("alice", event.contribution.participantId)
    }

    private fun state(
        volume: Long,
        fill: Float,
        contributionId: String?
    ) = ThemeSceneState(
        ledgerId = "ledger",
        themeId = ThemeRegistry.VESSEL,
        cycleId = "cycle",
        completedCycleCount = 0,
        participantA = ThemeParticipantState("alice", "Alice", "#38E66B", "bear"),
        participantB = ThemeParticipantState("bob", "Bob", "#147DFF", "squirrel"),
        participantAContributionMinor = volume,
        participantBContributionMinor = 0L,
        cycleVolumeMinor = volume,
        capacityMinor = 10_000L,
        normalizedFill = fill,
        overflowRatio = 0f,
        netBalanceMinor = volume / 2,
        creditorId = if (volume == 0L) null else "alice",
        latestContribution = contributionId?.let {
            ThemeContribution(it, "alice", 1_000L, volume)
        }
    )
}
