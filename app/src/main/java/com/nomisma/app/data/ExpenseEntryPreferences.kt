package com.nomisma.app.data

import android.content.Context
import com.nomisma.app.security.securePreferences
import org.json.JSONArray
import org.json.JSONObject

data class ExpenseEntryRecents(
    val categoryIds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val participantId: String? = null
)

object ExpenseEntryRecentsPolicy {
    fun record(
        current: ExpenseEntryRecents,
        categoryId: String,
        tags: List<String>,
        participantId: String
    ): ExpenseEntryRecents = ExpenseEntryRecents(
        categoryIds = promote(categoryId, current.categoryIds, MAX_CATEGORIES),
        tags = tags.asReversed().fold(current.tags) { ordered, tag ->
            promote(tag, ordered, MAX_TAGS)
        },
        participantId = participantId
    )

    private fun promote(value: String, current: List<String>, maximum: Int): List<String> =
        (listOf(value.trim()) + current.filterNot { it == value.trim() })
            .filter(String::isNotBlank)
            .take(maximum)

    private const val MAX_CATEGORIES = 6
    private const val MAX_TAGS = 10
}

class ExpenseEntryPreferences(context: Context) {
    private val preferences = context.applicationContext
        .securePreferences("nomisma_expense_entry")

    fun load(): ExpenseEntryRecents {
        val raw = preferences.getString(KEY_RECENTS, null) ?: return ExpenseEntryRecents()
        return runCatching {
            val json = JSONObject(raw)
            ExpenseEntryRecents(
                categoryIds = json.getStringList("category_ids"),
                tags = json.getStringList("tags"),
                participantId = json.optString("participant_id").takeIf(String::isNotBlank)
            )
        }.getOrDefault(ExpenseEntryRecents())
    }

    fun record(categoryId: String, tags: List<String>, participantId: String): ExpenseEntryRecents {
        val updated = ExpenseEntryRecentsPolicy.record(load(), categoryId, tags, participantId)
        val json = JSONObject().apply {
            put("category_ids", JSONArray(updated.categoryIds))
            put("tags", JSONArray(updated.tags))
            put("participant_id", updated.participantId.orEmpty())
        }
        preferences.edit().putString(KEY_RECENTS, json.toString()).apply()
        return updated
    }

    private companion object {
        const val KEY_RECENTS = "recents_json"
    }
}

private fun JSONObject.getStringList(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
        }
    }
}
