package com.aistudio.dhanam.expense.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordLifecycleControllerTest {
    @Test
    fun acceptsOnlyOneWakeEventPerListeningCycle() {
        val controller = WakeWordLifecycleController()
        controller.beginStart()
        assertTrue(controller.beginListening())
        assertTrue(controller.beginTranscription())
        assertFalse(controller.beginTranscription())
        assertTrue(controller.beginRecovery())
        assertFalse(controller.beginTranscription())
    }

    @Test
    fun rearmReturnsToListeningOnlyAfterRecovery() {
        val controller = WakeWordLifecycleController()
        controller.beginStart()
        assertTrue(controller.beginListening())
        assertTrue(controller.beginTranscription())
        assertTrue(controller.beginRecovery())
        assertTrue(controller.beginListening())
        assertEquals(WakeWordLifecycleState.LISTENING, controller.current())
    }

    @Test
    fun stopAndFailureRequireAnExplicitRestart() {
        val controller = WakeWordLifecycleController()
        controller.beginStart()
        assertTrue(controller.beginListening())
        controller.stop()
        assertEquals(WakeWordLifecycleState.STOPPED, controller.current())
        assertFalse(controller.beginListening())

        controller.beginStart()
        controller.fail()
        assertEquals(WakeWordLifecycleState.FAILED, controller.current())
        controller.beginStart()
        assertTrue(controller.beginListening())
    }
}
