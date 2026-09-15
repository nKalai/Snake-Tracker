package com.snaketracker.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Route-contract test for the top-level bottom-nav destinations. The expected
 * set comes from the issue spec (issue #24: Calendar / Snakes / Food Stock /
 * Settings), not from re-reading [Routes].
 */
class RoutesTest {

    @Test
    fun settings_isATopLevelDestinationAlongsideTheExistingThree() {
        // Independent literals from the issue spec, not Routes constants.
        assertEquals(setOf("calendar", "list", "settings", "food_stock"), Routes.topLevel)
    }
}
