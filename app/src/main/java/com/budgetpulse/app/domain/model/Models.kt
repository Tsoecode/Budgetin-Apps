package com.budgetpulse.app.domain.model

enum class TxType { EXPENSE, INCOME }
enum class TxSource { MANUAL, EMAIL }
enum class AccountType(val label: String) { CASH("Cash"), BANK("Bank"), CARD("Card") }

/** Transaction categories. colorArgb is used for charts and icons. */
enum class Category(val label: String, val type: TxType, val colorArgb: Long) {
    FOOD("Food & Restaurants", TxType.EXPENSE, 0xFFE4572E),
    TRANSPORT("Transport", TxType.EXPENSE, 0xFFF2A900),
    SHOPPING("Shopping", TxType.EXPENSE, 0xFFD6336C),
    BILLS("Bills & Utilities", TxType.EXPENSE, 0xFF3B82C4),
    ENTERTAINMENT("Entertainment", TxType.EXPENSE, 0xFF8E54E9),
    HEALTH("Health", TxType.EXPENSE, 0xFF2BA58F),
    EDUCATION("Education", TxType.EXPENSE, 0xFF4F6BED),
    TRAVEL("Travel", TxType.EXPENSE, 0xFF00A3B4),
    OTHER_EXPENSE("Other", TxType.EXPENSE, 0xFF7A7A7A),
    SALARY("Salary", TxType.INCOME, 0xFF2E9E6B),
    BONUS("Bonus", TxType.INCOME, 0xFF55B96A),
    OTHER_INCOME("Other income", TxType.INCOME, 0xFF8BC34A);

    companion object {
        fun forType(type: TxType): List<Category> = entries.filter { it.type == type }
    }
}

data class Transaction(
    val id: Long = 0,
    val accountId: Long,
    val type: TxType,
    val amount: Double,
    val category: Category,
    val note: String,
    val timestamp: Long,
    val source: TxSource = TxSource.MANUAL,
    val externalId: String? = null
)

data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val colorArgb: Long,
    val initialBalance: Double = 0.0
)

data class AccountWithBalance(val account: Account, val balance: Double)
data class BudgetLimit(val category: Category, val monthlyLimit: Double)
data class SavingsGoal(val id: Long = 0, val name: String, val target: Double, val saved: Double)

data class NewsItem(
    val title: String,
    val source: String,
    val link: String,
    val publishedAt: Long,
    val imageUrl: String
)

data class CurrencyRates(
    val base: String,
    val rates: Map<String, Double>,
    val updatedLabel: String,
    val nextUpdateEpochSec: Long,
    val provider: String,
    val stale: Boolean = false
)

/** One e-mail pulled from the user inbox (never persisted, only parsed). */
data class RawEmail(
    val messageId: String,
    val from: String,
    val subject: String,
    val body: String,
    val receivedAt: Long
)

data class ParsedTransaction(
    val type: TxType,
    val amount: Double,
    val currencyCode: String?,
    val description: String,
    val category: Category
)

/** One entry of the notification list: a transaction that was added by hand or imported from an e-mail. */
data class AppNotification(
    val id: Long,
    val source: TxSource,
    val type: TxType,
    val title: String,
    val category: Category,
    val amount: Double,
    val timestamp: Long,
    val read: Boolean,
    val transactionId: Long?
)
