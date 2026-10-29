package com.aistudio.dhanam.expense.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalPromptValidatorTest {
    @Test fun trimsValidPrompt() {
        assertEquals("Explain compound interest.", LocalPromptValidator.validate("  Explain compound interest.  "))
    }

    @Test fun rejectsBlankPrompt() {
        assertThrows(IllegalArgumentException::class.java) { LocalPromptValidator.validate("  \n ") }
    }

    @Test fun rejectsOversizedPrompt() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalPromptValidator.validate("x".repeat(LocalPromptValidator.MAX_PROMPT_CHARS + 1))
        }
    }
}
