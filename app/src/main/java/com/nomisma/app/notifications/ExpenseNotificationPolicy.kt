package com.nomisma.app.notifications

import com.nomisma.app.domain.Expense
import com.nomisma.app.domain.LedgerSnapshot

object ExpenseNotificationPolicy {
    fun addedExpenses(previous: LedgerSnapshot, current: LedgerSnapshot): List<Expense> {
        if (previous.id != current.id) return emptyList()
        val previousIds = previous.expenses.mapTo(mutableSetOf(), Expense::id)
        return current.expenses.filter { it.id !in previousIds }
    }
}
