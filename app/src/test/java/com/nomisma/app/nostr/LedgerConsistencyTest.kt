package com.nomisma.app.nostr

import com.nomisma.app.domain.Expense
import com.nomisma.app.domain.LedgerSnapshot
import com.nomisma.app.domain.Participant
import com.nomisma.app.domain.SettlementCycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerConsistencyTest {
    private val cycle = SettlementCycle("L:cycle:0", "L", 1L, null, 0L, null, 0L, 0, null)

    private fun expense(id: String, amount: Long, payer: String, shared: Boolean = true, deleted: Boolean = false) =
        Expense(
            id = id, ledgerId = "L", authorId = payer, amountMinor = amount, currency = "EUR",
            description = "d-$id", categoryId = "food", tags = emptyList(), createdAtMillis = 1L,
            cycleId = cycle.id, sharedEvenly = shared, deletedAtMillis = if (deleted) 2L else null
        )

    private fun snapshot(vararg expenses: Expense) = LedgerSnapshot(
        id = "L", title = "Casa", currency = "EUR", capacityMinor = 100_000L,
        participants = listOf(Participant("alice", "A", "#fff", "bear"), Participant("bob", "B", "#000", "squirrel")),
        expenses = expenses.toList(), themeId = "vessel", themeVersion = 1, settlementCycles = listOf(cycle)
    )

    private fun digest(snapshot: LedgerSnapshot, author: String, frontier: Map<String, Long>) =
        LedgerDigestCalculator.compute(snapshot, frontier, author, reply = false, nowMillis = 10L)

    private val five = expense("e1", 500, "alice")
    private val twelve = expense("e2", 1200, "bob")

    @Test
    fun sameOperationsInAnyOrderGiveTheSameFingerprintAndAreVerified() {
        val frontier = mapOf("alice" to 1L, "bob" to 1L)
        val onPixel = digest(snapshot(five, twelve), "alice", frontier)
        val onXiaomi = digest(snapshot(twelve, five.copy(description = "edited text only")), "bob", frontier)

        assertEquals(onPixel.stateHash, onXiaomi.stateHash)
        assertEquals(ConsistencyVerdict.Verified(onPixel.fingerprint), LedgerConsistency.compare(onPixel, onXiaomi))
        assertEquals(ConsistencyVerdict.Verified(onXiaomi.fingerprint), LedgerConsistency.compare(onXiaomi, onPixel))
        assertEquals("bob", onPixel.creditorId)
        assertEquals(350L, onPixel.balanceMinor)
    }

    @Test
    fun anyFinancialDifferenceAtTheSameFrontierIsADivergenceInThatCycle() {
        val frontier = mapOf("alice" to 1L, "bob" to 1L)
        val base = digest(snapshot(five, twelve), "alice", frontier)
        listOf(
            five.copy(amountMinor = 501),
            five.copy(authorId = "bob"),
            five.copy(sharedEvenly = false),
            five.copy(deletedAtMillis = 3L)
        ).forEach { changed ->
            val other = digest(snapshot(changed, twelve), "bob", frontier)
            assertNotEquals(base.stateHash, other.stateHash)
            assertEquals(ConsistencyVerdict.Diverged(listOf(cycle.id)), LedgerConsistency.compare(base, other))
        }
        val missingEntry = digest(snapshot(twelve), "bob", frontier)
        assertTrue(LedgerConsistency.compare(base, missingEntry) is ConsistencyVerdict.Diverged)
    }

    @Test
    fun differentFrontiersAreSyncingAndTellWhoMustResendWhat() {
        val pixel = digest(snapshot(five, twelve), "alice", mapOf("alice" to 3L, "bob" to 1L))
        val xiaomi = digest(snapshot(twelve), "bob", mapOf("alice" to 1L, "bob" to 2L))

        assertEquals(
            ConsistencyVerdict.Syncing(resendAfter = 1L, waitingForPartner = true),
            LedgerConsistency.compare(pixel, xiaomi)
        )
        assertEquals(
            ConsistencyVerdict.Syncing(resendAfter = 1L, waitingForPartner = true),
            LedgerConsistency.compare(xiaomi, pixel)
        )
        val caughtUp = digest(snapshot(five, twelve), "bob", mapOf("alice" to 3L))
        assertEquals(
            ConsistencyVerdict.Syncing(resendAfter = null, waitingForPartner = false),
            LedgerConsistency.compare(pixel, caughtUp)
        )
    }

    @Test
    fun checkpointsSurviveTheWireAndOtherLedgersAreFlagged() {
        val original = digest(snapshot(five, twelve), "alice", mapOf("alice" to 2L, "bob" to 1L)).copy(reply = true)
        val parsed = LedgerDigest.fromJson(original.toJson())
        assertEquals(original, parsed)
        assertEquals(ConsistencyVerdict.OtherLedger, LedgerConsistency.compare(original, parsed.copy(ledgerId = "M")))
        assertTrue(runCatching { LedgerDigest.fromJson(original.toJson().replace(original.stateHash, "zz")) }.isFailure)
    }
}
