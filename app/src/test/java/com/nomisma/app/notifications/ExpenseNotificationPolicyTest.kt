package com.nomisma.app.notifications

import com.nomisma.app.domain.Expense
import com.nomisma.app.domain.LedgerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseNotificationPolicyTest {
    @Test
    fun returnsOnlyExpensesAddedToTheSameLedger() {
        val existing = expense("existing")
        val added = expense("added")
        val previous = snapshot(listOf(existing))
        val current = snapshot(listOf(existing.copy(description = "Modificata"), added))

        assertEquals(
            listOf("added"),
            ExpenseNotificationPolicy.addedExpenses(previous, current).map(Expense::id)
        )
    }

    @Test
    fun ignoresAdoptionOfAnotherLedger() {
        val current = snapshot(listOf(expense("added")), ledgerId = "other")

        assertTrue(ExpenseNotificationPolicy.addedExpenses(snapshot(emptyList()), current).isEmpty())
    }

    private fun expense(id: String) = Expense(
        id = id,
        ledgerId = "ledger",
        authorId = "alice",
        amountMinor = 1_250L,
        currency = "EUR",
        description = "Spesa",
        categoryId = "groceries",
        tags = emptyList(),
        createdAtMillis = 1_700_000_000_000L
    )

    private fun snapshot(expenses: List<Expense>, ledgerId: String = "ledger") = LedgerSnapshot(
        id = ledgerId,
        title = "Casa",
        currency = "EUR",
        capacityMinor = 100_000L,
        participants = emptyList(),
        expenses = expenses,
        themeId = "vessel",
        themeVersion = 1
    )
}
