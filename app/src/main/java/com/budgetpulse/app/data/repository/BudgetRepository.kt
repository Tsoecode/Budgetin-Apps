package com.budgetpulse.app.data.repository

import com.budgetpulse.app.data.local.BudgetDao
import com.budgetpulse.app.data.local.BudgetEntity
import com.budgetpulse.app.data.local.GoalDao
import com.budgetpulse.app.data.local.toDomain
import com.budgetpulse.app.data.local.toEntity
import com.budgetpulse.app.domain.model.BudgetLimit
import com.budgetpulse.app.domain.model.Category
import com.budgetpulse.app.domain.model.SavingsGoal
import com.budgetpulse.app.util.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Category limits ("Established limits") and savings goals. */
class BudgetRepository(private val budgetDao: BudgetDao, private val goalDao: GoalDao) {

    fun observeLimits(): Flow<List<BudgetLimit>> = budgetDao.observeAll().map { l -> l.map { it.toDomain() } }
    fun observeGoals(): Flow<List<SavingsGoal>> = goalDao.observeAll().map { l -> l.map { it.toDomain() } }

    suspend fun setLimit(category: Category, limit: Double) {
        budgetDao.upsert(BudgetEntity(category, limit))
        AppLog.d(AppLog.DB, "UPSERT budget $category=$limit")
    }

    suspend fun removeLimit(category: Category) {
        budgetDao.delete(category)
        AppLog.d(AppLog.DB, "DELETE budget $category")
    }

    suspend fun addGoal(goal: SavingsGoal) {
        val id = goalDao.insert(goal.toEntity())
        AppLog.d(AppLog.DB, "INSERT goal id=$id name=${goal.name}")
    }

    suspend fun updateGoal(goal: SavingsGoal) {
        goalDao.update(goal.toEntity())
        AppLog.d(AppLog.DB, "UPDATE goal id=${goal.id} saved=${goal.saved}")
    }

    suspend fun deleteGoal(id: Long) {
        goalDao.deleteById(id)
        AppLog.d(AppLog.DB, "DELETE goal id=$id")
    }
}
