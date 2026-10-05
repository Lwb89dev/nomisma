package com.nomisma.app.data

import android.content.Context
import com.nomisma.app.domain.SavedExpenseFilter
import com.nomisma.app.domain.ExpenseFilterDraft
import com.nomisma.app.domain.TimeRangePreset
import org.json.JSONArray
import org.json.JSONObject

class SavedFilterStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("nomisma_saved_filters", Context.MODE_PRIVATE)

    fun load(): List<SavedExpenseFilter> {
        val raw = preferences.getString(KEY_FILTERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.getJSONObject(index)
                    add(
                        SavedExpenseFilter(
                            id = json.getString("id"),
                            name = json.getString("name"),
                            filter = ExpenseFilterDraft(
                                timeRange = TimeRangePreset.valueOf(json.getString("time_range")),
                                participantIds = json.stringSet("participant_ids", "participant_id"),
                                categoryIds = json.stringSet("category_ids", "category_id"),
                                tags = json.stringSet("tags", "tag"),
                                searchQuery = json.optString("search_query"),
                                customStart = json.optString("custom_start"),
                                customEnd = json.optString("custom_end"),
                                minAmount = json.optString("min_amount"),
                                maxAmount = json.optString("max_amount")
                            )
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun upsert(filter: SavedExpenseFilter): List<SavedExpenseFilter> {
        val updated = (load().filterNot { it.id == filter.id } + filter).takeLast(MAX_FILTERS)
        persist(updated)
        return updated
    }

    fun delete(filterId: String): List<SavedExpenseFilter> {
        val updated = load().filterNot { it.id == filterId }
        persist(updated)
        return updated
    }

    private fun persist(filters: List<SavedExpenseFilter>) {
        val array = JSONArray()
        filters.forEach { filter ->
            array.put(JSONObject().apply {
                put("id", filter.id)
                put("name", filter.name)
                put("time_range", filter.filter.timeRange.name)
                put("participant_ids", JSONArray(filter.filter.participantIds.toList()))
                put("category_ids", JSONArray(filter.filter.categoryIds.toList()))
                put("tags", JSONArray(filter.filter.tags.toList()))
                put("search_query", filter.filter.searchQuery)
                put("custom_start", filter.filter.customStart)
                put("custom_end", filter.filter.customEnd)
                put("min_amount", filter.filter.minAmount)
                put("max_amount", filter.filter.maxAmount)
            })
        }
        preferences.edit().putString(KEY_FILTERS, array.toString()).apply()
    }

    private companion object {
        const val KEY_FILTERS = "filters_json"
        const val MAX_FILTERS = 20
    }
}

private fun JSONObject.stringSet(arrayKey: String, legacyKey: String): Set<String> {
    val array = optJSONArray(arrayKey)
    if (array != null) {
        return buildSet {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }
    return optString(legacyKey).trim().takeIf(String::isNotBlank)?.let(::setOf) ?: emptySet()
}
