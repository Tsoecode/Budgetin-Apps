package com.budgetpulse.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.budgetpulse.app.domain.model.AccountType
import com.budgetpulse.app.domain.model.Category
import com.budgetpulse.app.domain.model.TxSource
import com.budgetpulse.app.domain.model.TxType

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val colorArgb: Long,
    val initialBalance: Double
)

@Entity(
    tableName = "transactions",
    foreignKeys = [ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["accountId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("accountId"), Index("timestamp"), Index(value = ["externalId"], unique = true)]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val type: TxType,
    val amount: Double,
    val category: Category,
    val note: String,
    val timestamp: Long,
    val source: TxSource,
    /** E-mail Message-ID for imported rows: the unique index prevents importing the same mail twice. */
    val externalId: String?
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: Category,
    val monthlyLimit: Double
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val target: Double,
    val saved: Double
)

@Entity(tableName = "news")
data class NewsEntity(
    @PrimaryKey val link: String,
    val title: String,
    val source: String,
    val publishedAt: Long,
    val imageUrl: String,
    val weekKey: String,
    val fetchedAt: Long
)

@Entity(tableName = "rates_cache")
data class RatesCacheEntity(
    @PrimaryKey val base: String,
    val ratesJson: String,
    val updatedLabel: String,
    val nextUpdateEpochSec: Long,
    val provider: String,
    val fetchedAtMillis: Long
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** TxSource name: MANUAL or EMAIL */
    val source: String,
    val isIncome: Boolean,
    val title: String,
    /** Category enum name */
    val category: String,
    val amount: Double,
    val timestamp: Long,
    val isRead: Boolean,
    val transactionId: Long?
)
