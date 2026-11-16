package com.budgetpulse.app.data.repository

import androidx.room.withTransaction
import com.budgetpulse.app.data.local.AppDatabase
import com.budgetpulse.app.data.local.toDomain
import com.budgetpulse.app.data.local.toEntity
import com.budgetpulse.app.domain.model.Account
import com.budgetpulse.app.domain.model.AccountType
import com.budgetpulse.app.util.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface AccountDeleteResult {
    data object Deleted : AccountDeleteResult
    data object RequiresDestination : AccountDeleteResult
    data object LastAccount : AccountDeleteResult
    data object NotFound : AccountDeleteResult
    data object InvalidDestination : AccountDeleteResult
}

class AccountRepository(private val database: AppDatabase) {
    private val dao = database.accountDao()
    private val transactions = database.transactionDao()

    fun observeAll(): Flow<List<Account>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun seedDefaultsIfEmpty() {
        if (dao.count() > 0) return
        dao.insert(Account(name = "Cash wallet", type = AccountType.CASH, colorArgb = 0xFFE4572E, initialBalance = 0.0).toEntity())
        dao.insert(Account(name = "Bank account", type = AccountType.BANK, colorArgb = 0xFF3B82C4, initialBalance = 0.0).toEntity())
        AppLog.d(AppLog.DB, "seeded default accounts")
    }

    suspend fun add(account: Account): Long = dao.insert(account.toEntity()).also {
        AppLog.d(AppLog.DB, "INSERT account id=$it name=${account.name}")
    }

    suspend fun update(account: Account) {
        dao.update(account.toEntity())
        AppLog.d(AppLog.DB, "UPDATE account id=${account.id}")
    }

    /**
     * Delete an unused zero-balance account, or merge its starting balance and all transactions
     * into [destinationId]. The operation and last-account guard are atomic.
     */
    suspend fun deleteOrMerge(id: Long, destinationId: Long? = null): AccountDeleteResult =
        database.withTransaction {
            if (dao.count() <= 1) return@withTransaction AccountDeleteResult.LastAccount
            val source = dao.getById(id) ?: return@withTransaction AccountDeleteResult.NotFound
            val transactionCount = transactions.countForAccount(id)
            val needsDestination = source.initialBalance != 0.0 || transactionCount > 0

            if (needsDestination && destinationId == null) {
                return@withTransaction AccountDeleteResult.RequiresDestination
            }
            if (!needsDestination && destinationId == null) {
                dao.deleteById(id)
                return@withTransaction AccountDeleteResult.Deleted
            }

            val targetId = destinationId ?: return@withTransaction AccountDeleteResult.RequiresDestination
            if (targetId == id || dao.getById(targetId) == null) {
                return@withTransaction AccountDeleteResult.InvalidDestination
            }

            dao.addToInitialBalance(targetId, source.initialBalance)
            transactions.moveAccountTransactions(id, targetId)
            dao.deleteById(id)
            AppLog.d(AppLog.DB, "MERGE account id=$id into id=$targetId; moved $transactionCount transactions")
            AccountDeleteResult.Deleted
        }
}
