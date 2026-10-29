package com.aistudio.dhanam.expense.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ExpenseEntity::class, BudgetEntity::class, KhataLoanEntity::class, RepaymentEntity::class],
    version = 2,
    exportSchema = true
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun financeDao(): FinanceDao

    companion object {
        const val DATABASE_NAME = "dhanam_finance.db"

        /** Additive migration: existing expenses are preserved; no destructive fallback is configured. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS budgets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    category TEXT NOT NULL,
                    month_epoch_day INTEGER NOT NULL,
                    amount_paise INTEGER NOT NULL,
                    created_at_epoch_millis INTEGER NOT NULL,
                    updated_at_epoch_millis INTEGER NOT NULL
                )""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_budgets_category_month_epoch_day ON budgets(category, month_epoch_day)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS khata_loans (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    person_name TEXT NOT NULL,
                    direction TEXT NOT NULL,
                    principal_paise INTEGER NOT NULL,
                    paid_paise INTEGER NOT NULL,
                    opened_on_epoch_day INTEGER NOT NULL,
                    due_on_epoch_day INTEGER,
                    note TEXT NOT NULL,
                    created_at_epoch_millis INTEGER NOT NULL,
                    updated_at_epoch_millis INTEGER NOT NULL
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_khata_loans_person_name ON khata_loans(person_name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_khata_loans_due_on_epoch_day ON khata_loans(due_on_epoch_day)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS khata_repayments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    loan_id INTEGER NOT NULL,
                    amount_paise INTEGER NOT NULL,
                    paid_on_epoch_day INTEGER NOT NULL,
                    note TEXT NOT NULL,
                    request_id TEXT NOT NULL,
                    created_at_epoch_millis INTEGER NOT NULL
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_khata_repayments_loan_id_paid_on_epoch_day ON khata_repayments(loan_id, paid_on_epoch_day)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_khata_repayments_request_id ON khata_repayments(request_id)")
            }
        }

        @Volatile private var instance: FinanceDatabase? = null

        fun getInstance(context: Context): FinanceDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FinanceDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
