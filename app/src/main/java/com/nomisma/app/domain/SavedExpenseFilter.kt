package com.nomisma.app.domain

import java.io.Serializable
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class ExpenseFilterDraft(
    val timeRange: TimeRangePreset = TimeRangePreset.ALL_TIME,
    val participantIds: Set<String> = emptySet(),
    val categoryIds: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val searchQuery: String = "",
    val customStart: String = "",
    val customEnd: String = "",
    val minAmount: String = "",
    val maxAmount: String = ""
) : Serializable {
    val activeCriteriaCount: Int
        get() = listOf(
            timeRange != TimeRangePreset.ALL_TIME,
            participantIds.isNotEmpty(),
            categoryIds.isNotEmpty(),
            tags.isNotEmpty(),
            searchQuery.isNotBlank(),
            minAmount.isNotBlank(),
            maxAmount.isNotBlank()
        ).count { it }

    fun validationError(zoneId: ZoneId = ZoneId.systemDefault()): String? {
        val start = parseDate(customStart, zoneId, endOfDay = false)
        val end = parseDate(customEnd, zoneId, endOfDay = true)
        if (timeRange == TimeRangePreset.CUSTOM) {
            if (customStart.isNotBlank() && start == null) return "Data iniziale non valida: usa AAAA-MM-GG"
            if (customEnd.isNotBlank() && end == null) return "Data finale non valida: usa AAAA-MM-GG"
            if (start != null && end != null && start > end) return "La data iniziale deve precedere quella finale"
        }
        val minimum = minAmount.takeIf(String::isNotBlank)?.let(ExpenseParser::parseMinorUnits)
        val maximum = maxAmount.takeIf(String::isNotBlank)?.let(ExpenseParser::parseMinorUnits)
        if (minAmount.isNotBlank() && minimum == null) return "Importo minimo non valido"
        if (maxAmount.isNotBlank() && maximum == null) return "Importo massimo non valido"
        if (minimum != null && maximum != null && minimum > maximum) return "L'importo minimo supera il massimo"
        return null
    }

    fun toExpenseFilter(
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ExpenseFilter {
        val invalid = validationError(zoneId) != null
        val customRange = DateRange(
            startMillis = parseDate(customStart, zoneId, endOfDay = false),
            endMillis = parseDate(customEnd, zoneId, endOfDay = true)
        )
        return ExpenseFilter(
            participants = participantIds,
            categories = categoryIds,
            tags = tags,
            dateRange = if (invalid) {
                DateRange(Long.MAX_VALUE, Long.MIN_VALUE)
            } else {
                DateRangeResolver.resolve(timeRange, nowMillis, zoneId, customRange)
            },
            minAmountMinor = minAmount.takeIf(String::isNotBlank)?.let(ExpenseParser::parseMinorUnits),
            maxAmountMinor = maxAmount.takeIf(String::isNotBlank)?.let(ExpenseParser::parseMinorUnits),
            searchQuery = searchQuery
        )
    }

    private fun parseDate(raw: String, zoneId: ZoneId, endOfDay: Boolean): Long? {
        if (raw.isBlank()) return null
        return runCatching {
            val date = LocalDate.parse(raw.trim())
            val boundary = if (endOfDay) date.plusDays(1) else date
            boundary.atStartOfDay(zoneId).toInstant().toEpochMilli() - if (endOfDay) 1L else 0L
        }.getOrNull()
    }
}

data class SavedExpenseFilter(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val filter: ExpenseFilterDraft
)
