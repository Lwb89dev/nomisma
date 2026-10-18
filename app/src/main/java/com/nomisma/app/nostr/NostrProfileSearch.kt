package com.nomisma.app.nostr

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import java.util.UUID

/**
 * Finds the other participant by profile name instead of a pasted key.
 *
 * Name search is NIP-50 and only some relays implement it, so a couple of search relays
 * are asked in parallel. Every profile is signature-checked before it is shown, and a
 * NIP-05 check is run on the best matches because many profiles can share one name.
 */
class NostrProfileSearch(
    private val searchRelays: List<String> = DEFAULT_SEARCH_RELAYS,
    private val lookupRelays: List<String> = DEFAULT_LOOKUP_RELAYS,
    private val nip05: Nip05Verifier = Nip05Verifier(),
    private val relayTimeoutMillis: Long = 5_000L
) {
    suspend fun search(rawQuery: String, exclude: Set<String> = emptySet()): List<NostrProfile> {
        val query = rawQuery.trim().removePrefix("nostr:")
        if (query.length < MIN_QUERY_LENGTH) return emptyList()

        directKey(query)?.let { pubkey ->
            return if (pubkey in exclude) emptyList() else listOf(lookup(pubkey) ?: bare(pubkey))
        }
        if ('@' in query) {
            nip05.resolve(query)?.let { pubkey ->
                val profile = lookup(pubkey) ?: bare(pubkey)
                return listOf(profile.copy(nip05 = Nip05.parse(query)?.toString(), nip05Verified = true))
                    .filter { it.pubkey !in exclude }
            }
        }

        val events = coroutineScope {
            searchRelays.map { relay ->
                async { fetch(relay, RelayFilter(kinds = listOf(0), search = query, limit = SEARCH_LIMIT)) }
            }.awaitAll().flatten()
        }
        val candidates = ProfileRanking.rank(query, events.mapNotNull(NostrProfileParser::parse), exclude)
        return ProfileRanking.rank(query, verify(candidates), exclude)
    }

    /** The newest profile of one key, or null when no relay knows it. */
    suspend fun lookup(pubkey: String): NostrProfile? {
        val filter = RelayFilter(authors = listOf(pubkey), kinds = listOf(0), limit = 1)
        val events = coroutineScope {
            (lookupRelays + searchRelays).distinct().map { relay -> async { fetch(relay, filter) } }.awaitAll().flatten()
        }
        val profile = events.filter { it.pubkey.equals(pubkey, ignoreCase = true) }
            .mapNotNull(NostrProfileParser::parse)
            .maxByOrNull { it.createdAt } ?: return null
        return verify(listOf(profile)).first()
    }

    private suspend fun verify(profiles: List<NostrProfile>): List<NostrProfile> = coroutineScope {
        profiles.map { profile ->
            async {
                val identifier = profile.nip05
                if (identifier == null) profile else profile.copy(nip05Verified = nip05.verify(identifier, profile.pubkey))
            }
        }.awaitAll()
    }

    private suspend fun fetch(relayUrl: String, filter: RelayFilter): List<NostrEvent> {
        val events = Collections.synchronizedList(mutableListOf<NostrEvent>())
        try {
            val relay = NostrRelayClient(relayUrl)
            try {
                coroutineScope {
                    val subscriptionId = UUID.randomUUID().toString()
                    val finished = CompletableDeferred<Unit>()
                    // The relay flow has no replay, so listening must start before the REQ goes out.
                    val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                        relay.messages.collect { message ->
                            when (message) {
                                is RelayMessage.Event ->
                                    if (message.subscriptionId == subscriptionId) events += message.event
                                is RelayMessage.EndOfStoredEvents ->
                                    if (message.subscriptionId == subscriptionId) finished.complete(Unit)
                                is RelayMessage.Failure, RelayMessage.Closed -> finished.complete(Unit)
                                else -> Unit
                            }
                        }
                    }
                    relay.connectAndAwait(CONNECT_TIMEOUT_MILLIS)
                    relay.subscribe(subscriptionId, filter)
                    withTimeoutOrNull(relayTimeoutMillis) { finished.await() }
                    collector.cancel()
                }
            } finally {
                relay.close()
            }
        } catch (failure: Exception) {
            // A slow or broken relay only costs its own results; real cancellation still propagates.
            currentCoroutineContext().ensureActive()
        }
        return events.toList().filter { it.kind == 0 && NostrEventValidator.validate(it).isSuccess }
    }

    private fun directKey(query: String): String? {
        Nip19.npubToHex(query)?.let { return it }
        val hex = query.lowercase()
        return hex.takeIf { it.length == 64 && it.all { c -> c in "0123456789abcdef" } }
    }

    private fun bare(pubkey: String) = NostrProfile(pubkey, null, null, null, 0L)

    companion object {
        const val MIN_QUERY_LENGTH = 2
        private const val SEARCH_LIMIT = 30
        private const val CONNECT_TIMEOUT_MILLIS = 4_000L

        val DEFAULT_SEARCH_RELAYS = listOf("wss://relay.ditto.pub", "wss://search.nos.today")
        val DEFAULT_LOOKUP_RELAYS = listOf("wss://relay.damus.io", "wss://purplepag.es", "wss://nos.lol")
    }
}
