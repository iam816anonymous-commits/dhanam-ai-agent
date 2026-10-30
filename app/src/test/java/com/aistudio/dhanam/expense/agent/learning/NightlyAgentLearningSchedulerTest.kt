package com.aistudio.dhanam.expense.agent.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class NightlyAgentLearningSchedulerTest {
    @Test fun nightlyModelWindowIncludesOneAmAndExcludesSixAm() {
        assertFalse(NightlyAgentLearningScheduler.isWithinWindow(LocalTime.of(0, 59, 59)))
        assertTrue(NightlyAgentLearningScheduler.isWithinWindow(LocalTime.of(1, 0)))
        assertTrue(NightlyAgentLearningScheduler.isWithinWindow(LocalTime.of(5, 59, 59)))
        assertFalse(NightlyAgentLearningScheduler.isWithinWindow(LocalTime.of(6, 0)))
    }
}
