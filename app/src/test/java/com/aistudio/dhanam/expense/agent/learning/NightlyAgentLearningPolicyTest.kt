package com.aistudio.dhanam.expense.agent.learning

import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightlyAgentLearningPolicyTest {
    @Test fun windowIncludesOneAmAndExcludesSixAm() {
        assertTrue(NightlyAgentLearningScheduler.isWithinWindow(LocalTime.of(1, 0)))
        assertTrue(NightlyAgentLearningScheduler.isWithinWindow(LocalTime.of(5, 59, 59)))
        assertFalse(NightlyAgentLearningScheduler.isWithinWindow(LocalTime.of(6, 0)))
        assertFalse(NightlyAgentLearningScheduler.isWithinWindow(LocalTime.MIDNIGHT))
        assertFalse(NightlyAgentLearningScheduler.isWithinWindow(LocalTime.of(23, 59)))
    }
}
