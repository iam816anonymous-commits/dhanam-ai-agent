package com.aistudio.dhanam.expense.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDestinationTest {
    @Test
    fun routesAreUnique() {
        val routes = AppDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun allPrimaryDestinationsHaveLabels() {
        assertEquals(5, AppDestination.entries.size)
        assertTrue(AppDestination.entries.all { it.label.isNotBlank() && it.route.isNotBlank() })
    }
}
