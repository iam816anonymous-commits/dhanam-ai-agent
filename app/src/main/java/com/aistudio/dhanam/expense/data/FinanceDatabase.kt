package com.aistudio.dhanam.expense.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ExpenseEntity::class, BudgetEntity::class, KhataLoanEntity::class, RepaymentEntity::class, AgentLearningReportEntity::class, AgentSkillEntity::class, AgentMemoryEntity::class, AgentMessageEntity::class],
    version = 7,
    exportSchema = true
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun financeDao(): FinanceDao

    companion object {
        const val DATABASE_NAME = "dhanam_finance.db"

        /** Additive migration: existing finance records are preserved; no destructive fallback. */
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

        /** Preserve v2 target data while adding metadata needed for legacy continuity. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN payment_mode TEXT NOT NULL DEFAULT 'Unknown'")
                db.execSQL("ALTER TABLE expenses ADD COLUMN detected_language TEXT NOT NULL DEFAULT 'en'")
                db.execSQL("ALTER TABLE khata_loans ADD COLUMN phone_number TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE khata_loans ADD COLUMN is_settled INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Idempotent source IDs prevent same-day identical transactions from being mistaken for duplicates. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN source_record_key TEXT")
                db.execSQL("ALTER TABLE khata_loans ADD COLUMN legacy_source_id TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_expenses_source_record_key ON expenses(source_record_key)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_khata_loans_legacy_source_id ON khata_loans(legacy_source_id)")
            }
        }

        /** Additive local report table; existing finance records remain untouched. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS agent_learning_reports (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    report_date_epoch_day INTEGER NOT NULL,
                    run_status TEXT NOT NULL,
                    operations_summary TEXT NOT NULL,
                    intelligence_summary TEXT NOT NULL,
                    skill_summary TEXT NOT NULL,
                    next_improvements TEXT NOT NULL,
                    duration_millis INTEGER NOT NULL,
                    created_at_epoch_millis INTEGER NOT NULL
                )""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_agent_learning_reports_report_date_epoch_day ON agent_learning_reports(report_date_epoch_day)")
            }
        }

        /** Versioned, data-only agent skills. Existing finance tables are not modified. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS agent_skills (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    skill_key TEXT NOT NULL,
                    version INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    match_text TEXT NOT NULL,
                    category TEXT NOT NULL,
                    example_count INTEGER NOT NULL,
                    test_pass_count INTEGER NOT NULL,
                    test_total_count INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    created_at_epoch_millis INTEGER NOT NULL,
                    approved_at_epoch_millis INTEGER
                )""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_agent_skills_skill_key_version ON agent_skills(skill_key, version)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_skills_status ON agent_skills(status)")
            }
        }

        /** Add bounded role-specific memory and structured handoff history without changing finance rows. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS agent_memories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    agent_role TEXT NOT NULL,
                    memory_key TEXT NOT NULL,
                    memory_value TEXT NOT NULL,
                    source_report_epoch_day INTEGER NOT NULL,
                    confidence_percent INTEGER NOT NULL,
                    updated_at_epoch_millis INTEGER NOT NULL
                )""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_agent_memories_agent_role_memory_key ON agent_memories(agent_role, memory_key)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_memories_updated_at_epoch_millis ON agent_memories(updated_at_epoch_millis)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS agent_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    session_id TEXT NOT NULL,
                    from_agent TEXT NOT NULL,
                    to_agent TEXT NOT NULL,
                    message_type TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    created_at_epoch_millis INTEGER NOT NULL
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_messages_session_id_created_at_epoch_millis ON agent_messages(session_id, created_at_epoch_millis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_messages_created_at_epoch_millis ON agent_messages(created_at_epoch_millis)")
            }
        }

        @Volatile private var instance: FinanceDatabase? = null

        fun getInstance(context: Context): FinanceDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FinanceDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build().also { instance = it }
            }
    }
}
