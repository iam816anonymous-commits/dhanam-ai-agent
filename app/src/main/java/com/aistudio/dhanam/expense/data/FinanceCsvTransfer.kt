package com.aistudio.dhanam.expense.data

import android.content.ContentResolver
import android.net.Uri
import com.aistudio.dhanam.expense.domain.ExpenseInputValidator
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.time.LocalDate

data class CsvImportPreview(val expenses: List<ExpenseEntity>, val errors: List<String>)

object FinanceCsvTransfer {
    private const val MAX_BYTES = 10 * 1024 * 1024
    private const val MAX_ROWS = 50_000
    private val header = listOf("description", "amount_rupees", "category", "date", "note")

    fun exportCsv(expenses: List<ExpenseEntity>): String = buildString {
        append(header.joinToString(",")).append("\r\n")
        expenses.forEach { expense ->
            val amount = "%d.%02d".format(java.util.Locale.ROOT, expense.amountPaise / 100, expense.amountPaise % 100)
            val values = listOf(expense.title, amount, expense.category,
                LocalDate.ofEpochDay(expense.occurredOnEpochDay).toString(), expense.note)
            append(values.joinToString(",") { escape(it) }).append("\r\n")
        }
    }

    fun parseCsv(text: String, nowMillis: Long = System.currentTimeMillis()): CsvImportPreview {
        require(text.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES) { "CSV is larger than the 10 MB import limit." }
        val rows = parseRows(text)
        require(rows.isNotEmpty()) { "CSV file is empty." }
        require(rows.first().map { it.trim().lowercase() } == header) {
            "CSV headers must be: ${header.joinToString(", ")}."
        }
        require(rows.size - 1 <= MAX_ROWS) { "CSV contains too many rows. Limit: ${MAX_ROWS}." }
        val parsed = mutableListOf<ExpenseEntity>()
        val errors = mutableListOf<String>()
        rows.drop(1).forEachIndexed { index, row ->
            if (row.size != header.size) {
                errors += "Row ${index + 2}: expected 5 columns."
            } else {
                try {
                    val valid = ExpenseInputValidator.validate(row[0], row[1], row[2], row[3], row[4])
                    parsed += ExpenseEntity(
                        title = valid.title, amountPaise = valid.amountPaise, category = valid.category,
                        occurredOnEpochDay = valid.occurredOn.toEpochDay(), note = valid.note,
                        createdAtEpochMillis = nowMillis, updatedAtEpochMillis = nowMillis
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

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\""
        else value

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
