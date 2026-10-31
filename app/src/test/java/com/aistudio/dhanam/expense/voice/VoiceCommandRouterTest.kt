package com.aistudio.dhanam.expense.voice

import com.aistudio.dhanam.expense.ui.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandRouterTest {
    @Test fun routesExplicitScreenNavigationCommands() {
        assertEquals(AppDestination.EXPENSES, VoiceCommandRouter.destinationFor("show expenses"))
        assertEquals(AppDestination.KHATA, VoiceCommandRouter.destinationFor("open khata"))
        assertEquals(AppDestination.INSIGHTS, VoiceCommandRouter.destinationFor("go to insights"))
        assertEquals(AppDestination.SETTINGS, VoiceCommandRouter.destinationFor("open settings"))
        assertEquals(AppDestination.AGENT, VoiceCommandRouter.destinationFor("open Jarvis"))
    }

    @Test fun leavesFinanceCommandsForTheSharedAgent() {
        assertNull(VoiceCommandRouter.destinationFor("note an expense of 250 for lunch"))
        assertNull(VoiceCommandRouter.destinationFor("Ravi repaid 500"))
        assertNull(VoiceCommandRouter.destinationFor("how much did I spend this month"))
    }
}
