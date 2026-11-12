package com.budgetpulse.app.data.repository

import com.budgetpulse.app.data.local.AccountDao
import com.budgetpulse.app.data.local.toDomain
import com.budgetpulse.app.data.local.toEntity
import com.budgetpulse.app.domain.model.Account
import com.budgetpulse.app.domain.model.AccountType
import com.budgetpulse.app.util.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AccountRepository(private val dao: AccountDao) {

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

    suspend fun delete(id: Long) {
        dao.deleteById(id)
        AppLog.d(AppLog.DB, "DELETE account id=$id (transactions cascade)")
    }
}
