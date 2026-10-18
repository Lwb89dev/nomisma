package com.nomisma.app.domain

import org.json.JSONObject

enum class OperationType(val wireValue: String) {
    ENTRY_CREATE("entry.create"),
    ENTRY_UPDATE("entry.update"),
    ENTRY_DELETE("entry.delete"),
    CATEGORY_CREATE("category.create"),
    CATEGORY_RENAME("category.rename"),
    CATEGORY_ARCHIVE("category.archive"),
    TAG_CREATE("tag.create"),
    TAG_RENAME("tag.rename"),
    TAG_ARCHIVE("tag.archive"),
    LEDGER_METADATA_SET("ledger.metadata.set"),
    LEDGER_CAPACITY_SET("ledger.capacity.set"),
    THEME_SET("theme.set"),
    PARTICIPANT_COLOR_SET("participant.color.set"),
    PARTICIPANT_NAME_SET("participant.name.set"),
    PARTICIPANT_THEME_IDENTITY_SET("participant.theme_identity.set"),
    SETTLEMENT_PROPOSE("settlement.propose"),
    SETTLEMENT_ACCEPT("settlement.accept")
}

data class LedgerOperation(
    val operationId: String,
    val ledgerId: String,
    val authorId: String,
    val authorSequence: Long,
    val type: OperationType,
    val createdAtMillis: Long,
    val payloadJson: String,
    val authorPubkey: String? = null,
    val transportEventId: String? = null,
    val syncState: SyncState = SyncState.OUTBOX
) {
    fun toWireJson(): String = JSONObject().apply {
        put("v", 1)
        put("op_id", operationId)
        put("ledger_id", ledgerId)
        put("author_id", authorId)
        authorPubkey?.let { put("author_pubkey", it) }
        put("author_sequence", authorSequence)
        put("type", type.wireValue)
        put("created_at_ms", createdAtMillis)
        put("payload", JSONObject(payloadJson))
    }.toString()

    companion object {
        private const val MAX_WIRE_LENGTH = 65_536
        private const val MAX_ID_LENGTH = 200

        fun fromWireJson(raw: String, fallbackAuthorId: String? = null): LedgerOperation {
            require(raw.length <= MAX_WIRE_LENGTH) { "Operation payload is too large" }
            val json = JSONObject(raw)
            require(json.optInt("v", 0) == 1) { "Unsupported operation version" }

            val operationId = json.getString("op_id").trim()
            val ledgerId = json.getString("ledger_id").trim()
            val authorId = json.optString("author_id", fallbackAuthorId.orEmpty()).trim()
            val authorPubkey = json.optString("author_pubkey").trim().takeIf { it.isNotBlank() }
            val typeValue = json.getString("type")
            val type = OperationType.entries.firstOrNull { it.wireValue == typeValue }
                ?: error("Unsupported operation type: $typeValue")
            val payload = json.getJSONObject("payload")

            require(operationId.isNotBlank()) { "Operation id is empty" }
            require(operationId.length <= MAX_ID_LENGTH) { "Operation id is too long" }
            require(ledgerId.isNotBlank()) { "Ledger id is empty" }
            require(ledgerId.length <= MAX_ID_LENGTH) { "Ledger id is too long" }
            require(authorId.isNotBlank()) { "Author id is empty" }
            require(authorId.length <= MAX_ID_LENGTH) { "Author id is too long" }
            authorPubkey?.let {
                require(it.length == 64 && it.all { character -> character.digitToIntOrNull(16) != null }) {
                    "Author pubkey is invalid"
                }
            }
            require(json.getLong("author_sequence") > 0L) { "Author sequence must be positive" }
            require(json.getLong("created_at_ms") > 0L) { "Operation timestamp must be positive" }

            return LedgerOperation(
                operationId = operationId,
                ledgerId = ledgerId,
                authorId = authorId,
                authorSequence = json.getLong("author_sequence"),
                type = type,
                createdAtMillis = json.getLong("created_at_ms"),
                payloadJson = payload.toString(),
                authorPubkey = authorPubkey,
                syncState = SyncState.APPLIED
            )
        }
    }
}

enum class SyncState {
    LOCAL_ONLY,
    OUTBOX,
    PUBLISHED,
    APPLIED,
    DEFERRED,
    REJECTED
}

enum class SequenceDisposition { STALE, EXPECTED, DEFER }

object OperationSequencePolicy {
    fun classify(currentSequence: Long, incomingSequence: Long): SequenceDisposition {
        require(currentSequence >= 0L) { "Current sequence cannot be negative" }
        require(incomingSequence > 0L) { "Incoming sequence must be positive" }
        return when {
            incomingSequence <= currentSequence -> SequenceDisposition.STALE
            incomingSequence == currentSequence + 1L -> SequenceDisposition.EXPECTED
            else -> SequenceDisposition.DEFER
        }
    }
}

/**
 * Decides which unsynced local operations do not stop the device from adopting the ledger of a
 * pairing invite. Only plain configuration qualifies: it can be dropped without losing anything
 * the user entered, whereas an entry, category, tag or settlement would be lost for good.
 */
object PairingAdoptionPolicy {
    private val discardableTypes = setOf(
        OperationType.THEME_SET,
        OperationType.PARTICIPANT_COLOR_SET,
        OperationType.PARTICIPANT_NAME_SET,
        OperationType.PARTICIPANT_THEME_IDENTITY_SET,
        OperationType.LEDGER_METADATA_SET,
        OperationType.LEDGER_CAPACITY_SET
    )

    fun isDiscardable(wireType: String): Boolean =
        OperationType.entries.firstOrNull { it.wireValue == wireType } in discardableTypes

    fun blocking(wireTypes: Collection<String>): List<String> =
        wireTypes.distinct().filterNot(::isDiscardable)
}
