package com.aistudio.dhanam.expense.model

object LocalPromptValidator {
    const val MAX_PROMPT_CHARS = 4000

    fun validate(raw: String): String {
        val prompt = raw.trim()
        require(prompt.isNotEmpty()) { "Enter a prompt first." }
        require(prompt.length <= MAX_PROMPT_CHARS) {
            "Prompts are limited to $MAX_PROMPT_CHARS characters."
        }
        return prompt
    }
}
