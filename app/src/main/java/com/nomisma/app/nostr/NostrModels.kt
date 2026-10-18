package com.nomisma.app.nostr

import fr.acinq.secp256k1.Secp256k1Jni
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object NostrLimits {
    const val MAX_EVENT_CONTENT_LENGTH = 262_144
    const val MAX_SERIALIZED_EVENT_LENGTH = 300_000
    const val MAX_RELAY_MESSAGE_LENGTH = 350_000
    const val MAX_TAGS = 64
    const val MAX_TAG_VALUES = 16
    const val MAX_TAG_VALUE_LENGTH = 2_048
}

data class NostrTag(val values: List<String>) {
    init {
        require(values.isNotEmpty()) { "A Nostr tag cannot be empty" }
        require(values.size <= NostrLimits.MAX_TAG_VALUES) { "A Nostr tag has too many values" }
        require(values.all { it.length <= NostrLimits.MAX_TAG_VALUE_LENGTH }) { "A Nostr tag value is too long" }
    }

    operator fun get(index: Int): String? = values.getOrNull(index)

    fun toJson(): JSONArray = JSONArray().apply { values.forEach(::put) }
}

data class UnsignedNostrEvent(
    val pubkey: String?,
    val createdAt: Long,
    val kind: Int,
    val tags: List<NostrTag>,
    val content: String
) {
    /**
     * NIP-01 serialization hashed into the event id. Built by hand because
     * Android's org.json escapes '/' as "\/", and base64 NIP-44 ciphertext
     * almost always contains '/', so the id would differ from every other client.
     */
    fun canonicalSerialization(pubkeyOverride: String = pubkey.orEmpty()): String = buildString {
        append("[0,")
        append(NostrCanonicalJson.string(pubkeyOverride)).append(',')
        append(createdAt).append(',')
        append(kind).append(',')
        append(tags.joinToString(",", "[", "]") { tag ->
            tag.values.joinToString(",", "[", "]", transform = NostrCanonicalJson::string)
        })
        append(',')
        append(NostrCanonicalJson.string(content))
        append(']')
    }

    fun toSignerJson(): String = JSONObject().apply {
        put("kind", kind)
        put("content", content)
        put("tags", JSONArray().apply { tags.forEach { put(it.toJson()) } })
        put("created_at", createdAt)
        pubkey?.let { put("pubkey", it) }
    }.toString()

    fun toRumorJson(): String = JSONObject().apply {
        put("pubkey", requireNotNull(pubkey) { "Rumor pubkey is required" })
        put("created_at", createdAt)
        put("kind", kind)
        put("tags", JSONArray().apply { tags.forEach { put(it.toJson()) } })
        put("content", content)
    }.toString()

    companion object {
        fun fromJson(raw: String): UnsignedNostrEvent {
            require(raw.length <= NostrLimits.MAX_SERIALIZED_EVENT_LENGTH) { "Serialized event is too large" }
            val json = JSONObject(raw)
            val tagsJson = json.optJSONArray("tags") ?: JSONArray()
            require(tagsJson.length() <= NostrLimits.MAX_TAGS) { "Event has too many tags" }
            val tags = buildList {
                for (i in 0 until tagsJson.length()) {
                    val tagJson = tagsJson.optJSONArray(i) ?: continue
                    add(NostrTag(buildList {
                        for (j in 0 until tagJson.length()) add(tagJson.optString(j))
                    }))
                }
            }
            return UnsignedNostrEvent(
                pubkey = json.getString("pubkey"),
                createdAt = json.getLong("created_at"),
                kind = json.getInt("kind"),
                tags = tags,
                content = json.getString("content")
            )
        }
    }
}

data class NostrEvent(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<NostrTag>,
    val content: String,
    val sig: String
) {
    fun asUnsigned(): UnsignedNostrEvent = UnsignedNostrEvent(pubkey, createdAt, kind, tags, content)

    fun hasConsistentId(): Boolean = id.equals(NostrHash.sha256Hex(asUnsigned().canonicalSerialization(pubkey)), ignoreCase = true)

    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("pubkey", pubkey)
        put("created_at", createdAt)
        put("kind", kind)
        put("tags", JSONArray().apply { tags.forEach { put(it.toJson()) } })
        put("content", content)
        put("sig", sig)
    }.toString()

    companion object {
        fun fromJson(raw: String): NostrEvent {
            require(raw.length <= NostrLimits.MAX_SERIALIZED_EVENT_LENGTH) { "Serialized event is too large" }
            val json = JSONObject(raw)
            val tagsJson = json.optJSONArray("tags") ?: JSONArray()
            require(tagsJson.length() <= NostrLimits.MAX_TAGS) { "Event has too many tags" }
            val tags = buildList {
                for (i in 0 until tagsJson.length()) {
                    val tagJson = tagsJson.optJSONArray(i) ?: continue
                    add(NostrTag(buildList {
                        for (j in 0 until tagJson.length()) add(tagJson.optString(j))
                    }))
                }
            }
            return NostrEvent(
                id = json.getString("id"),
                pubkey = json.getString("pubkey"),
                createdAt = json.getLong("created_at"),
                kind = json.getInt("kind"),
                tags = tags,
                content = json.getString("content"),
                sig = json.getString("sig")
            )
        }
    }
}

/** JSON string escaping as NIP-01 and JSON.stringify do it: '/' and non-ASCII stay verbatim. */
object NostrCanonicalJson {
    fun string(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { appendEscaped(it) }
        append('"')
    }

    private fun StringBuilder.appendEscaped(character: Char) {
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> if (character < ' ') append("\\u%04x".format(character.code)) else append(character)
        }
    }
}

object NostrKinds {
    const val LEDGER_OPERATION = 7777
    const val SEAL = 13
    const val GIFT_WRAP = 1059
}

object NostrHash {
    fun sha256(value: String): ByteArray = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))

    fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

object NostrHex {
    fun decode(value: String): ByteArray {
        require(value.length % 2 == 0) { "Hex value has odd length" }
        return ByteArray(value.length / 2) { index ->
            val high = value[index * 2].digitToIntOrNull(16) ?: error("Invalid hex value")
            val low = value[index * 2 + 1].digitToIntOrNull(16) ?: error("Invalid hex value")
            ((high shl 4) or low).toByte()
        }
    }

    fun encode(value: ByteArray): String = value.joinToString("") { byte -> "%02x".format(byte) }
}

object NostrEventValidator {
    fun validateShape(event: NostrEvent): Result<Unit> = runCatching {
        require(event.id.length == 64) { "Invalid event id length" }
        require(event.pubkey.length == 64) { "Invalid pubkey length" }
        require(event.sig.length == 128) { "Invalid signature length" }
        require(event.kind in 0..65535) { "Invalid event kind" }
        require(event.content.length <= NostrLimits.MAX_EVENT_CONTENT_LENGTH) { "Event content is too large" }
        require(event.tags.size <= NostrLimits.MAX_TAGS) { "Event has too many tags" }
        require(event.tags.all { tag ->
            tag.values.isNotEmpty() &&
                tag.values.size <= NostrLimits.MAX_TAG_VALUES &&
                tag.values.all { it.length <= NostrLimits.MAX_TAG_VALUE_LENGTH }
        }) { "Event tags are invalid" }
        require(event.id.all { it.digitToIntOrNull(16) != null }) { "Invalid event id hex" }
        require(event.pubkey.all { it.digitToIntOrNull(16) != null }) { "Invalid event pubkey hex" }
        require(event.sig.all { it.digitToIntOrNull(16) != null }) { "Invalid event signature hex" }
        require(event.hasConsistentId()) { "Event id does not match canonical event data" }
    }

    fun validate(event: NostrEvent): Result<Unit> = runCatching {
        validateShape(event).getOrThrow()
        require(
            Secp256k1Jni.verifySchnorr(
                NostrHex.decode(event.sig),
                NostrHash.sha256(event.asUnsigned().canonicalSerialization(event.pubkey)),
                NostrHex.decode(event.pubkey)
            )
        ) { "Invalid Schnorr signature" }
    }
}

data class RelayFilter(
    val authors: List<String> = emptyList(),
    val kinds: List<Int> = emptyList(),
    val recipientPubkey: String? = null,
    val since: Long? = null,
    val limit: Int? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        if (authors.isNotEmpty()) put("authors", JSONArray(authors))
        if (kinds.isNotEmpty()) put("kinds", JSONArray(kinds))
        recipientPubkey?.let { put("#p", JSONArray().put(it)) }
        since?.let { put("since", it) }
        limit?.let { put("limit", it) }
    }
}
