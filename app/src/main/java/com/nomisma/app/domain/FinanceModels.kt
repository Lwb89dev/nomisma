package com.nomisma.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.UUID

data class Participant(
    val id: String,
    val name: String,
    val colorHex: String,
    val avatarId: String
)

data class SupportedCurrency(
    val code: String,
    val symbol: String,
    val displayName: String
)

object SupportedCurrencies {
    val all = listOf(
        SupportedCurrency("EUR", "€", "Euro"),
        SupportedCurrency("USD", "$", "Dollaro USA"),
        SupportedCurrency("GBP", "£", "Sterlina"),
        SupportedCurrency("CHF", "CHF ", "Franco svizzero")
    )

    fun contains(code: String): Boolean = all.any { it.code == code }
    fun symbol(code: String): String = all.firstOrNull { it.code == code }?.symbol ?: "$code "
}

object LedgerMetadataPolicy {
    fun normalizeTitle(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    fun validate(title: String, currency: String) {
        require(title.none(Char::isISOControl)) { "Il nome del ledger contiene caratteri non validi" }
        require(normalizeTitle(title).length in 1..60) { "Il nome del ledger deve contenere da 1 a 60 caratteri" }
        require(SupportedCurrencies.contains(currency)) { "Valuta non supportata" }
    }
}

object ParticipantNamePolicy {
    fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    fun validate(raw: String): String {
        require(raw.none(Char::isISOControl)) { "Il nome contiene caratteri non validi" }
        return normalize(raw).also { normalized ->
            require(normalized.length in 1..40) { "Il nome deve contenere da 1 a 40 caratteri" }
        }
    }
}

data class Expense(
    val id: String,
    val ledgerId: String,
    val authorId: String,
    val amountMinor: Long,
    val currency: String,
    val description: String,
    val categoryId: String,
    val tags: List<String>,
    val createdAtMillis: Long,
    val authorSequence: Long = 0L,
    val updatedAtMillis: Long = createdAtMillis,
    val deletedAtMillis: Long? = null,
    val cycleId: String = "",
    val merchant: String = "",
    val notes: String = ""
)

object ExpensePolicy {
    const val MAX_AMOUNT_MINOR = 100_000_000_000L
    const val MAX_DESCRIPTION_LENGTH = 500
    const val MAX_MERCHANT_LENGTH = 200
    const val MAX_NOTES_LENGTH = 2_000
    const val MAX_TAGS = 32
    const val MAX_TAG_LENGTH = 64
    const val MAX_ENTITY_ID_LENGTH = 200
    const val MIN_OCCURRED_AT_MILLIS = 946_684_800_000L // 2000-01-01T00:00:00Z
    const val MAX_FUTURE_SKEW_MILLIS = 86_400_000L

    fun validate(amountMinor: Long, description: String, merchant: String, notes: String) {
        require(amountMinor in 1L..MAX_AMOUNT_MINOR) { "Importo fuori dall'intervallo supportato" }
        require(description.trim().isNotEmpty()) { "La descrizione è vuota" }
        require(description.length <= MAX_DESCRIPTION_LENGTH) { "La descrizione è troppo lunga" }
        require(merchant.length <= MAX_MERCHANT_LENGTH) { "Il nome dell'esercente è troppo lungo" }
        require(notes.length <= MAX_NOTES_LENGTH) { "Le note sono troppo lunghe" }
    }

    fun validateOccurredAt(occurredAtMillis: Long, nowMillis: Long = System.currentTimeMillis()) {
        require(occurredAtMillis >= MIN_OCCURRED_AT_MILLIS) { "La data della spesa è troppo lontana nel passato" }
        require(occurredAtMillis <= nowMillis + MAX_FUTURE_SKEW_MILLIS) { "La data della spesa è nel futuro" }
    }
}

data class TagDefinition(
    val id: String,
    val name: String,
    val isArchived: Boolean,
    val usageCount: Int = 0
)

enum class SettlementMethod(val wireValue: String, val displayName: String) {
    BANK_TRANSFER("bank_transfer", "Bonifico"),
    CASH("cash", "Contanti"),
    LIGHTNING("lightning", "Lightning"),
    OTHER("other", "Altro");

    companion object {
        fun fromWire(value: String?): SettlementMethod? =
            entries.firstOrNull { it.wireValue == value }
    }
}

object SettlementPolicy {
    const val MAX_NOTE_LENGTH = 500

    fun normalizeNote(raw: String): String = raw.trim().also {
        require(it.length <= MAX_NOTE_LENGTH) { "La nota del settlement è troppo lunga" }
    }
}

data class SettlementCycle(
    val id: String,
    val ledgerId: String,
    val startedAtMillis: Long,
    val closedAtMillis: Long?,
    val totalMinor: Long,
    val creditorId: String?,
    val balanceMinor: Long,
    val transactionCount: Int,
    val closedByParticipantId: String?,
    val method: SettlementMethod = SettlementMethod.OTHER,
    val note: String = ""
) {
    val isClosed: Boolean get() = closedAtMillis != null
}

data class LedgerSnapshot(
    val id: String,
    val title: String,
    val currency: String,
    val capacityMinor: Long,
    val participants: List<Participant>,
    val expenses: List<Expense>,
    val themeId: String,
    val themeVersion: Int,
    val tags: List<TagDefinition> = emptyList(),
    val settlementCycles: List<SettlementCycle> = emptyList(),
    val categories: List<ExpenseCategory> = ExpenseCategories.defaults
) {
    val activeExpenses: List<Expense>
        get() = expenses.filter { it.deletedAtMillis == null }

    val currentCycle: SettlementCycle?
        get() = settlementCycles.firstOrNull { !it.isClosed }

    val currentCycleExpenses: List<Expense>
        get() {
            val cycleId = currentCycle?.id ?: return activeExpenses
            return activeExpenses.filter { it.cycleId == cycleId }
        }

    val previousCycles: List<SettlementCycle>
        get() = settlementCycles.filter(SettlementCycle::isClosed).sortedByDescending { it.closedAtMillis }

    val activeTags: List<TagDefinition>
        get() = tags.filterNot(TagDefinition::isArchived)

    val activeCategories: List<ExpenseCategory>
        get() = categories.filterNot(ExpenseCategory::isArchived)

    fun categoryDisplayName(categoryId: String): String =
        categories.firstOrNull { it.id == categoryId }?.displayName
            ?: ExpenseCategories.displayName(categoryId)

    val cycleVolumeMinor: Long
        get() = currentCycleExpenses.sumOf { it.amountMinor }

    val normalizedFill: Float
        get() = if (capacityMinor <= 0L) 0f
        else (cycleVolumeMinor.toDouble() / capacityMinor.toDouble()).coerceAtMost(1.0).toFloat()

    val overflowRatio: Float
        get() = if (capacityMinor <= 0L) 0f
        else ((cycleVolumeMinor - capacityMinor).coerceAtLeast(0L).toDouble() / capacityMinor.toDouble()).toFloat()

    fun contributionFor(participantId: String): Long =
        currentCycleExpenses.filter { it.authorId == participantId }.sumOf { it.amountMinor }

    fun balance(): BalanceSummary = BalanceCalculator.calculate(this)

    companion object {
        fun empty(): LedgerSnapshot = LedgerSnapshot(
            id = "",
            title = "Nomisma",
            currency = "EUR",
            capacityMinor = 100_000L,
            participants = emptyList(),
            expenses = emptyList(),
            themeId = "vessel",
            themeVersion = 1
        )
    }
}

data class BalanceSummary(
    val creditorId: String?,
    val amountMinor: Long,
    val paidByParticipant: Map<String, Long>
)

object BalanceCalculator {
    fun calculate(snapshot: LedgerSnapshot): BalanceSummary {
        return calculate(snapshot.participants, snapshot.currentCycleExpenses)
    }

    fun calculate(participants: List<Participant>, expenses: List<Expense>): BalanceSummary {
        val orderedParticipants = participants.sortedBy { it.id }
        if (orderedParticipants.size < 2) {
            return BalanceSummary(null, 0L, emptyMap())
        }

        val first = orderedParticipants[0]
        val second = orderedParticipants[1]
        val paidFirst = expenses.filter { it.authorId == first.id }.sumOf(Expense::amountMinor)
        val paidSecond = expenses.filter { it.authorId == second.id }.sumOf(Expense::amountMinor)
        val total = paidFirst + paidSecond

        // The odd minor unit is assigned deterministically to the first participant.
        val dueFirst = total / 2L + total % 2L
        val dueSecond = total - dueFirst
        val creditFirst = paidFirst - dueFirst
        val creditSecond = paidSecond - dueSecond

        return when {
            creditFirst > 0L -> BalanceSummary(first.id, creditFirst, mapOf(first.id to paidFirst, second.id to paidSecond))
            creditSecond > 0L -> BalanceSummary(second.id, creditSecond, mapOf(first.id to paidFirst, second.id to paidSecond))
            else -> BalanceSummary(null, 0L, mapOf(first.id to paidFirst, second.id to paidSecond))
        }
    }
}

data class DateRange(
    val startMillis: Long? = null,
    val endMillis: Long? = null
)

enum class TimeRangePreset(val displayName: String) {
    TODAY("Oggi"),
    THIS_WEEK("Questa settimana"),
    THIS_MONTH("Questo mese"),
    LAST_MONTH("Mese scorso"),
    LAST_30_DAYS("Ultimi 30 giorni"),
    LAST_90_DAYS("Ultimi 90 giorni"),
    THIS_YEAR("Quest'anno"),
    PREVIOUS_YEAR("Anno scorso"),
    ALL_TIME("Sempre"),
    CUSTOM("Personalizzato")
}

object DateRangeResolver {
    fun resolve(
        preset: TimeRangePreset,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        customRange: DateRange = DateRange()
    ): DateRange {
        if (preset == TimeRangePreset.ALL_TIME) return DateRange()
        if (preset == TimeRangePreset.CUSTOM) return customRange

        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val today = now.toLocalDate()
        val (startDate, endDateExclusive) = when (preset) {
            TimeRangePreset.TODAY -> today to today.plusDays(1)
            TimeRangePreset.THIS_WEEK -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                monday to monday.plusWeeks(1)
            }
            TimeRangePreset.THIS_MONTH -> today.withDayOfMonth(1) to today.withDayOfMonth(1).plusMonths(1)
            TimeRangePreset.LAST_MONTH -> {
                val start = today.withDayOfMonth(1).minusMonths(1)
                start to start.plusMonths(1)
            }
            TimeRangePreset.LAST_30_DAYS -> today.minusDays(29) to today.plusDays(1)
            TimeRangePreset.LAST_90_DAYS -> today.minusDays(89) to today.plusDays(1)
            TimeRangePreset.THIS_YEAR -> today.withDayOfYear(1) to today.withDayOfYear(1).plusYears(1)
            TimeRangePreset.PREVIOUS_YEAR -> {
                val start = today.withDayOfYear(1).minusYears(1)
                start to start.plusYears(1)
            }
            TimeRangePreset.ALL_TIME, TimeRangePreset.CUSTOM -> error("Handled above")
        }
        return DateRange(
            startMillis = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            endMillis = endDateExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L
        )
    }
}

data class ExpenseCategory(
    val id: String,
    val displayName: String,
    val isDefault: Boolean = true,
    val isArchived: Boolean = false,
    val usageCount: Int = 0
)

object ExpenseCategories {
    val defaults = listOf(
        ExpenseCategory("groceries", "Spesa"),
        ExpenseCategory("dining", "Ristoranti"),
        ExpenseCategory("housing", "Alloggio"),
        ExpenseCategory("utilities", "Utenze"),
        ExpenseCategory("transport", "Trasporti"),
        ExpenseCategory("fuel", "Carburante"),
        ExpenseCategory("shopping", "Acquisti"),
        ExpenseCategory("health", "Salute"),
        ExpenseCategory("entertainment", "Intrattenimento"),
        ExpenseCategory("travel", "Viaggi"),
        ExpenseCategory("subscriptions", "Abbonamenti"),
        ExpenseCategory("home", "Casa"),
        ExpenseCategory("education", "Istruzione"),
        ExpenseCategory("pets", "Animali"),
        ExpenseCategory("gifts", "Regali"),
        ExpenseCategory("insurance", "Assicurazioni"),
        ExpenseCategory("taxes", "Tasse"),
        ExpenseCategory("fees", "Commissioni"),
        ExpenseCategory("personal_care", "Cura personale"),
        ExpenseCategory("other", "Altro")
    )

    fun displayName(categoryId: String): String =
        defaults.firstOrNull { it.id == categoryId }?.displayName
            ?: categoryId.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

data class ExpenseFilter(
    val participants: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val dateRange: DateRange = DateRange(),
    val minAmountMinor: Long? = null,
    val maxAmountMinor: Long? = null,
    val searchQuery: String = ""
) {
    fun matches(expense: Expense): Boolean {
        val query = searchQuery.trim().lowercase()
        val searchable = buildString {
            append(expense.description)
            append(' ')
            append(expense.merchant)
            append(' ')
            append(expense.notes)
            append(' ')
            append(expense.categoryId)
            append(' ')
            append(expense.tags.joinToString(" "))
        }.lowercase()

        return (participants.isEmpty() || expense.authorId in participants) &&
            (categories.isEmpty() || expense.categoryId in categories) &&
            (tags.isEmpty() || expense.tags.containsAll(tags)) &&
            (dateRange.startMillis == null || expense.createdAtMillis >= dateRange.startMillis) &&
            (dateRange.endMillis == null || expense.createdAtMillis <= dateRange.endMillis) &&
            (minAmountMinor == null || expense.amountMinor >= minAmountMinor) &&
            (maxAmountMinor == null || expense.amountMinor <= maxAmountMinor) &&
            (query.isEmpty() || searchable.contains(query))
    }
}

data class AnalyticsSnapshot(
    val totalMinor: Long,
    val transactionCount: Int,
    val averageMinor: Long,
    val filteredBalance: BalanceSummary,
    val byParticipant: Map<String, Long>,
    val byCategory: Map<String, Long>,
    val byTag: Map<String, Long>,
    val byMerchant: Map<String, Long>,
    val periodComparison: PeriodComparison?,
    val aggregationPeriod: AggregationPeriod,
    val spendingOverTime: List<SpendingPoint>
)

data class PeriodComparison(
    val currentTotalMinor: Long,
    val previousTotalMinor: Long,
    val currentTransactionCount: Int,
    val previousTransactionCount: Int,
    val changeMinor: Long,
    val changePercent: Double?
)

enum class AggregationPeriod { DAY, WEEK, MONTH }

data class SpendingPoint(
    val startMillis: Long,
    val label: String,
    val totalMinor: Long
)

object AnalyticsCalculator {
    fun calculate(
        snapshot: LedgerSnapshot,
        filter: ExpenseFilter = ExpenseFilter(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis()
    ): AnalyticsSnapshot {
        val expenses = snapshot.activeExpenses.filter(filter::matches)
        val byParticipant = expenses.groupingBy(Expense::authorId).fold(0L) { total, expense -> total + expense.amountMinor }
        val byCategory = expenses.groupingBy(Expense::categoryId).fold(0L) { total, expense -> total + expense.amountMinor }
        val byTag = expenses.flatMap { expense -> expense.tags.map { tag -> tag to expense.amountMinor } }
            .groupingBy { it.first }
            .fold(0L) { total, entry -> total + entry.second }
        val byMerchant = merchantTotals(expenses)
        val total = expenses.sumOf { it.amountMinor }
        val aggregationPeriod = aggregationPeriod(expenses, filter.dateRange, zoneId)
        val periodComparison = previousPeriod(filter.dateRange, zoneId)?.let { previousRange ->
            val previousExpenses = snapshot.activeExpenses.filter(
                filter.copy(dateRange = previousRange)::matches
            )
            val previousTotal = previousExpenses.sumOf(Expense::amountMinor)
            val change = total - previousTotal
            PeriodComparison(
                currentTotalMinor = total,
                previousTotalMinor = previousTotal,
                currentTransactionCount = expenses.size,
                previousTransactionCount = previousExpenses.size,
                changeMinor = change,
                changePercent = if (previousTotal == 0L) null else change.toDouble() * 100.0 / previousTotal.toDouble()
            )
        }

        return AnalyticsSnapshot(
            totalMinor = total,
            transactionCount = expenses.size,
            averageMinor = if (expenses.isEmpty()) 0L else total / expenses.size,
            filteredBalance = BalanceCalculator.calculate(snapshot.participants, expenses),
            byParticipant = byParticipant,
            byCategory = byCategory,
            byTag = byTag,
            byMerchant = byMerchant,
            periodComparison = periodComparison,
            aggregationPeriod = aggregationPeriod,
            spendingOverTime = spendingOverTime(
                expenses = expenses,
                period = aggregationPeriod,
                range = filter.dateRange,
                zoneId = zoneId,
                nowMillis = nowMillis
            )
        )
    }

    private fun merchantTotals(expenses: List<Expense>): Map<String, Long> {
        val totals = linkedMapOf<String, Pair<String, Long>>()
        expenses.forEach { expense ->
            val displayName = expense.merchant.trim().replace(Regex("\\s+"), " ")
            if (displayName.isNotEmpty()) {
                val key = displayName.lowercase(Locale.ROOT)
                val existing = totals[key]
                totals[key] = (existing?.first ?: displayName) to ((existing?.second ?: 0L) + expense.amountMinor)
            }
        }
        return totals.values.associate { it.first to it.second }
    }

    private fun previousPeriod(range: DateRange, zoneId: ZoneId): DateRange? {
        val startMillis = range.startMillis ?: return null
        val endMillis = range.endMillis ?: return null
        if (endMillis < startMillis) return null

        return runCatching {
            val startDate = Instant.ofEpochMilli(startMillis).atZone(zoneId).toLocalDate()
            val endDate = Instant.ofEpochMilli(endMillis).atZone(zoneId).toLocalDate()
            val dayCount = ChronoUnit.DAYS.between(startDate, endDate) + 1L
            val previousStart = startDate.minusDays(dayCount)
            val previousEndExclusive = startDate
            DateRange(
                startMillis = previousStart.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                endMillis = previousEndExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L
            )
        }.getOrNull()
    }

    private fun aggregationPeriod(
        expenses: List<Expense>,
        range: DateRange,
        zoneId: ZoneId
    ): AggregationPeriod {
        val start = range.startMillis ?: expenses.minOfOrNull(Expense::createdAtMillis) ?: return AggregationPeriod.DAY
        val end = range.endMillis ?: expenses.maxOfOrNull(Expense::createdAtMillis) ?: start
        val days = java.time.Duration.between(
            Instant.ofEpochMilli(start).atZone(zoneId).toLocalDate().atStartOfDay(),
            Instant.ofEpochMilli(end).atZone(zoneId).toLocalDate().plusDays(1).atStartOfDay()
        ).toDays()
        return when {
            days <= 45L -> AggregationPeriod.DAY
            days <= 180L -> AggregationPeriod.WEEK
            else -> AggregationPeriod.MONTH
        }
    }

    private fun spendingOverTime(
        expenses: List<Expense>,
        period: AggregationPeriod,
        range: DateRange,
        zoneId: ZoneId,
        nowMillis: Long
    ): List<SpendingPoint> {
        val totals = expenses
            .groupingBy { expense -> bucketStart(expense.createdAtMillis, period, zoneId) }
            .fold(0L) { total, expense -> total + expense.amountMinor }
        val rawStart = range.startMillis ?: expenses.minOfOrNull(Expense::createdAtMillis) ?: return emptyList()
        val rawEnd = range.endMillis ?: expenses.maxOfOrNull(Expense::createdAtMillis) ?: rawStart
        val effectiveEnd = if (rawEnd > nowMillis && rawStart <= nowMillis) nowMillis else rawEnd
        if (effectiveEnd < rawStart) return emptyList()
        val start = bucketStart(rawStart, period, zoneId)
        val end = bucketStart(effectiveEnd, period, zoneId)
        val result = mutableListOf<SpendingPoint>()
        var date = start
        while (!date.isAfter(end) && result.size < MAX_TIMELINE_POINTS) {
            result += SpendingPoint(
                startMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                label = when (period) {
                    AggregationPeriod.DAY -> "%02d/%02d".format(date.dayOfMonth, date.monthValue)
                    AggregationPeriod.WEEK -> "%02d/%02d".format(date.dayOfMonth, date.monthValue)
                    AggregationPeriod.MONTH -> "%02d/%d".format(date.monthValue, date.year)
                },
                totalMinor = totals[date] ?: 0L
            )
            date = when (period) {
                AggregationPeriod.DAY -> date.plusDays(1)
                AggregationPeriod.WEEK -> date.plusWeeks(1)
                AggregationPeriod.MONTH -> date.plusMonths(1)
            }
        }
        return result
    }

    private const val MAX_TIMELINE_POINTS = 240

    private fun bucketStart(epochMillis: Long, period: AggregationPeriod, zoneId: ZoneId): LocalDate {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
        return when (period) {
            AggregationPeriod.DAY -> date
            AggregationPeriod.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            AggregationPeriod.MONTH -> date.withDayOfMonth(1)
        }
    }
}

object ExpenseParser {
    fun parseMinorUnits(raw: String): Long? = runCatching {
        val normalized = raw.trim().replace(',', '.')
        if (normalized.isBlank()) return null
        BigDecimal(normalized)
            .setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .longValueExact()
            .takeIf { it in 1L..ExpensePolicy.MAX_AMOUNT_MINOR }
    }.getOrNull()

    fun newId(): String = UUID.randomUUID().toString()
}

fun Long.formatMinorUnits(currency: String): String {
    val sign = if (this < 0L) "-" else ""
    val absolute = kotlin.math.abs(this)
    val major = absolute / 100L
    val minor = (absolute % 100L).toString().padStart(2, '0')
    return "$sign${SupportedCurrencies.symbol(currency)}$major,$minor"
}
