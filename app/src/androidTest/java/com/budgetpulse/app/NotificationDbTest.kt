package com.budgetpulse.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budgetpulse.app.data.local.AppDatabase
import com.budgetpulse.app.data.repository.NotificationRepository
import com.budgetpulse.app.domain.model.Category
import com.budgetpulse.app.domain.model.Transaction
import com.budgetpulse.app.domain.model.TxSource
import com.budgetpulse.app.domain.model.TxType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room test for the notification list behind the bell. */
@RunWith(AndroidJUnit4::class)
class NotificationDbTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: NotificationRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = NotificationRepository(db.notificationDao())
    }

    @After
    fun tearDown() = db.close()

    private fun tx(note: String, source: TxSource, type: TxType = TxType.EXPENSE, amount: Double = 12.5) = Transaction(
        accountId = 1, type = type, amount = amount, category = Category.FOOD, note = note, timestamp = 1L, source = source
    )

    @Test
    fun everyAddedTransactionBecomesAnUnreadNotification() = runBlocking {
        repo.recordTransaction(tx("Lunch", TxSource.MANUAL), 1)
        repo.recordTransaction(tx("Salary", TxSource.EMAIL, TxType.INCOME, 2000.0), 2)
        assertEquals(2, repo.observeUnreadCount().first())
        val items = repo.observeAll().first()
        assertEquals(setOf(TxSource.MANUAL, TxSource.EMAIL), items.map { it.source }.toSet())
        assertTrue(items.any { it.type == TxType.INCOME && it.title == "Salary" })
    }

    @Test
    fun openingTheListClearsTheBadge_butRemembersWhichWereNew() = runBlocking {
        repo.recordTransaction(tx("A", TxSource.MANUAL), 1)
        repo.recordTransaction(tx("B", TxSource.EMAIL), 2)
        val fresh = repo.unreadIds()
        assertEquals(2, fresh.size)
        repo.markAllRead()
        assertEquals(0, repo.observeUnreadCount().first())
        assertTrue(repo.observeAll().first().all { it.read })
    }

    @Test
    fun onlyTheNewestHundredAreKept() = runBlocking {
        repeat(120) { repo.recordTransaction(tx("n$it", TxSource.MANUAL), it.toLong() + 1) }
        assertEquals(100, repo.observeAll().first().size)
    }

    @Test
    fun clearRemovesEverything() = runBlocking {
        repo.recordTransaction(tx("A", TxSource.MANUAL), 1)
        repo.clear()
        assertEquals(0, repo.observeAll().first().size)
    }

    @Test
    fun currencyConversionAlsoConvertsStoredNotificationAmounts() = runBlocking {
        repo.recordTransaction(tx("A", TxSource.MANUAL, amount = 12.5), 1)
        db.notificationDao().convertAll(16000.0, 0)
        assertEquals(200000.0, repo.observeAll().first().single().amount, 0.0)
    }
}
