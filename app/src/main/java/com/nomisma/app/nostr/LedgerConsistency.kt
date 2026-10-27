package com.nomisma.app.nostr

import com.nomisma.app.domain.Expense
import com.nomisma.app.domain.LedgerSnapshot
import org.json.JSONObject

/**
 * What one device believes the shared ledger is, small enough to send to the other device.
 *
 * [frontier] is a vector clock: for every author, the highest operation sequence this device has
 * folded into its ledger. [stateHash] fingerprints the financial state only (entries, payers,
 * splits, cycles), so two devices that applied the same operations must produce the same hash,
 * whatever order the operations arrived in. [cycleHashes] narrow a mismatch down to a cycle.
 */
data class LedgerDigest(
    val ledgerId: String,
    val authorId: String,
    val frontier: Map<String, Long>,
    val stateHash: String,
    val cycleHashes: Map<String, String>,
    val creditorId: String?,
    val balanceMinor: Long,
    val reply: Boolean,
    val sentAtMillis: Long
) {
    /** Short form both screens show, so people can also compare it by eye. */
    val fingerprint: String get() = stateHash.take(4) + " " + stateHash.substring(4, 8)

    fun toJson(): String = JSONObject().apply {
        put("v", VERSION)
        put("ledger_id", ledgerId)
        put("author_id", authorId)
        put("frontier", JSONObject(frontier))
        put("state", stateHash)
        put("cycles", JSONObject(cycleHashes))
        creditorId?.let { put("creditor", it) }
        put("balance_minor", balanceMinor)
        put("reply", reply)
        put("sent_at_ms", sentAtMillis)
    }.toString()

    companion object {
        const val VERSION = 1
        private const val MAX_ENTRIES = 512

        fun fromJson(raw: String): LedgerDigest {
            require(raw.length <= 65_536) { "Checkpoint is too large" }
            val json = JSONObject(raw)
            require(json.optInt("v") == VERSION) { "Unsupported checkpoint version" }
            val state = json.getString("state")
            require(state.length == 64 && state.all { it in "0123456789abcdef" }) { "Invalid checkpoint hash" }
            return LedgerDigest(
                ledgerId = json.getString("ledger_id"),
                authorId = json.getString("author_id"),
                frontier = json.getJSONObject("frontier").toLongMap(),
                stateHash = state,
                cycleHashes = json.optJSONObject("cycles")?.toStringMap().orEmpty(),
                creditorId = json.optString("creditor").takeIf(String::isNotBlank),
                balanceMinor = json.getLong("balance_minor"),
                reply = json.optBoolean("reply", false),
                sentAtMillis = json.getLong("sent_at_ms")
            )
        }

        private fun JSONObject.toLongMap(): Map<String, Long> {
            require(length() <= MAX_ENTRIES) { "Checkpoint frontier is too large" }
            return keys().asSequence().associateWith { getLong(it) }
        }

        private fun JSONObject.toStringMap(): Map<String, String> {
            require(length() <= MAX_ENTRIES) { "Checkpoint has too many cycles" }
            return keys().asSequence().associateWith { getString(it) }
        }
    }
}

object LedgerDigestCalculator {
    private const val DOMAIN = "nomisma-ledger-digest-v1"

    fun compute(
        snapshot: LedgerSnapshot,
        frontier: Map<String, Long>,
        authorId: String,
        reply: Boolean,
        nowMillis: Long
    ): LedgerDigest {
        val cycleHashes = cycleRows(snapshot).mapValues { (cycleId, rows) ->
            NostrHash.sha256Hex(buildString {
                append(cycleId).append('\n')
                rows.sorted().forEach { append(it).append('\n') }
            })
        }
        val stateHash = NostrHash.sha256Hex(buildString {
            append(DOMAIN).append('\n')
            append(snapshot.id).append('\n')
            append(snapshot.currency).append('\n')
            snapshot.participants.map { it.id }.sorted().forEach { append("p|").append(it).append('\n') }
            cycleHashes.toSortedMap().forEach { (cycleId, hash) -> append("c|").append(cycleId).append('|').append(hash).append('\n') }
        })
        val balance = snapshot.balance()
        return LedgerDigest(
            ledgerId = snapshot.id,
            authorId = authorId,
            frontier = frontier.filterValues { it > 0L },
            stateHash = stateHash,
            cycleHashes = cycleHashes.mapValues { it.value.take(16) },
            creditorId = balance.creditorId,
            balanceMinor = balance.amountMinor,
            reply = reply,
            sentAtMillis = nowMillis
        )
    }

    /** Every financial fact, one canonical line each, grouped by settlement cycle. */
    private fun cycleRows(snapshot: LedgerSnapshot): Map<String, List<String>> {
        val rows = snapshot.expenses.groupBy { it.cycleId }.mapValues { (_, expenses) -> expenses.map(::expenseRow) }
            .toMutableMap()
        snapshot.settlementCycles.forEach { cycle ->
            val header = if (cycle.isClosed) {
                "closed|${cycle.totalMinor}|${cycle.creditorId.orEmpty()}|${cycle.balanceMinor}|${cycle.transactionCount}"
            } else {
                "open"
            }
            rows[cycle.id] = rows[cycle.id].orEmpty() + "cycle|$header"
        }
        return rows
    }

    private fun expenseRow(expense: Expense): String = listOf(
        "e",
        expense.id,
        expense.amountMinor,
        expense.currency,
        expense.authorId,
        if (expense.sharedEvenly) 1 else 0,
        if (expense.deletedAtMillis != null) 1 else 0
    ).joinToString("|")
}

sealed interface ConsistencyVerdict {
    /** Same operations applied and the same financial state: the balances agree. */
    data class Verified(val fingerprint: String) : ConsistencyVerdict

    /** Same operations applied but a different state: a real bug or a rejected operation. */
    data class Diverged(val cycleIds: List<String>) : ConsistencyVerdict

    /**
     * Still syncing. [resendAfter] is set when the partner has not seen all of this device's
     * operations: everything of ours after that sequence goes out again.
     */
    data class Syncing(val resendAfter: Long?, val waitingForPartner: Boolean) : ConsistencyVerdict

    /** The partner is on another ledger altogether. */
    data object OtherLedger : ConsistencyVerdict
}

object LedgerConsistency {
    fun compare(local: LedgerDigest, remote: LedgerDigest): ConsistencyVerdict {
        if (local.ledgerId != remote.ledgerId) return ConsistencyVerdict.OtherLedger
        val authors = local.frontier.keys + remote.frontier.keys
        val sameFrontier = authors.all { local.frontier[it] ?: 0L == remote.frontier[it] ?: 0L }
        if (sameFrontier) {
            if (local.stateHash == remote.stateHash) return ConsistencyVerdict.Verified(local.fingerprint)
            val cycles = (local.cycleHashes.keys + remote.cycleHashes.keys)
                .filter { local.cycleHashes[it] != remote.cycleHashes[it] }
                .sorted()
            return ConsistencyVerdict.Diverged(cycles)
        }
        val mine = local.frontier[local.authorId] ?: 0L
        val seenByPartner = remote.frontier[local.authorId] ?: 0L
        val theirs = remote.frontier[remote.authorId] ?: 0L
        val seenByMe = local.frontier[remote.authorId] ?: 0L
        return ConsistencyVerdict.Syncing(
            resendAfter = seenByPartner.takeIf { it < mine },
            waitingForPartner = seenByMe < theirs
        )
    }
}
