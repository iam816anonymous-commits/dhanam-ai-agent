package com.aistudio.dhanam.expense.data

import android.content.ContentResolver
import android.net.Uri
import com.aistudio.dhanam.expense.domain.ExpenseInputValidator
import com.aistudio.dhanam.expense.domain.IncomeInputValidator
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.security.MessageDigest

data class CsvImportPreview(val expenses: List<ExpenseEntity>, val errors: List<String>)
data class IncomeCsvImportPreview(val income: List<IncomeEntity>, val errors: List<String>)

object FinanceCsvTransfer {
    private const val MAX_BYTES = 10 * 1024 * 1024
    private const val MAX_ROWS = 50_000
    private val header = listOf("description", "amount_rupees", "category", "date", "note", "payment_mode", "detected_language")
    private val legacyHeader = header.take(5)
    private val incomeHeader = listOf("description", "amount_rupees", "category", "date", "note")

    fun exportCsv(expenses: List<ExpenseEntity>): String = buildString {
        append(header.joinToString(",")).append("\r\n")
        expenses.forEach { expense ->
            val amount = "%d.%02d".format(java.util.Locale.ROOT, expense.amountPaise / 100, expense.amountPaise % 100)
            val values = listOf(expense.title, amount, expense.category,
                LocalDate.ofEpochDay(expense.occurredOnEpochDay).toString(), expense.note,
                expense.paymentMode, expense.detectedLanguage)
            append(values.joinToString(",") { escape(it) }).append("\r\n")
        }
    }

    fun exportIncomeCsv(income: List<IncomeEntity>): String = buildString {
        append(incomeHeader.joinToString(",")).append("\r\n")
        income.forEach { record ->
            val amount = "%d.%02d".format(java.util.Locale.ROOT, record.amountPaise / 100, record.amountPaise % 100)
            val values = listOf(record.title, amount, record.category,
                LocalDate.ofEpochDay(record.occurredOnEpochDay).toString(), record.note)
            append(values.joinToString(",") { escape(it) }).append("\r\n")
        }
    }

    fun parseIncomeCsv(text: String, nowMillis: Long = System.currentTimeMillis()): IncomeCsvImportPreview {
        require(text.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES) { "CSV is larger than the 10 MB import limit." }
        val normalizedText = text.removePrefix("\uFEFF")
        val rows = parseRows(normalizedText)
        require(rows.isNotEmpty()) { "CSV file is empty." }
        val normalizedHeader = rows.first().map { it.trim().lowercase() }
        require(normalizedHeader == incomeHeader) {
            "Income CSV headers must be: ${incomeHeader.joinToString(", ")}."
        }
        require(rows.size - 1 <= MAX_ROWS) { "CSV contains too many rows. Limit: ${MAX_ROWS}." }
        val parsed = mutableListOf<IncomeEntity>()
        val errors = mutableListOf<String>()
        val sourceHash = MessageDigest.getInstance("SHA-256")
            .digest(normalizedText.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
        rows.drop(1).forEachIndexed { index, row ->
            if (row.size != incomeHeader.size) {
                errors += "Row ${index + 2}: expected ${incomeHeader.size} columns."
            } else {
                try {
                    val valid = IncomeInputValidator.validate(row[0], row[1], row[2], row[3], row[4])
                    parsed += IncomeEntity(
                        title = valid.title,
                        amountPaise = valid.amountPaise,
                        category = valid.category,
                        occurredOnEpochDay = valid.occurredOn.toEpochDay(),
                        note = valid.note,
                        createdAtEpochMillis = nowMillis,
                        updatedAtEpochMillis = nowMillis,
                        sourceRecordKey = "income-csv:$sourceHash:${index + 2}"
                    )
                } catch (error: Exception) {
                    errors += "Row ${index + 2}: ${error.message ?: "invalid income record"}"
                }
            }
        }
        return IncomeCsvImportPreview(parsed, errors)
    }

    fun parseCsv(text: String, nowMillis: Long = System.currentTimeMillis()): CsvImportPreview {
        require(text.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES) { "CSV is larger than the 10 MB import limit." }
        val normalizedText = text.removePrefix("\uFEFF")
        val rows = parseRows(normalizedText)
        require(rows.isNotEmpty()) { "CSV file is empty." }
        val normalizedHeader = rows.first().map { it.trim().lowercase() }
        require(normalizedHeader == header || normalizedHeader == legacyHeader) {
            "CSV headers must be: ${header.joinToString(", ")} (older five-column Dhanam expense CSVs are also supported)."
        }
        val hasMetadata = normalizedHeader == header
        val expectedColumns = normalizedHeader.size
        require(rows.size - 1 <= MAX_ROWS) { "CSV contains too many rows. Limit: ${MAX_ROWS}." }
        val parsed = mutableListOf<ExpenseEntity>()
        val errors = mutableListOf<String>()
        val sourceHash = MessageDigest.getInstance("SHA-256")
            .digest(normalizedText.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
        rows.drop(1).forEachIndexed { index, row ->
            if (row.size != expectedColumns) {
                errors += "Row ${index + 2}: expected $expectedColumns columns."
            } else {
                try {
                    val valid = ExpenseInputValidator.validate(row[0], row[1], row[2], row[3], row[4])
                    parsed += ExpenseEntity(
                        title = valid.title, amountPaise = valid.amountPaise, category = valid.category,
                        occurredOnEpochDay = valid.occurredOn.toEpochDay(), note = valid.note,
                        createdAtEpochMillis = nowMillis, updatedAtEpochMillis = nowMillis,
                        paymentMode = if (hasMetadata) row[5].trim().ifBlank { "Unknown" } else "Unknown",
                        detectedLanguage = if (hasMetadata) row[6].trim().ifBlank { "en" } else "en",
                        sourceRecordKey = "csv:$sourceHash:${index + 2}"
                    )
                } catch (e: Exception) {
                    errors += "Row ${index + 2}: ${e.message ?: "invalid record"}"
                }
            }
        }
        return CsvImportPreview(parsed, errors)
    }

    fun readText(resolver: ContentResolver, uri: Uri): String {
        resolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_BYTES) { "CSV is larger than the 10 MB import limit." }
                output.write(buffer, 0, count)
            }
            return output.toString(StandardCharsets.UTF_8.name())
        } ?: error("Could not open the selected file.")
    }

    fun writeText(resolver: ContentResolver, uri: Uri, text: String) {
        resolver.openOutputStream(uri, "wt")?.bufferedWriter(StandardCharsets.UTF_8)?.use { it.write(text) }
            ?: error("Could not write the selected file.")
    }

    private fun escape(value: String): String {
        val safe = if (value.startsWith("=") || value.startsWith("+") || value.startsWith("-") ||
            value.startsWith("@") || value.startsWith("\t") || value.startsWith("\r")) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\""
        else safe
    }

    /** RFC-4180-style quoted field reader; embedded commas, quotes and line breaks are retained. */
    internal fun parseRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var afterQuote = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ }
                    else { quoted = false; afterQuote = true }
                } else field.append(c)
            } else when {
                c == '"' && field.isEmpty() && !afterQuote -> quoted = true
                c == ',' -> { row += field.toString(); field.setLength(0); afterQuote = false }
                c == '\r' || c == '\n' -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += field.toString(); field.setLength(0); afterQuote = false
                    if (row.any { it.isNotEmpty() }) rows += row.toList()
                    row.clear()
                }
                afterQuote && !c.isWhitespace() -> error("Unexpected character after a quoted CSV field.")
                else -> field.append(c)
            }
            i++
        }
        require(!quoted) { "CSV contains an unterminated quoted field." }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            if (row.any { it.isNotEmpty() }) rows += row.toList()
        }
        return rows
    }
}
