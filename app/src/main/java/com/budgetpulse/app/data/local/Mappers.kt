package com.budgetpulse.app.data.local

import com.budgetpulse.app.domain.model.Account
import com.budgetpulse.app.domain.model.AppNotification
import com.budgetpulse.app.domain.model.Category
import com.budgetpulse.app.domain.model.TxSource
import com.budgetpulse.app.domain.model.TxType
import com.budgetpulse.app.domain.model.BudgetLimit
import com.budgetpulse.app.domain.model.NewsItem
import com.budgetpulse.app.domain.model.SavingsGoal
import com.budgetpulse.app.domain.model.Transaction

fun TransactionEntity.toDomain() = Transaction(id, accountId, type, amount, category, note, timestamp, source, externalId)
fun Transaction.toEntity() = TransactionEntity(id, accountId, type, amount, category, note, timestamp, source, externalId)

fun AccountEntity.toDomain() = Account(id, name, type, colorArgb, initialBalance)
fun Account.toEntity() = AccountEntity(id, name, type, colorArgb, initialBalance)

fun BudgetEntity.toDomain() = BudgetLimit(category, monthlyLimit)
fun GoalEntity.toDomain() = SavingsGoal(id, name, target, saved)
fun SavingsGoal.toEntity() = GoalEntity(id, name, target, saved)

fun NewsEntity.toDomain() = NewsItem(title, source, link, publishedAt, imageUrl)

fun NotificationEntity.toDomain() = AppNotification(
    id = id,
    source = runCatching { TxSource.valueOf(source) }.getOrDefault(TxSource.MANUAL),
    type = if (isIncome) TxType.INCOME else TxType.EXPENSE,
    title = title,
    category = runCatching { Category.valueOf(category) }.getOrDefault(Category.OTHER_EXPENSE),
    amount = amount, timestamp = timestamp, read = isRead, transactionId = transactionId
)
