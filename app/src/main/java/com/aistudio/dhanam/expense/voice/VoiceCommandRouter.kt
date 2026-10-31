package com.aistudio.dhanam.expense.voice

import com.aistudio.dhanam.expense.ui.AppDestination

/**
 * Routes explicit screen-navigation utterances. Financial commands remain with AgentViewModel,
 * so every mutation continues through its existing validation and confirmation flow.
 */
object VoiceCommandRouter {
    fun destinationFor(transcript: String): AppDestination? {
        val text = transcript.trim().lowercase()
            .replace(Regex("""[.!?,]+$"""), "")
            .replace(Regex("""\s+"""), " ")
        if (text.isBlank()) return null

        return when {
            matches(text, "open expenses", "show expenses", "go to expenses", "navigate to expenses", "my expenses", "expense list", "open budget", "show budget", "open budgets", "show budgets", "go to budget") ->
                AppDestination.EXPENSES
            matches(text, "open khata", "show khata", "go to khata", "open loans", "show loans", "lending and borrowing") ->
                AppDestination.KHATA
            matches(text, "open insights", "show insights", "go to insights", "open analytics", "show analytics", "spending dashboard", "open dashboard", "show dashboard") ->
                AppDestination.INSIGHTS
            matches(text, "open settings", "show settings", "go to settings", "settings and models") ->
                AppDestination.SETTINGS
            matches(text, "open agent", "open jarvis", "go to agent", "go home", "back to assistant") ->
                AppDestination.AGENT
            else -> null
        }
    }

    private fun matches(text: String, vararg phrases: String): Boolean =
        phrases.any { phrase -> text == phrase || text.startsWith("$phrase ") }
}
