package com.aistudio.dhanam.expense.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecommendationFeedbackEntityTest {
    @Test
    fun acceptsOneBoundedDecisionForARecommendationAndMonth() {
        val feedback = RecommendationFeedbackEntity(
            recommendationId = "increase-food-october",
            periodMonth = "2026-10",
            decision = "HELPFUL",
            createdAtEpochMillis = 1L
        )
        assertEquals("HELPFUL", feedback.decision)
    }

    @Test
    fun rejectsInvalidMonthAndDecision() {
        assertThrows(IllegalArgumentException::class.java) {
            RecommendationFeedbackEntity(
                recommendationId = "increase-food",
                periodMonth = "2026-13",
                decision = "HELPFUL",
                createdAtEpochMillis = 1L
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecommendationFeedbackEntity(
                recommendationId = "increase-food",
                periodMonth = "2026-10",
                decision = "RUN_AUTOMATION",
                createdAtEpochMillis = 1L
            )
        }
    }
}
