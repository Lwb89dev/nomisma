package com.nomisma.app.theme

import com.nomisma.app.domain.LedgerSnapshot

data class ThemeDefinition(
    val id: String,
    val displayName: String,
    val version: Int,
    val description: String
)

data class ThemeParticipantState(
    val id: String,
    val name: String,
    val colorHex: String,
    val identityId: String
)

data class ThemeIdentityDefinition(
    val id: String,
    val displayName: String,
    val symbol: String,
    val contributionMaterial: String
)

object ThemeIdentityRegistry {
    val identities = listOf(
        ThemeIdentityDefinition("bear", "Orso", "🐻", "Miele"),
        ThemeIdentityDefinition("squirrel", "Scoiattolo", "🐿", "Ghiande"),
        ThemeIdentityDefinition("fox", "Volpe", "🦊", "Bacche"),
        ThemeIdentityDefinition("rabbit", "Coniglio", "🐇", "Carote"),
        ThemeIdentityDefinition("raccoon", "Procione", "🦝", "Gettoni"),
        ThemeIdentityDefinition("hedgehog", "Riccio", "🦔", "Castagne"),
        ThemeIdentityDefinition("owl", "Gufo", "🦉", "Semi"),
        ThemeIdentityDefinition("otter", "Lontra", "🦦", "Conchiglie")
    )

    fun contains(id: String): Boolean = identities.any { it.id == id }
    fun symbol(id: String): String = identities.firstOrNull { it.id == id }?.symbol ?: "●"
}

object ParticipantColorPalette {
    val colors = listOf(
        "#38E66B",
        "#147DFF",
        "#FFB547",
        "#FF6685",
        "#A97BFF",
        "#2ED3C6",
        "#F06CFF",
        "#E5E968"
    )

    fun normalize(raw: String): String? {
        val value = raw.trim().uppercase().let { if (it.startsWith('#')) it else "#$it" }
        return value.takeIf { it.length == 7 && it.drop(1).all { character -> character in "0123456789ABCDEF" } }
    }
}

data class ThemeContribution(
    val id: String,
    val participantId: String,
    val amountMinor: Long,
    val createdAtMillis: Long
)

/**
 * Immutable, presentation-only projection consumed by the scene engine.
 * Domain entities deliberately stop at the mapper boundary.
 */
data class ThemeSceneState(
    val ledgerId: String,
    val themeId: String,
    val cycleId: String?,
    val completedCycleCount: Int,
    val participantA: ThemeParticipantState?,
    val participantB: ThemeParticipantState?,
    val participantAContributionMinor: Long,
    val participantBContributionMinor: Long,
    val cycleVolumeMinor: Long,
    val capacityMinor: Long,
    val normalizedFill: Float,
    val overflowRatio: Float,
    val netBalanceMinor: Long,
    val creditorId: String?,
    val latestContribution: ThemeContribution?
)

typealias ThemeFinancialState = ThemeSceneState

enum class ThemeEventOrigin { LOCAL, REMOTE, RECONCILIATION }

sealed interface ThemePresentationEvent {
    data class ContributionAdded(
        val contribution: ThemeContribution,
        val fromFill: Float,
        val targetFill: Float,
        val capacityReached: Boolean,
        val capacityExceeded: Boolean
    ) : ThemePresentationEvent

    data class SettlementCompleted(val settlementId: String) : ThemePresentationEvent

    data object CapacityReached : ThemePresentationEvent
    data object CapacityExceeded : ThemePresentationEvent
    data class BalanceChanged(val creditorId: String?, val amountMinor: Long) : ThemePresentationEvent
    data class ThemeChanged(val themeId: String) : ThemePresentationEvent
    data class ParticipantThemeIdentityChanged(
        val participantId: String,
        val identityId: String
    ) : ThemePresentationEvent
    data class ParticipantColorChanged(
        val participantId: String,
        val colorHex: String
    ) : ThemePresentationEvent
    data class ParticipantNameChanged(
        val participantId: String,
        val name: String
    ) : ThemePresentationEvent

    data class Reconciled(
        val fromFill: Float,
        val targetFill: Float,
        val cycleVolumeMinor: Long
    ) : ThemePresentationEvent
}

data class ThemeEventEnvelope(
    val id: String,
    val ledgerId: String,
    val origin: ThemeEventOrigin,
    val createdAtMillis: Long,
    val event: ThemePresentationEvent
)

data class ThemeEventQueueState(
    val pending: List<ThemeEventEnvelope> = emptyList(),
    val lastConsumedEventId: String? = null
)

interface ThemeDefinitionProvider {
    fun definitions(): List<ThemeDefinition>
}

object ThemeRegistry : ThemeDefinitionProvider {
    const val VESSEL = "vessel"
    const val WOODLAND = "woodland"
    const val MEMPOOL = "mempool"

    override fun definitions(): List<ThemeDefinition> = listOf(
        ThemeDefinition(VESSEL, "Vessel", 1, "The original glass vessel and colored liquid."),
        ThemeDefinition(WOODLAND, "Woodland", 1, "A warm woodland savings diorama."),
        ThemeDefinition(MEMPOOL, "Mempool", 1, "A dark mine cart for pending activity.")
    )

    fun contains(id: String): Boolean = definitions().any { it.id == id }
}

object ThemePresentationEventResolver {
    fun resolve(
        previous: ThemeFinancialState?,
        current: ThemeFinancialState,
        @Suppress("UNUSED_PARAMETER") isRemote: Boolean = false
    ): List<ThemePresentationEvent> {
        if (previous == null || previous.ledgerId != current.ledgerId) return emptyList()
        return buildList {
            val cycleChanged = previous.cycleId != current.cycleId &&
                current.completedCycleCount > previous.completedCycleCount
            val cycleVolumeIncreased = previous.cycleId == current.cycleId &&
                current.cycleVolumeMinor > previous.cycleVolumeMinor
            if (cycleChanged) {
                previous.cycleId?.let { add(ThemePresentationEvent.SettlementCompleted(it)) }
            } else if (cycleVolumeIncreased && current.latestContribution?.id != previous.latestContribution?.id) {
                current.latestContribution?.let {
                    add(
                        ThemePresentationEvent.ContributionAdded(
                            contribution = it,
                            fromFill = previous.normalizedFill,
                            targetFill = current.normalizedFill,
                            capacityReached = previous.normalizedFill < 1f && current.normalizedFill >= 1f,
                            capacityExceeded = previous.overflowRatio <= 0f && current.overflowRatio > 0f
                        )
                    )
                }
            }
            if (previous.netBalanceMinor != current.netBalanceMinor || previous.creditorId != current.creditorId) {
                add(ThemePresentationEvent.BalanceChanged(current.creditorId, current.netBalanceMinor))
            }
            if (previous.themeId != current.themeId) {
                add(ThemePresentationEvent.ThemeChanged(current.themeId))
            }
            listOfNotNull(current.participantA, current.participantB).forEach { participant ->
                val previousParticipant = listOfNotNull(previous.participantA, previous.participantB)
                    .firstOrNull { it.id == participant.id }
                if (previousParticipant != null && previousParticipant.identityId != participant.identityId) {
                    add(ThemePresentationEvent.ParticipantThemeIdentityChanged(participant.id, participant.identityId))
                }
                if (previousParticipant != null && previousParticipant.colorHex != participant.colorHex) {
                    add(ThemePresentationEvent.ParticipantColorChanged(participant.id, participant.colorHex))
                }
                if (previousParticipant != null && previousParticipant.name != participant.name) {
                    add(ThemePresentationEvent.ParticipantNameChanged(participant.id, participant.name))
                }
            }
        }
    }
}

object ThemeStateMapper {
    fun map(snapshot: LedgerSnapshot): ThemeFinancialState {
        val a = snapshot.participants.getOrNull(0)
        val b = snapshot.participants.getOrNull(1)
        val balance = snapshot.balance()
        return ThemeFinancialState(
            ledgerId = snapshot.id,
            themeId = snapshot.themeId,
            cycleId = snapshot.currentCycle?.id,
            completedCycleCount = snapshot.previousCycles.size,
            participantA = a?.let { ThemeParticipantState(it.id, it.name, it.colorHex, it.avatarId) },
            participantB = b?.let { ThemeParticipantState(it.id, it.name, it.colorHex, it.avatarId) },
            participantAContributionMinor = a?.let { snapshot.contributionFor(it.id) } ?: 0L,
            participantBContributionMinor = b?.let { snapshot.contributionFor(it.id) } ?: 0L,
            cycleVolumeMinor = snapshot.cycleVolumeMinor,
            capacityMinor = snapshot.capacityMinor,
            normalizedFill = snapshot.normalizedFill,
            overflowRatio = snapshot.overflowRatio,
            netBalanceMinor = balance.amountMinor,
            creditorId = balance.creditorId,
            latestContribution = snapshot.currentCycleExpenses.maxByOrNull { it.createdAtMillis }?.let {
                ThemeContribution(
                    id = it.id,
                    participantId = it.authorId,
                    amountMinor = it.amountMinor,
                    createdAtMillis = it.createdAtMillis
                )
            }
        )
    }
}
