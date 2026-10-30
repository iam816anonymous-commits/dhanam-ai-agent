package com.aistudio.dhanam.expense.agent

import com.aistudio.dhanam.expense.data.ExpenseEntity
import java.security.MessageDigest
import java.util.Locale

data class SkillCandidate(
    val skillKey: String,
    val title: String,
    val matchText: String,
    val category: String,
    val exampleCount: Int,
    val testPassCount: Int,
    val testTotalCount: Int
)

/**
 * Learns conservative, exact-description category rules from the local ledger.
 * Leave-one-out validation prevents a rule being proposed merely because one row exists.
 */
object SkillLearningEngine {
    private const val MIN_DISTINCT_DATES = 3
    private const val MIN_TEST_ACCURACY_PERCENT = 90

    fun propose(expenses: List<ExpenseEntity>): List<SkillCandidate> {
        return expenses.asSequence()
            .filter { it.amountPaise > 0 && it.title.isNotBlank() }
            .groupBy { normalize(it.title) }
            .filter { (title, rows) ->
                title.length in 3..120 &&
                    rows.map { it.occurredOnEpochDay }.distinct().size >= MIN_DISTINCT_DATES
            }
            .mapNotNull { (normalizedTitle, rows) ->
                val results = rows.map { heldOut ->
                    val training = rows.filterNot { it.id == heldOut.id }
                    val categoryCounts = training.groupingBy { it.category }.eachCount()
                    val top = categoryCounts.maxByOrNull { it.value } ?: return@mapNotNull null
                    val tied = categoryCounts.values.count { it == top.value } > 1
                    if (!tied && top.key == heldOut.category) top.key else null
                }
                val passes = results.count { it != null }
                val total = results.size
                if (total < MIN_DISTINCT_DATES ||
                    passes * 100 < total * MIN_TEST_ACCURACY_PERCENT) {
                    null
                } else {
                    val categoryCounts = rows.groupingBy { it.category }.eachCount()
                    val category = categoryCounts.maxByOrNull { it.value }?.key ?: return@mapNotNull null
                    SkillCandidate(
                        skillKey = sha256(normalizedTitle),
                        title = "Category rule: $normalizedTitle",
                        matchText = normalizedTitle,
                        category = category,
                        exampleCount = rows.size,
                        testPassCount = passes,
                        testTotalCount = total
                    )
                }
            }
            .sortedByDescending { it.exampleCount }
            .take(20)
            .toList()
    }

    fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("""\s+"""), " ")

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
