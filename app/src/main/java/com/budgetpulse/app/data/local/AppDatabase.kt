package com.budgetpulse.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class, TransactionEntity::class, BudgetEntity::class,
        GoalEntity::class, NewsEntity::class, RatesCacheEntity::class, NotificationEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun newsDao(): NewsDao
    abstract fun ratesDao(): RatesDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        /** v1 -> v2: adds the notifications table. Existing transactions, accounts, limits and goals are untouched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS notifications (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "source TEXT NOT NULL, " +
                        "isIncome INTEGER NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "category TEXT NOT NULL, " +
                        "amount REAL NOT NULL, " +
                        "timestamp INTEGER NOT NULL, " +
                        "isRead INTEGER NOT NULL, " +
                        "transactionId INTEGER)"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "budgetpulse.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
