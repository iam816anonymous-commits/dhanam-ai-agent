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
        val headers = rows.first().map(::normalizeHeader)
        val exactHeader = rows.first().map { it.trim().lowercase() }
        val columnMap = if (exactHeader == header || exactHeader == legacyHeader) {
            mapOf("description" to 0, "amount" to 1, "category" to 2, "date" to 3, "note" to 4)
                .plus(if (exactHeader == header) mapOf("payment_mode" to 5, "detected_language" to 6) else emptyMap())
        } else {
            headers.mapIndexedNotNull { index, value -> headerAliases[value]?.let { it to index } }.toMap()
        }
        require(columnMap.containsKey("description") && columnMap.containsKey("amount") && columnMap.containsKey("date")) {
            "CSV needs description, amount and date columns. Supported names include Description/Expense/Particulars, Amount/Cost, Date/Transaction Date, Category, Notes/Remarks and Payment Method."
        }
        require(rows.size - 1 <= MAX_ROWS) { "CSV contains too many rows. Limit: $MAX_ROWS." }
        val parsed = mutableListOf<ExpenseEntity>()
        val errors = mutableListOf<String>()
        val sourceHash = MessageDigest.getInstance("SHA-256")
            .digest(normalizedText.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
        rows.drop(1).forEachIndexed { index, row ->
            val rowNumber = index + 2
            try {
                fun value(key: String): String = columnMap[key]?.let { row.getOrNull(it).orEmpty().trim() }.orEmpty()
                val title = value("description")
                val amount = normalizeExternalAmount(value("amount"))
                val category = value("category").ifBlank { "Other" }.let(::normalizeExternalCategory)
                val date = normalizeExternalDate(value("date"))
                val valid = ExpenseInputValidator.validate(title, amount, category, date, value("note"))
                parsed += ExpenseEntity(
                    title = valid.title, amountPaise = valid.amountPaise, category = valid.category,
                    occurredOnEpochDay = valid.occurredOn.toEpochDay(), note = valid.note,
                    createdAtEpochMillis = nowMillis, updatedAtEpochMillis = nowMillis,
                    paymentMode = value("payment_mode").ifBlank { "Unknown" }.take(60),
                    detectedLanguage = value("detected_language").ifBlank { "en" }.take(12),
                    sourceRecordKey = "csv:$sourceHash:$rowNumber"
                )
            } catch (error: Exception) {
                errors += "Row $rowNumber: ${error.message ?: "invalid expense record"}"
            }
        }
        return CsvImportPreview(parsed, errors)
    }

    private val headerAliases = mapOf(
        "description" to "description", "expense" to "description", "item" to "description",
        "particulars" to "description", "details" to "description", "merchant" to "description",
        "transactiondescription" to "description", "transactiondetails" to "description",
        "amount" to "amount", "amountrupees" to "amount", "amountinr" to "amount",
        "cost" to "amount", "price" to "amount", "value" to "amount", "total" to "amount",
        "expenseamount" to "amount", "debit" to "amount", "spent" to "amount",
        "category" to "category", "expensecategory" to "category", "type" to "category",
        "date" to "date", "transactiondate" to "date", "paymentdate" to "date",
        "occurredon" to "date", "datetime" to "date",
        "note" to "note", "notes" to "note", "remark" to "note", "remarks" to "note",
        "memo" to "note", "comment" to "note",
        "paymentmode" to "payment_mode", "paymentmethod" to "payment_mode", "mode" to "payment_mode",
        "detectedlanguage" to "detected_language", "language" to "detected_language"
    )

    private fun normalizeHeader(value: String): String =
        value.trim().lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun normalizeExternalAmount(value: String): String {
        val cleaned = value.trim()
            .replace(Regex("(?i)^(?:INR|Rs\\.?|₹)\\s*"), "")
            .replace(",", "")
            .replace(Regex("\\s+"), "")
        require(cleaned.isNotBlank()) { "Amount is missing." }
        return cleaned
    }

    private fun normalizeExternalCategory(value: String): String {
        val normalized = value.trim().lowercase()
        val aliases = mapOf(
            "food" to "Food", "groceries" to "Food", "grocery" to "Food", "restaurant" to "Food",
            "transport" to "Transport", "travel" to "Transport", "fuel" to "Transport", "commute" to "Transport",
            "shopping" to "Shopping", "retail" to "Shopping",
            "bills" to "Bills", "bill" to "Bills", "utilities" to "Bills", "rent" to "Bills",
            "health" to "Health", "medical" to "Health", "medicine" to "Health",
            "education" to "Education", "school" to "Education", "fees" to "Education",
            "other" to "Other", "miscellaneous" to "Other", "misc" to "Other"
        )
        return aliases[normalized] ?: ExpenseInputValidator.categories.firstOrNull { it.equals(value.trim(), true) }
            ?: throw IllegalArgumentException("Unknown category '$value'. Use Food, Transport, Shopping, Bills, Health, Education or Other.")
    }

    private fun normalizeExternalDate(value: String): String {
        val raw = value.trim()
        require(raw.isNotBlank()) { "Date is missing." }
        val iso = runCatching { LocalDate.parse(raw) }.getOrNull()
        if (iso != null) return iso.toString()
        val patterns = listOf(
            java.time.format.DateTimeFormatter.ofPattern("d/M/uuuu", java.util.Locale.ROOT),
            java.time.format.DateTimeFormatter.ofPattern("d-M-uuuu", java.util.Locale.ROOT),
            java.time.format.DateTimeFormatter.ofPattern("d.M.uuuu", java.util.Locale.ROOT),
            java.time.format.DateTimeFormatter.ofPattern("M/d/uuuu", java.util.Locale.ROOT),
            java.time.format.DateTimeFormatter.ofPattern("d MMM uuuu", java.util.Locale.ENGLISH),
            java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu", java.util.Locale.ENGLISH)
        )
        for (formatter in patterns) {
            val parsed = runCatching { LocalDate.parse(raw, formatter) }.getOrNull()
            if (parsed != null) return parsed.toString()
        }
        throw IllegalArgumentException("Unrecognised date '$raw'. Use YYYY-MM-DD or DD/MM/YYYY.")
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
