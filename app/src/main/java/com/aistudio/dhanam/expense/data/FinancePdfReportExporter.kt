package com.aistudio.dhanam.expense.data

import android.content.ContentResolver
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.aistudio.dhanam.expense.domain.MonthlyFinanceSummary
import com.aistudio.dhanam.expense.domain.MoneyParser
import java.io.IOException

object FinancePdfReportExporter {
    fun write(resolver: ContentResolver, uri: Uri, summary: MonthlyFinanceSummary) {
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; isFakeBoldText = true }
            var y = 54f
            canvas.drawText("Dhanam — Monthly Finance Report", 40f, y, paint)
            paint.textSize = 12f
            paint.isFakeBoldText = false
            y += 30f
            canvas.drawText("Period: ${summary.month}", 40f, y, paint)
            y += 28f
            canvas.drawText("Recorded spending: ${MoneyParser.formatPaise(summary.totalSpentPaise)}", 40f, y, paint)
            y += 22f
            canvas.drawText("Transactions: ${summary.transactionCount}", 40f, y, paint)
            y += 22f
            canvas.drawText("Monthly budget: ${summary.overallBudgetPaise?.let(MoneyParser::formatPaise) ?: "Not set"}", 40f, y, paint)
            y += 22f
            val remaining = summary.remainingBudgetPaise
            val remainingText = if (remaining == null) "Not available" else
                (if (remaining < 0) "Over budget by " else "") + MoneyParser.formatPaise(kotlin.math.abs(remaining))
            canvas.drawText("Budget remaining: $remainingText", 40f, y, paint)
            y += 34f
            paint.isFakeBoldText = true
            canvas.drawText("Category breakdown", 40f, y, paint)
            paint.isFakeBoldText = false
            y += 22f
            if (summary.categoryTotalsPaise.isEmpty()) {
                canvas.drawText("No expense records for this period.", 40f, y, paint)
            } else {
                summary.categoryTotalsPaise.forEach { (category, total) ->
                    if (y > 790f) return@forEach
                    canvas.drawText(category.take(50), 40f, y, paint)
                    canvas.drawText(MoneyParser.formatPaise(total), 360f, y, paint)
                    y += 20f
                }
            }
            y = 810f
            paint.textSize = 9f
            canvas.drawText("Generated locally from records stored on this device.", 40f, y, paint)
            document.finishPage(page)
            resolver.openOutputStream(uri, "wt")?.use { document.writeTo(it) }
                ?: throw IOException("Could not write the selected PDF file.")
        } finally {
            document.close()
        }
    }
}
