package com.budgetpulse.app

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budgetpulse.app.data.local.AccountEntity
import com.budgetpulse.app.data.local.AppDatabase
import com.budgetpulse.app.data.local.BudgetEntity
import com.budgetpulse.app.data.local.GoalEntity
import com.budgetpulse.app.data.local.TransactionEntity
import com.budgetpulse.app.domain.model.AccountType
import com.budgetpulse.app.domain.model.Category
import com.budgetpulse.app.domain.model.TxSource
import com.budgetpulse.app.domain.model.TxType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room test: the currency switch must convert every table, and a failure must change nothing. */
@RunWith(AndroidJUnit4::class)
class CurrencyConversionDbTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(): Long {
        val account = db.accountDao().insert(AccountEntity(name = "Bank", type = AccountType.BANK, colorArgb = 0xFF3B82C4, initialBalance = 100.0))
        fun tx(amount: Double, type: TxType = TxType.EXPENSE) = TransactionEntity(
            accountId = account, type = type, amount = amount, category = Category.FOOD,
            note = "n", timestamp = amount.toLong(), source = TxSource.MANUAL, externalId = null
        )
        db.transactionDao().insert(tx(12.5))
        db.transactionDao().insert(tx(3.17))
        db.transactionDao().insert(tx(2000.0, TxType.INCOME))
        db.budgetDao().upsert(BudgetEntity(Category.FOOD, 180.0))
        db.goalDao().insert(GoalEntity(name = "Laptop", target = 900.0, saved = 250.5))
        return account
    }

    @Test
    fun convertAll_updatesTransactionsAccountsLimitsAndGoals() = runBlocking {
        seed()
        val factor = 16000.0   // 1 USD = 16,000 IDR
        db.withTransaction {
            assertEquals(3, db.transactionDao().convertAll(factor, 0))
            assertEquals(1, db.accountDao().convertAll(factor, 0))
            assertEquals(1, db.budgetDao().convertAll(factor, 0))
            assertEquals(1, db.goalDao().convertAll(factor, 0))
        }
        val amounts = db.transactionDao().observeAll().first().map { it.amount }.sorted()
        assertEquals(listOf(50720.0, 200000.0, 32000000.0), amounts)
        assertEquals(1600000.0, db.accountDao().observeAll().first().single().initialBalance, 0.0)
        assertEquals(2880000.0, db.budgetDao().observeAll().first().single().monthlyLimit, 0.0)
        val goal = db.goalDao().observeAll().first().single()
        assertEquals(14400000.0, goal.target, 0.0)
        assertEquals(4008000.0, goal.saved, 0.0)
    }

    @Test
    fun convertAll_roundsToTwoDecimalsForCentCurrencies() = runBlocking {
        seed()
        db.transactionDao().convertAll(1.0 / 16000.0, 2)   // IDR -> USD style
        val smallest = db.transactionDao().observeAll().first().map { it.amount }.min()
        assertEquals(0.0, smallest * 100 - Math.round(smallest * 100), 1e-6)   // at most 2 decimals
    }

    @Test
    fun aFailureInsideTheTransaction_rollsEverythingBack() = runBlocking {
        seed()
        try {
            db.withTransaction {
                db.transactionDao().convertAll(16000.0, 0)
                db.accountDao().convertAll(16000.0, 0)
                error("simulated crash half-way through the conversion")
            }
        } catch (expected: IllegalStateException) {
            // expected
        }
        val amounts = db.transactionDao().observeAll().first().map { it.amount }.sorted()
        assertEquals(listOf(3.17, 12.5, 2000.0), amounts)
        assertEquals(100.0, db.accountDao().observeAll().first().single().initialBalance, 0.0)
    }
}
