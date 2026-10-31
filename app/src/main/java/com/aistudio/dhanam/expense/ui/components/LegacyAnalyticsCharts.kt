package com.aistudio.dhanam.expense.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aistudio.dhanam.expense.data.ExpenseEntity
import com.aistudio.dhanam.expense.domain.MoneyParser
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Restored visual category distribution, computed only from saved transactions. */
@Composable
fun LegacyCategoryDonutChart(categoryTotals: Map<String, Long>) {
    val entries = categoryTotals.entries.filter { it.value > 0L }.sortedByDescending { it.value }.take(6)
    if (entries.isEmpty()) return
    val total = entries.sumOf { it.value }
    val scheme = MaterialTheme.colorScheme
    val colors = listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.error, scheme.inversePrimary, scheme.outline)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Category spending distribution", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Canvas(Modifier.size(148.dp)) {
                    var start = -90f
                    val stroke = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Butt)
                    entries.forEachIndexed { index, entry ->
                        val sweep = (entry.value.toDouble() / total.toDouble() * 360.0).toFloat()
                        drawArc(
                            color = colors[index % colors.size],
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = stroke
                        )
                        start += sweep
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    entries.forEachIndexed { index, entry ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(colors[index % colors.size]))
                            Column(Modifier.weight(1f)) {
                                Text(entry.key, style = MaterialTheme.typography.bodySmall)
                                Text(MoneyParser.formatPaise(entry.value), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Seven-day spend pattern ending on the selected month's latest available date. */
@Composable
fun LegacyWeeklySpendChart(expenses: List<ExpenseEntity>, month: YearMonth) {
    val end = if (month == YearMonth.now()) LocalDate.now() else month.atEndOfMonth()
    val start = maxOf(month.atDay(1), end.minusDays(6))
    val days = (0..java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt()).map { start.plusDays(it.toLong()) }
    val totals = days.map { day ->
        day to expenses.asSequence().filter { it.occurredOnEpochDay == day.toEpochDay() }
            .fold(0L) { total, expense -> Math.addExact(total, expense.amountPaise) }
    }
    val maximum = totals.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Daily spending pattern", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().height(112.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom) {
                totals.forEach { (day, amount) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom) {
                        Box(
                            Modifier.width(18.dp)
                                .height((amount.toDouble() / maximum.toDouble() * 76.0).toFloat().coerceAtLeast(if (amount > 0) 3f else 0f).dp)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
            Text("Daily totals are calculated from recorded expenses, not bank transactions.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Restored payment-method mix using the saved payment_mode field. */
@Composable
fun LegacyPaymentModeBreakdown(expenses: List<ExpenseEntity>, month: YearMonth) {
    val start = month.atDay(1).toEpochDay()
    val end = (if (month == YearMonth.now()) LocalDate.now() else month.atEndOfMonth()).toEpochDay()
    val rows = expenses.asSequence().filter { it.occurredOnEpochDay in start..end }
        .groupBy { it.paymentMode.ifBlank { "Unknown" } }
        .mapValues { (_, records) -> records.fold(0L) { total, expense -> Math.addExact(total, expense.amountPaise) } }
        .toList().sortedByDescending { it.second }
    if (rows.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Payment methods", style = MaterialTheme.typography.titleMedium)
            rows.forEach { (mode, amount) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(mode, style = MaterialTheme.typography.bodyMedium)
                    Text(MoneyParser.formatPaise(amount), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("Unknown means the payment method was not recorded for that transaction.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
