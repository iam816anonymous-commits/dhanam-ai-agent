package com.aistudio.dhanam.expense.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import com.aistudio.dhanam.expense.domain.LegacyMoneyConverter
import java.time.ZoneId

data class LegacyFinancePreview(
    val sourcePath: String,
    val expenses: List<ExpenseEntity>,
    val loans: List<KhataLoanEntity>,
    val budgets: List<BudgetEntity>,
    val errors: List<String>
) {
    val isEmpty: Boolean get() = expenses.isEmpty() && loans.isEmpty() && budgets.isEmpty()
}

data class LegacyImportCounts(val expenses: Int, val loans: Int, val budgets: Int)

/**
 * Reads the previous Dhanam database read-only. It never modifies or deletes the legacy database.
 * The user must review the preview and explicitly confirm import.
 */
object LegacyFinanceMigration {
    const val LEGACY_DATABASE_NAME = "dhanam_expense_database"

    fun inspect(context: Context, nowMillis: Long = System.currentTimeMillis()): LegacyFinancePreview {
        val file = context.getDatabasePath(LEGACY_DATABASE_NAME)
        require(file.exists() && file.isFile) {
            "No legacy Dhanam database was found on this installation. Install/update over the old app or use CSV import."
        }
        val expenses = mutableListOf<ExpenseEntity>()
        val loans = mutableListOf<KhataLoanEntity>()
        val budgets = mutableListOf<BudgetEntity>()
        val errors = mutableListOf<String>()
        val monthStart = YearMonth.now().atDay(1).toEpochDay()
        val zone = ZoneId.systemDefault()
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            if (tableExists(db, "expenses")) {
                db.rawQuery("SELECT id, title, amount, category, timestamp, notes, paymentMode, detectedLanguage FROM expenses ORDER BY timestamp, id", null).use { cursor ->
                    var rowNumber = 0
                    while (cursor.moveToNext()) {
                        rowNumber++
                        try {
                            val title = cursor.getString(1)?.trim().orEmpty()
                            require(title.isNotBlank() && title.length <= 120) { "description is blank or too long" }
                            val amount = LegacyMoneyConverter.toPaise(cursor.getDouble(2), allowZero = false)
                            val category = normalizeCategory(cursor.getString(3))
                            val timestamp = cursor.getLong(4)
                            val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
                            val note = cursor.getString(5).orEmpty().takeIf { it.length <= 1000 }
                                ?: error("note exceeds 1,000 characters")
                            expenses += ExpenseEntity(
                                title = title, amountPaise = amount, category = category,
                                occurredOnEpochDay = date.toEpochDay(), note = note,
                                createdAtEpochMillis = timestamp, updatedAtEpochMillis = nowMillis,
                                paymentMode = cursor.getString(6).orEmpty().ifBlank { "Unknown" }.take(60),
                                detectedLanguage = cursor.getString(7).orEmpty().ifBlank { "en" }.take(12)
                            )
                        } catch (e: Exception) {
                            errors += "Legacy expense row $rowNumber: ${e.message ?: "invalid record"}."
                        }
                    }
                }
            } else errors += "Legacy database has no expenses table."
            if (tableExists(db, "loans")) {
                db.rawQuery("SELECT id, personName, amount, amountPaid, dateGivenMillis, dueDateMillis, notes, phoneNumber, isSettled FROM loans ORDER BY dateGivenMillis, id", null).use { cursor ->
                    var rowNumber = 0
                    while (cursor.moveToNext()) {
                        rowNumber++
                        try {
                            val person = cursor.getString(1)?.trim().orEmpty()
                            require(person.isNotBlank() && person.length <= 100) { "person name is blank or too long" }
                            val principal = LegacyMoneyConverter.toPaise(cursor.getDouble(2), allowZero = false)
                            val paid = LegacyMoneyConverter.toPaise(cursor.getDouble(3), allowZero = true)
                            require(paid <= principal) { "amount paid exceeds principal" }
                            val openedMillis = cursor.getLong(4)
                            val dueEpoch = if (cursor.isNull(5)) null else
                                Instant.ofEpochMilli(cursor.getLong(5)).atZone(zone).toLocalDate().toEpochDay()
                            val openedEpoch = Instant.ofEpochMilli(openedMillis).atZone(zone).toLocalDate().toEpochDay()
                            loans += KhataLoanEntity(
                                personName = person, direction = "LENT", principalPaise = principal,
                                paidPaise = paid, openedOnEpochDay = openedEpoch, dueOnEpochDay = dueEpoch,
                                note = cursor.getString(6).orEmpty().take(1000),
                                createdAtEpochMillis = openedMillis, updatedAtEpochMillis = nowMillis,
                                phoneNumber = cursor.getString(7).orEmpty().take(40),
                                isSettled = cursor.getInt(8) != 0
                            )
                        } catch (e: Exception) {
                            errors += "Legacy Khata row $rowNumber: ${e.message ?: "invalid record"}."
                        }
                    }
                }
            } else errors += "Legacy database has no loans table."
            if (tableExists(db, "category_budgets")) {
                db.rawQuery("SELECT category, monthlyLimit FROM category_budgets ORDER BY category", null).use { cursor ->
                    var rowNumber = 0
                    while (cursor.moveToNext()) {
                        rowNumber++
                        try {
                            val category = normalizeCategory(cursor.getString(0))
                            val amount = LegacyMoneyConverter.toPaise(cursor.getDouble(1), allowZero = false)
                            budgets += BudgetEntity(
                                category = category, monthEpochDay = monthStart, amountPaise = amount,
                                createdAtEpochMillis = nowMillis, updatedAtEpochMillis = nowMillis
                            )
                        } catch (e: Exception) {
                            errors += "Legacy budget row $rowNumber: ${e.message ?: "invalid record"}."
                        }
                    }
                }
            }
        }
        return LegacyFinancePreview(file.absolutePath, expenses, loans, budgets, errors)
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { it.moveToFirst() }


    private fun normalizeCategory(raw: String?): String {
        val value = raw.orEmpty().trim().lowercase()
        return when {
            value.contains("food") || value.contains("dining") || value.contains("snack") || value.contains("grocery") -> "Food"
            value.contains("transport") || value.contains("fuel") || value.contains("petrol") || value == "travel" -> "Transport"
            value.contains("shopping") || value.contains("clothes") || value.contains("clothing") -> "Shopping"
            value.contains("bill") || value.contains("utilit") || value.contains("recharge") -> "Bills"
            value.contains("health") || value.contains("medical") || value.contains("medicine") -> "Health"
            value.contains("education") || value.contains("fee") || value.contains("school") -> "Education"
            else -> "Other"
        }
    }
}
