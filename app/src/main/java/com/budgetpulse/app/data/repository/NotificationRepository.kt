package com.budgetpulse.app.data.repository

import com.budgetpulse.app.data.local.NotificationDao
import com.budgetpulse.app.data.local.NotificationEntity
import com.budgetpulse.app.data.local.toDomain
import com.budgetpulse.app.domain.model.AppNotification
import com.budgetpulse.app.domain.model.Transaction
import com.budgetpulse.app.domain.model.TxType
import com.budgetpulse.app.util.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The list behind the bell: one simple entry for every transaction that gets added. */
class NotificationRepository(private val dao: NotificationDao) {

    fun observeAll(): Flow<List<AppNotification>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    suspend fun unreadIds(): Set<Long> = dao.unreadIds().toSet()

    suspend fun markAllRead() {
        val n = dao.markAllRead()
        if (n > 0) AppLog.d(AppLog.DB, "notifications: $n marked as read")
    }

    suspend fun clear() {
        dao.clear()
        AppLog.d(AppLog.DB, "notifications cleared")
    }

    /** Called when a transaction was stored (manually or from an e-mail). */
    suspend fun recordTransaction(tx: Transaction, transactionId: Long) {
        dao.insert(
            NotificationEntity(
                source = tx.source.name,
                isIncome = tx.type == TxType.INCOME,
                title = tx.note.ifBlank { tx.category.label },
                category = tx.category.name,
                amount = tx.amount,
                timestamp = System.currentTimeMillis(),
                isRead = false,
                transactionId = transactionId
            )
        )
        dao.trim()
    }
}
