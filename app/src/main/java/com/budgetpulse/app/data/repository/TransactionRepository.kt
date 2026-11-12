package com.budgetpulse.app.data.repository

import com.budgetpulse.app.data.local.TransactionDao
import com.budgetpulse.app.data.local.toDomain
import com.budgetpulse.app.data.local.toEntity
import com.budgetpulse.app.domain.model.Transaction
import com.budgetpulse.app.util.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepository(private val dao: TransactionDao, private val notifications: NotificationRepository) {

    fun observeAll(): Flow<List<Transaction>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observe(id: Long): Flow<Transaction?> = dao.observeById(id).map { it?.toDomain() }

    suspend fun get(id: Long): Transaction? = dao.getById(id)?.toDomain()

    suspend fun add(tx: Transaction): Long {
        val id = dao.insert(tx.toEntity())
        AppLog.d(AppLog.DB, "INSERT transaction id=$id type=${tx.type} amount=${tx.amount} category=${tx.category}")
        if (id != -1L) notifications.recordTransaction(tx, id)
        return id
    }

    suspend fun update(tx: Transaction) {
        dao.update(tx.toEntity())
        AppLog.d(AppLog.DB, "UPDATE transaction id=${tx.id} amount=${tx.amount}")
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
        AppLog.d(AppLog.DB, "DELETE transaction id=$id")
    }

    /** @return true when stored, false when a transaction with the same e-mail Message-ID already exists. */
    suspend fun importFromEmail(tx: Transaction): Boolean {
        val id = dao.insert(tx.toEntity())
        val inserted = id != -1L
        AppLog.d(AppLog.DB, "IMPORT email tx externalId=${tx.externalId} inserted=$inserted")
        if (inserted) notifications.recordTransaction(tx, id)
        return inserted
    }

    suspend fun deleteAll() {
        dao.deleteAll()
        AppLog.d(AppLog.DB, "DELETE all transactions")
    }
}
