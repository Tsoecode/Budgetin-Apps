package com.budgetpulse.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.budgetpulse.app.domain.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE accounts SET initialBalance = ROUND(initialBalance * :factor, :digits)")
    suspend fun convertAll(factor: Double, digits: Int): Int
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    /** IGNORE + unique externalId => returns -1 for an already-imported e-mail. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tx: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<TransactionEntity>)

    @Update
    suspend fun update(tx: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    /** Currency switch: every amount x factor, rounded to [digits] decimals. Returns the rows changed. */
    @Query("UPDATE transactions SET amount = ROUND(amount * :factor, :digits)")
    suspend fun convertAll(factor: Double, digits: Int): Int
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun delete(category: Category)

    @Query("UPDATE budgets SET monthlyLimit = ROUND(monthlyLimit * :factor, :digits)")
    suspend fun convertAll(factor: Double, digits: Int): Int
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY id")
    fun observeAll(): Flow<List<GoalEntity>>

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE goals SET target = ROUND(target * :factor, :digits), saved = ROUND(saved * :factor, :digits)")
    suspend fun convertAll(factor: Double, digits: Int): Int
}

@Dao
interface NewsDao {
    /** Always the newest stored week, so the carousel is never empty after a failed refresh. */
    @Query("SELECT * FROM news WHERE weekKey = (SELECT MAX(weekKey) FROM news) ORDER BY publishedAt DESC")
    fun observeLatest(): Flow<List<NewsEntity>>

    @Query("SELECT COUNT(*) FROM news WHERE weekKey = :weekKey")
    suspend fun countForWeek(weekKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<NewsEntity>)

    @Query("DELETE FROM news WHERE weekKey < :weekKey")
    suspend fun deleteOlderThan(weekKey: String)
}

@Dao
interface RatesDao {
    @Query("SELECT * FROM rates_cache WHERE base = :base")
    suspend fun get(base: String): RatesCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RatesCacheEntity)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC, id DESC LIMIT 100")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("SELECT id FROM notifications WHERE isRead = 0")
    suspend fun unreadIds(): List<Long>

    @Insert
    suspend fun insert(notification: NotificationEntity): Long

    @Query("UPDATE notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllRead(): Int

    @Query("DELETE FROM notifications")
    suspend fun clear()

    /** Keeps the table small: only the newest 100 entries survive. */
    @Query("DELETE FROM notifications WHERE id NOT IN (SELECT id FROM notifications ORDER BY timestamp DESC, id DESC LIMIT 100)")
    suspend fun trim()

    /** Currency switch: stored amounts follow the conversion. */
    @Query("UPDATE notifications SET amount = ROUND(amount * :factor, :digits)")
    suspend fun convertAll(factor: Double, digits: Int): Int
}
