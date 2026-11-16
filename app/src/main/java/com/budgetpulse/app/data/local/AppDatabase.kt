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
    version = 4,
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

        /** v2 -> v3: change account deletion from CASCADE to RESTRICT to protect transaction history. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE transactions_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "accountId INTEGER NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "amount REAL NOT NULL, " +
                        "category TEXT NOT NULL, " +
                        "note TEXT NOT NULL, " +
                        "timestamp INTEGER NOT NULL, " +
                        "source TEXT NOT NULL, " +
                        "externalId TEXT, " +
                        "FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                )
                db.execSQL(
                    "INSERT INTO transactions_new (id, accountId, type, amount, category, note, timestamp, source, externalId) " +
                        "SELECT id, accountId, type, amount, category, note, timestamp, source, externalId FROM transactions"
                )
                db.execSQL("DROP TABLE transactions")
                db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
                db.execSQL("CREATE INDEX index_transactions_accountId ON transactions(accountId)")
                db.execSQL("CREATE INDEX index_transactions_timestamp ON transactions(timestamp)")
                db.execSQL("CREATE UNIQUE INDEX index_transactions_externalId ON transactions(externalId)")
            }
        }

        /** v3 -> v4: store the publisher homepage so news cards can show the outlet's own logo. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE news ADD COLUMN sourceUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "budgetpulse.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
