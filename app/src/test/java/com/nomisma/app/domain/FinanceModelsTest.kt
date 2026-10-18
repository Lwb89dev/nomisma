package com.nomisma.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.nomisma.app.theme.ThemeIdentityRegistry
import com.nomisma.app.theme.ParticipantColorPalette
import com.nomisma.app.theme.ThemePresentationEvent
import com.nomisma.app.theme.ThemePresentationEventResolver
import com.nomisma.app.theme.ThemeStateMapper
import com.nomisma.app.data.ExpenseEntryRecents
import com.nomisma.app.data.ExpenseEntryRecentsPolicy
import java.time.LocalDate
import java.time.ZoneId

class FinanceModelsTest {
    private val zone = ZoneId.of("Europe/Rome")

    @Test
    fun defaultTaxonomyContainsTheTwentyRequestedCategories() {
        assertEquals(20, ExpenseCategories.defaults.size)
        assertTrue(ExpenseCategories.defaults.any { it.id == "fuel" })
        assertTrue(ExpenseCategories.defaults.any { it.id == "personal_care" })
        assertEquals(20, ExpenseCategories.defaults.map(ExpenseCategory::id).distinct().size)
    }

    @Test
    fun ledgerCategoryCatalogSupportsCustomNamesAndArchives() {
        val snapshot = snapshot(emptyList()).copy(
            categories = listOf(
                ExpenseCategory("groceries", "Spesa", isDefault = true),
                ExpenseCategory("custom", "Vacanze", isDefault = false),
                ExpenseCategory("archived", "Vecchia", isDefault = false, isArchived = true)
            )
        )

        assertEquals("Vacanze", snapshot.categoryDisplayName("custom"))
        assertEquals(listOf("groceries", "custom"), snapshot.activeCategories.map(ExpenseCategory::id))
        assertEquals("Sconosciuta", snapshot.categoryDisplayName("sconosciuta"))
    }

    @Test
    fun supportedCurrenciesDriveFormattingAndMetadataValidation() {
        assertEquals("€1234,56", 123_456L.formatMinorUnits("EUR"))
        assertEquals("\$1234,56", 123_456L.formatMinorUnits("USD"))
        assertEquals("CHF 1234,56", 123_456L.formatMinorUnits("CHF"))
        assertEquals("Casa condivisa", LedgerMetadataPolicy.normalizeTitle("  Casa   condivisa "))
        assertTrue(runCatching { LedgerMetadataPolicy.validate("Casa", "JPY") }.isFailure)
    }

    @Test
    fun participantNamesAreNormalizedAndRejectControlCharacters() {
        assertEquals("Alice B.", ParticipantNamePolicy.validate(" Alice   B. "))
        assertTrue(runCatching { ParticipantNamePolicy.validate("Alice\nBob") }.isFailure)
    }

    @Test
    fun lastMonthUsesCalendarBoundariesIncludingLeapDay() {
        val now = LocalDate.of(2024, 3, 15).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val range = DateRangeResolver.resolve(TimeRangePreset.LAST_MONTH, now, zone)

        assertEquals(
            LocalDate.of(2024, 2, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            range.startMillis
        )
        assertEquals(
            LocalDate.of(2024, 3, 1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L,
            range.endMillis
        )
    }

    @Test
    fun combinedFilterRequiresEverySelectedTag() {
        val expense = expense(tags = listOf("household", "party"))

        assertTrue(ExpenseFilter(tags = setOf("household", "party")).matches(expense))
        assertFalse(ExpenseFilter(tags = setOf("household", "weekly")).matches(expense))
    }

    @Test
    fun localSearchIncludesMerchantAndNotes() {
        val expense = expense().copy(
            description = "Acquisto settimanale",
            merchant = "Esselunga",
            notes = "Cena con amici"
        )

        assertTrue(ExpenseFilter(searchQuery = "esselunga").matches(expense))
        assertTrue(ExpenseFilter(searchQuery = "amici").matches(expense))
        assertFalse(ExpenseFilter(searchQuery = "farmacia").matches(expense))
    }

    @Test
    fun reusableFilterDraftCombinesMultipleCriteriaAndAmounts() {
        val draft = ExpenseFilterDraft(
            participantIds = setOf("alice"),
            categoryIds = setOf("groceries", "dining"),
            tags = setOf("household", "party"),
            searchQuery = "test",
            minAmount = "5.00",
            maxAmount = "20.00"
        )
        val matching = expense(amount = 1_000L, tags = listOf("household", "party"))

        assertTrue(draft.toExpenseFilter().matches(matching))
        assertFalse(draft.copy(participantIds = setOf("bob")).toExpenseFilter().matches(matching))
        assertFalse(draft.copy(tags = setOf("missing")).toExpenseFilter().matches(matching))
        assertEquals(6, draft.activeCriteriaCount)
    }

    @Test
    fun filterDraftRejectsInvalidRangesInsteadOfShowingUnfilteredData() {
        val draft = ExpenseFilterDraft(
            timeRange = TimeRangePreset.CUSTOM,
            customStart = "2026-10-20",
            customEnd = "2026-10-01"
        )

        assertTrue(draft.validationError() != null)
        assertFalse(draft.toExpenseFilter().matches(expense(createdAt = 1_000L)))
    }

    @Test
    fun expenseEntryRecentsPromoteLatestValuesWithoutDuplicates() {
        val initial = ExpenseEntryRecents(
            categoryIds = listOf("dining", "groceries"),
            tags = listOf("weekly", "home"),
            participantId = "alice"
        )
        val updated = ExpenseEntryRecentsPolicy.record(
            current = initial,
            categoryId = "groceries",
            tags = listOf("home", "party"),
            participantId = "bob"
        )

        assertEquals(listOf("groceries", "dining"), updated.categoryIds)
        assertEquals(listOf("home", "party", "weekly"), updated.tags.take(3))
        assertEquals("bob", updated.participantId)
        assertEquals(updated.tags.size, updated.tags.distinct().size)
    }

    @Test
    fun analyticsIgnoreDeletedExpensesAndBuildTimeline() {
        val firstDay = LocalDate.of(2026, 9, 2).atStartOfDay(zone).toInstant().toEpochMilli()
        val secondDay = LocalDate.of(2026, 9, 3).atStartOfDay(zone).toInstant().toEpochMilli()
        val snapshot = snapshot(
            listOf(
                expense(id = "a", amount = 1_000L, createdAt = firstDay),
                expense(id = "b", amount = 2_500L, createdAt = secondDay),
                expense(id = "deleted", amount = 99_000L, createdAt = secondDay, deletedAt = secondDay)
            )
        )

        val analytics = AnalyticsCalculator.calculate(snapshot, zoneId = zone)

        assertEquals(3_500L, analytics.totalMinor)
        assertEquals(2, analytics.transactionCount)
        assertEquals(listOf(1_000L, 2_500L), analytics.spendingOverTime.map(SpendingPoint::totalMinor))
    }

    @Test
    fun analyticsTimelineKeepsEmptyBucketsAndExposesItsGranularity() {
        fun at(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val first = LocalDate.of(2026, 10, 1)
        val third = LocalDate.of(2026, 10, 3)
        val snapshot = snapshot(
            listOf(
                expense(id = "first", amount = 1_000L, createdAt = at(first)),
                expense(id = "third", amount = 2_000L, createdAt = at(third))
            )
        )
        val range = DateRange(at(first), at(third.plusDays(1)) - 1L)

        val analytics = AnalyticsCalculator.calculate(
            snapshot = snapshot,
            filter = ExpenseFilter(dateRange = range),
            zoneId = zone,
            nowMillis = at(third) + 12 * 60 * 60 * 1_000L
        )

        assertEquals(AggregationPeriod.DAY, analytics.aggregationPeriod)
        assertEquals(listOf(1_000L, 0L, 2_000L), analytics.spendingOverTime.map(SpendingPoint::totalMinor))
    }

    @Test
    fun personalShareStaysOutOfTheSplitButCountsInVolumeAndTags() {
        fun at(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val first = LocalDate.of(2026, 10, 1)
        val second = LocalDate.of(2026, 10, 2)
        val third = LocalDate.of(2026, 10, 3)
        val household = snapshot(
            listOf(
                expense(id = "shared", amount = 10_000L, authorId = "alice", createdAt = at(first), tags = listOf("supermercato")),
                expense(id = "own", amount = 2_000L, authorId = "alice", createdAt = at(first), tags = listOf("supermercato"), sharedEvenly = false),
                expense(id = "bob-own", amount = 4_000L, authorId = "bob", createdAt = at(third), tags = listOf("supermercato", "casa"), sharedEvenly = false)
            )
        )
        val range = DateRange(at(first), at(third.plusDays(1)) - 1L)
        val analytics = AnalyticsCalculator.calculate(
            snapshot = household,
            filter = ExpenseFilter(dateRange = range),
            zoneId = zone,
            nowMillis = at(third) + 12 * 60 * 60 * 1_000L
        )

        assertEquals(16_000L, analytics.totalMinor)
        assertEquals(16_000L, household.cycleVolumeMinor)
        assertEquals(12_000L, household.contributionFor("alice"))
        assertEquals(4_000L, household.contributionFor("bob"))
        assertEquals("alice", analytics.filteredBalance.creditorId)
        assertEquals(3_000L, analytics.filteredBalance.amountMinor)
        assertEquals(mapOf("alice" to 12_000L, "bob" to 4_000L), analytics.filteredBalance.paidByParticipant)
        assertEquals(mapOf("supermercato" to 16_000L, "casa" to 4_000L), analytics.byTag)
        assertEquals(listOf("supermercato", "casa"), analytics.tagSeries.map(TagSeries::tag))
        assertEquals(listOf(12_000L, 0L, 4_000L), analytics.tagSeries.first { it.tag == "supermercato" }.totals)
        assertEquals(listOf(0L, 0L, 4_000L), analytics.tagSeries.first { it.tag == "casa" }.totals)
        assertEquals(analytics.spendingOverTime.size, analytics.tagSeries.first().totals.size)
        assertEquals(at(second), analytics.spendingOverTime[1].startMillis)
    }

    @Test
    fun sharedBillCreditsHalfAndAlreadyDividedQuotaCreditsTheFullAmount() {
        val shared = snapshot(listOf(expense(id = "shared", amount = 5_000L, authorId = "alice")))
        val quota = snapshot(listOf(expense(id = "quota", amount = 5_000L, authorId = "alice", sharedEvenly = false)))
        val otherPaid = snapshot(listOf(expense(id = "bob", amount = 5_000L, authorId = "bob")))
        val uneven = snapshot(
            listOf(
                expense(id = "alice-share", amount = 7_000L, authorId = "alice", sharedEvenly = false),
                expense(id = "bob-share", amount = 3_000L, authorId = "bob", sharedEvenly = false)
            )
        )
        val even = snapshot(
            listOf(
                expense(id = "alice-share", amount = 4_000L, authorId = "alice", sharedEvenly = false),
                expense(id = "bob-share", amount = 4_000L, authorId = "bob", sharedEvenly = false)
            )
        )
        val oddCent = snapshot(listOf(expense(id = "odd", amount = 101L, authorId = "alice")))

        assertEquals("alice", shared.balance().creditorId)
        assertEquals(2_500L, shared.balance().amountMinor)
        assertEquals("alice", quota.balance().creditorId)
        assertEquals(5_000L, quota.balance().amountMinor)
        assertEquals("bob", otherPaid.balance().creditorId)
        assertEquals(2_500L, otherPaid.balance().amountMinor)
        assertEquals("alice", uneven.balance().creditorId)
        assertEquals(4_000L, uneven.balance().amountMinor)
        assertEquals(10_000L, AnalyticsCalculator.calculate(uneven, zoneId = zone).totalMinor)
        assertEquals(null, even.balance().creditorId)
        assertEquals("alice", oddCent.balance().creditorId)
        assertEquals(50L, oddCent.balance().amountMinor)
    }

    @Test
    fun tagTimelineKeepsOnlyTheLargestSeries() {
        val expenses = (1..7).map { index ->
            expense(
                id = "tag-$index",
                amount = index * 100L,
                tags = listOf("tag-$index"),
                createdAt = 1_700_000_000_000L
            )
        }
        val analytics = AnalyticsCalculator.calculate(snapshot(expenses), zoneId = zone)

        assertEquals(7, analytics.byTag.size)
        assertEquals(6, analytics.tagSeries.size)
        assertEquals("tag-7", analytics.tagSeries.first().tag)
        assertFalse(analytics.tagSeries.any { it.tag == "tag-1" })
    }

    @Test
    fun settlementMetadataUsesStableWireValuesAndBoundedNotes() {
        assertEquals(SettlementMethod.BANK_TRANSFER, SettlementMethod.fromWire("bank_transfer"))
        assertEquals(SettlementMethod.LIGHTNING, SettlementMethod.fromWire("lightning"))
        assertEquals(null, SettlementMethod.fromWire("unknown"))
        assertEquals("Pagamento completato", SettlementPolicy.normalizeNote("  Pagamento completato  "))
        assertTrue(
            runCatching { SettlementPolicy.normalizeNote("x".repeat(SettlementPolicy.MAX_NOTE_LENGTH + 1)) }
                .isFailure
        )
    }

    @Test
    fun analyticsGroupMerchantsIgnoringCaseWhitespaceAndBlankValues() {
        val snapshot = snapshot(
            listOf(
                expense(id = "a", amount = 1_000L).copy(merchant = "  Esselunga  "),
                expense(id = "b", amount = 2_500L).copy(merchant = "esselunga"),
                expense(id = "c", amount = 900L).copy(merchant = "   ")
            )
        )

        val analytics = AnalyticsCalculator.calculate(snapshot, zoneId = zone)

        assertEquals(mapOf("Esselunga" to 3_500L), analytics.byMerchant)
    }

    @Test
    fun analyticsBalanceIsCalculatedOnlyFromFilteredExpenses() {
        val snapshot = snapshot(
            listOf(
                expense(id = "alice-food", amount = 3_000L, authorId = "alice"),
                expense(id = "bob-food", amount = 1_000L, authorId = "bob"),
                expense(id = "alice-travel", amount = 9_000L, authorId = "alice").copy(categoryId = "travel")
            )
        )

        val analytics = AnalyticsCalculator.calculate(
            snapshot,
            ExpenseFilter(categories = setOf("groceries")),
            zone
        )

        assertEquals("alice", analytics.filteredBalance.creditorId)
        assertEquals(1_000L, analytics.filteredBalance.amountMinor)
        assertEquals(mapOf("alice" to 3_000L, "bob" to 1_000L), analytics.filteredBalance.paidByParticipant)
    }

    @Test
    fun expensePolicyRejectsOversizedAmountsAndDescriptions() {
        assertTrue(
            runCatching {
                ExpensePolicy.validate(ExpensePolicy.MAX_AMOUNT_MINOR + 1L, "Spesa", "", "")
            }.isFailure
        )
        assertTrue(
            runCatching {
                ExpensePolicy.validate(100L, "x".repeat(ExpensePolicy.MAX_DESCRIPTION_LENGTH + 1), "", "")
            }.isFailure
        )
        assertTrue(
            runCatching {
                ExpensePolicy.validateOccurredAt(
                    occurredAtMillis = ExpensePolicy.MIN_OCCURRED_AT_MILLIS - 1L,
                    nowMillis = 2_000_000_000_000L
                )
            }.isFailure
        )
        assertTrue(
            runCatching {
                ExpensePolicy.validateOccurredAt(
                    occurredAtMillis = 2_000_000_000_000L + ExpensePolicy.MAX_FUTURE_SKEW_MILLIS + 1L,
                    nowMillis = 2_000_000_000_000L
                )
            }.isFailure
        )
    }

    @Test
    fun analyticsCompareWithImmediatelyPreviousPeriodKeepingOtherFilters() {
        fun at(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val snapshot = snapshot(
            listOf(
                expense(id = "current-alice", amount = 3_000L, createdAt = at(LocalDate.of(2026, 10, 3))),
                expense(id = "previous-alice", amount = 2_000L, createdAt = at(LocalDate.of(2026, 9, 26))),
                expense(id = "previous-bob", amount = 8_000L, createdAt = at(LocalDate.of(2026, 9, 27)), authorId = "bob")
            )
        )
        val currentRange = DateRange(
            startMillis = at(LocalDate.of(2026, 10, 1)),
            endMillis = at(LocalDate.of(2026, 10, 8)) - 1L
        )

        val analytics = AnalyticsCalculator.calculate(
            snapshot,
            ExpenseFilter(participants = setOf("alice"), dateRange = currentRange),
            zone
        )

        val comparison = requireNotNull(analytics.periodComparison)
        assertEquals(3_000L, comparison.currentTotalMinor)
        assertEquals(2_000L, comparison.previousTotalMinor)
        assertEquals(1_000L, comparison.changeMinor)
        assertEquals(50.0, comparison.changePercent!!, 0.001)
        assertEquals(1, comparison.previousTransactionCount)
    }

    @Test
    fun analyticsComparisonHasNoPercentageWhenPreviousPeriodIsEmpty() {
        val start = LocalDate.of(2026, 10, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val snapshot = snapshot(listOf(expense(amount = 1_500L, createdAt = start)))

        val analytics = AnalyticsCalculator.calculate(
            snapshot,
            ExpenseFilter(dateRange = DateRange(start, start + 86_400_000L - 1L)),
            zone
        )

        val comparison = requireNotNull(analytics.periodComparison)
        assertEquals(0L, comparison.previousTotalMinor)
        assertEquals(null, comparison.changePercent)
    }

    @Test
    fun balanceAndVolumeUseOnlyTheOpenSettlementCycle() {
        val snapshot = snapshot(
            expenses = listOf(
                expense(id = "old", amount = 90_000L, cycleId = "closed", authorId = "alice"),
                expense(id = "current-a", amount = 4_000L, cycleId = "open", authorId = "alice"),
                expense(id = "current-b", amount = 2_000L, cycleId = "open", authorId = "bob")
            ),
            cycles = listOf(
                SettlementCycle("open", "ledger", 2_000L, null, 0L, null, 0L, 0, null),
                SettlementCycle("closed", "ledger", 1_000L, 1_999L, 90_000L, "alice", 45_000L, 1, "alice")
            )
        )

        assertEquals(6_000L, snapshot.cycleVolumeMinor)
        assertEquals("alice", snapshot.balance().creditorId)
        assertEquals(1_000L, snapshot.balance().amountMinor)
        assertEquals(1, snapshot.previousCycles.size)
    }

    @Test
    fun themeUsesOnlyTheOpenCycleAndKeepsParticipantIdentities() {
        val snapshot = snapshot(
            expenses = listOf(
                expense(id = "closed-late", amount = 90_000L, cycleId = "closed", createdAt = 9_000L),
                expense(id = "open", amount = 4_000L, cycleId = "open", createdAt = 2_000L)
            ),
            cycles = listOf(
                SettlementCycle("open", "ledger", 1_000L, null, 0L, null, 0L, 0, null),
                SettlementCycle("closed", "ledger", 0L, 999L, 90_000L, "alice", 45_000L, 1, "alice")
            )
        )

        val theme = ThemeStateMapper.map(snapshot)

        assertEquals("open", theme.latestContribution?.id)
        assertEquals(4_000L, theme.cycleVolumeMinor)
        assertEquals("bear", theme.participantA?.identityId)
        assertEquals("🐻", ThemeIdentityRegistry.symbol(theme.participantA?.identityId.orEmpty()))
    }

    @Test
    fun woodlandIdentityCatalogContainsDistinctCharactersAndMaterials() {
        val required = setOf("bear", "squirrel", "fox", "rabbit", "raccoon", "hedgehog")

        assertTrue(ThemeIdentityRegistry.identities.map { it.id }.containsAll(required))
        assertEquals(
            ThemeIdentityRegistry.identities.size,
            ThemeIdentityRegistry.identities.map { it.id }.distinct().size
        )
        assertTrue(ThemeIdentityRegistry.identities.all { it.contributionMaterial.isNotBlank() })
    }

    @Test
    fun participantColorsAreNormalizedAndValidated() {
        assertEquals("#38E66B", ParticipantColorPalette.normalize("38e66b"))
        assertEquals("#147DFF", ParticipantColorPalette.normalize(" #147dff "))
        assertEquals(null, ParticipantColorPalette.normalize("#xyz"))
        assertEquals(ParticipantColorPalette.colors.size, ParticipantColorPalette.colors.distinct().size)
    }

    @Test
    fun themeEventsAreDerivedFromFinancialStateTransitions() {
        val beforeContribution = ThemeStateMapper.map(snapshot(emptyList()))
        val afterContribution = ThemeStateMapper.map(snapshot(listOf(expense(id = "new", amount = 2_500L))))

        val contributionEvents = ThemePresentationEventResolver.resolve(beforeContribution, afterContribution)

        assertTrue(contributionEvents.any { it is ThemePresentationEvent.ContributionAdded })

        val openSnapshot = snapshot(
            expenses = listOf(expense(id = "cycle-entry", amount = 2_500L, cycleId = "open")),
            cycles = listOf(SettlementCycle("open", "ledger", 1_000L, null, 0L, null, 0L, 0, null))
        )
        val closedSnapshot = snapshot(
            expenses = openSnapshot.expenses,
            cycles = listOf(
                SettlementCycle("next", "ledger", 2_000L, null, 0L, null, 0L, 0, null),
                SettlementCycle("open", "ledger", 1_000L, 1_999L, 2_500L, "alice", 1_250L, 1, "alice")
            )
        )

        val settlementEvents = ThemePresentationEventResolver.resolve(
            ThemeStateMapper.map(openSnapshot),
            ThemeStateMapper.map(closedSnapshot)
        )

        assertTrue(settlementEvents.any { it is ThemePresentationEvent.SettlementCompleted })
        assertFalse(settlementEvents.any { it is ThemePresentationEvent.ContributionAdded })

        val recolored = beforeContribution.copy(
            participantA = beforeContribution.participantA?.copy(colorHex = "#FF6685")
        )
        val colorEvents = ThemePresentationEventResolver.resolve(beforeContribution, recolored)
        assertTrue(colorEvents.any { it is ThemePresentationEvent.ParticipantColorChanged })

        val renamed = beforeContribution.copy(
            participantA = beforeContribution.participantA?.copy(name = "Alicia")
        )
        val nameEvents = ThemePresentationEventResolver.resolve(beforeContribution, renamed)
        assertTrue(nameEvents.any { it is ThemePresentationEvent.ParticipantNameChanged })
    }

    @Test
    fun themeDoesNotInventContributionsAfterDeletionOrCapacityChange() {
        val older = expense(id = "older", amount = 1_000L, createdAt = 1_000L)
        val latest = expense(id = "latest", amount = 2_000L, createdAt = 2_000L)
        val beforeDeletion = ThemeStateMapper.map(snapshot(listOf(older, latest)))
        val afterDeletion = ThemeStateMapper.map(
            snapshot(listOf(older, latest.copy(deletedAtMillis = 3_000L)))
        )

        val deletionEvents = ThemePresentationEventResolver.resolve(beforeDeletion, afterDeletion)

        assertFalse(deletionEvents.any { it is ThemePresentationEvent.ContributionAdded })

        val capacityChanged = beforeDeletion.copy(
            capacityMinor = 1_000L,
            normalizedFill = 1f,
            overflowRatio = 2f
        )
        val capacityEvents = ThemePresentationEventResolver.resolve(beforeDeletion, capacityChanged)

        assertFalse(capacityEvents.any { it is ThemePresentationEvent.CapacityReached })
        assertFalse(capacityEvents.any { it is ThemePresentationEvent.CapacityExceeded })
    }

    private fun expense(
        id: String = "expense",
        amount: Long = 1_000L,
        tags: List<String> = emptyList(),
        createdAt: Long = 1_000L,
        deletedAt: Long? = null,
        cycleId: String = "",
        authorId: String = "alice",
        sharedEvenly: Boolean = true
    ) = Expense(
        id = id,
        ledgerId = "ledger",
        authorId = authorId,
        amountMinor = amount,
        currency = "EUR",
        description = "Test",
        categoryId = "groceries",
        tags = tags,
        createdAtMillis = createdAt,
        deletedAtMillis = deletedAt,
        cycleId = cycleId,
        sharedEvenly = sharedEvenly
    )

    private fun snapshot(
        expenses: List<Expense>,
        cycles: List<SettlementCycle> = emptyList()
    ) = LedgerSnapshot(
        id = "ledger",
        title = "Casa",
        currency = "EUR",
        capacityMinor = 100_000L,
        participants = listOf(
            Participant("alice", "Alice", "#00ff00", "bear"),
            Participant("bob", "Bob", "#0000ff", "squirrel")
        ),
        expenses = expenses,
        themeId = "vessel",
        themeVersion = 1,
        settlementCycles = cycles
    )
}
