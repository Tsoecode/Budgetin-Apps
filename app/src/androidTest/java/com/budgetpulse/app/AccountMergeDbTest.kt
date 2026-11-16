package com.budgetpulse.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budgetpulse.app.data.local.AppDatabase
import com.budgetpulse.app.data.local.toDomain
import com.budgetpulse.app.data.local.toEntity
import com.budgetpulse.app.data.repository.AccountDeleteResult
import com.budgetpulse.app.data.repository.AccountRepository
import com.budgetpulse.app.domain.logic.BudgetCalculator
import com.budgetpulse.app.domain.model.Account
import com.budgetpulse.app.domain.model.AccountType
import com.budgetpulse.app.domain.model.Category
import com.budgetpulse.app.domain.model.Transaction
import com.budgetpulse.app.domain.model.TxType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountMergeDbTest {
    private lateinit var db: AppDatabase
    private lateinit var accounts: AccountRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accounts = AccountRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun mergePreservesBalanceAndReassignsTransactionHistory() = runBlocking {
        val sourceId = accounts.add(Account(name = "Cash", type = AccountType.CASH, colorArgb = 1, initialBalance = 100.0))
        val targetId = accounts.add(Account(name = "Bank", type = AccountType.BANK, colorArgb = 2, initialBalance = 25.0))
        val income = Transaction(accountId = sourceId, type = TxType.INCOME, amount = 50.0, category = Category.SALARY, note = "Pay", timestamp = 2)
        val expense = Transaction(accountId = sourceId, type = TxType.EXPENSE, amount = 20.0, category = Category.FOOD, note = "Lunch", timestamp = 1)
        db.transactionDao().insert(income.toEntity())
        db.transactionDao().insert(expense.toEntity())

        val sourceBalance = BudgetCalculator.accountBalance(100.0, listOf(income, expense))
        val totalBefore = sourceBalance + 25.0
        assertEquals(AccountDeleteResult.Deleted, accounts.deleteOrMerge(sourceId, targetId))

        val remainingAccounts = accounts.observeAll().first()
        assertEquals(listOf(targetId), remainingAccounts.map { it.id })
        assertEquals(125.0, remainingAccounts.single().initialBalance, 0.0)
        val movedTransactions = db.transactionDao().observeByAccountId(targetId).first()
        assertEquals(2, movedTransactions.size)
        assertTrue(movedTransactions.all { it.accountId == targetId })
        assertEquals(totalBefore, BudgetCalculator.accountBalance(125.0, movedTransactions.map { it.toDomain() }), 0.0)
    }

    @Test
    fun lastRemainingAccountCannotBeDeleted() = runBlocking {
        val onlyId = accounts.add(Account(name = "Only", type = AccountType.CASH, colorArgb = 1))
        assertEquals(AccountDeleteResult.LastAccount, accounts.deleteOrMerge(onlyId))
        assertEquals(onlyId, accounts.observeAll().first().single().id)
    }

    @Test
    fun unusedZeroBalanceAccountCanBeDeletedWithoutDestination() = runBlocking {
        val removableId = accounts.add(Account(name = "Unused", type = AccountType.CASH, colorArgb = 1))
        accounts.add(Account(name = "Keep", type = AccountType.BANK, colorArgb = 2))
        assertEquals(AccountDeleteResult.Deleted, accounts.deleteOrMerge(removableId))
        assertEquals(1, accounts.observeAll().first().size)
    }
}
