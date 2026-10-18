package com.nomisma.app.nostr

import org.json.JSONObject

/** A kind-0 profile as published by its owner. Every text field is self-declared and untrusted. */
data class NostrProfile(
    val pubkey: String,
    val name: String?,
    val displayName: String?,
    val nip05: String?,
    val createdAt: Long,
    val nip05Verified: Boolean = false
) {
    val label: String get() = displayName ?: name ?: pubkeyFingerprint(pubkey)
    val npub: String get() = Nip19.hexToNpub(pubkey)
}

object ProfileText {
    private val dropped = setOf(
        Character.CONTROL, Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR,
        Character.PRIVATE_USE, Character.UNASSIGNED, Character.SURROGATE
    ).map(Byte::toInt).toSet()

    /**
     * Profile text is attacker-controlled and ends up on screen next to a security decision.
     * Bidi overrides, zero-width joiners and other invisible characters are removed so two
     * profiles cannot look identical while being different strings.
     */
    fun clean(raw: String?, maxLength: Int = 48): String? {
        if (raw == null) return null
        val visible = raw.replace(Regex("\\s+"), " ").filter { Character.getType(it) !in dropped }
        return visible.trim().take(maxLength).trim().takeIf(String::isNotEmpty)
    }
}

object NostrProfileParser {
    fun parse(event: NostrEvent): NostrProfile? {
        if (event.kind != 0) return null
        val json = runCatching { JSONObject(event.content) }.getOrNull() ?: return null
        fun text(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { (json.opt(it) as? String)?.takeIf(String::isNotBlank) }
                ?.let(ProfileText::clean)

        val name = text("name", "username")
        val displayName = text("display_name", "displayName")
        val nip05 = text("nip05")?.lowercase()?.takeIf { Nip05.parse(it) != null }
        if (name == null && displayName == null && nip05 == null) return null
        return NostrProfile(event.pubkey.lowercase(), name, displayName, nip05, event.createdAt)
    }
}

object ProfileRanking {
    /**
     * Keeps the newest profile per key, drops the ones that do not actually contain the
     * query in a name or NIP-05 (relay full-text search also matches bios), and orders by how
     * exactly the name matches, then by NIP-05 verification.
     */
    fun rank(
        query: String,
        profiles: Collection<NostrProfile>,
        exclude: Set<String> = emptySet(),
        limit: Int = 8
    ): List<NostrProfile> {
        val tokens = normalize(query.removePrefix("@")).split(' ').filter(String::isNotEmpty)
        if (tokens.isEmpty()) return emptyList()
        val whole = tokens.joinToString("")
        return profiles
            .groupBy { it.pubkey }
            .mapNotNull { (_, versions) -> versions.maxByOrNull { it.createdAt } }
            .filter { it.pubkey !in exclude }
            .mapNotNull { profile -> tier(profile, tokens, whole)?.let { profile to it } }
            .sortedWith(
                compareBy<Pair<NostrProfile, Int>> { it.second }
                    .thenByDescending { it.first.nip05Verified }
                    .thenByDescending { it.first.nip05 != null }
                    .thenByDescending { it.first.createdAt }
            )
            .map { it.first }
            .take(limit)
    }

    private fun tier(profile: NostrProfile, tokens: List<String>, whole: String): Int? {
        val names = listOfNotNull(profile.displayName, profile.name, profile.nip05?.substringBefore('@'))
            .map { normalize(it).replace(" ", "") }
        val haystack = listOfNotNull(profile.displayName, profile.name, profile.nip05)
            .joinToString(" ") { normalize(it) }
        return when {
            names.any { it == whole } -> 0
            names.any { it.startsWith(whole) } -> 1
            tokens.all { haystack.replace(" ", "").contains(it) } -> 2
            else -> null
        }
    }

    private fun normalize(value: String): String =
        value.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
            .replace(Regex("\\s+"), " ").trim()
}
