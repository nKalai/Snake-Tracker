package com.snaketracker.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Route-contract test for the top-level bottom-nav destinations. The expected
 * values come from the issue spec (issue #24: Calendar / Snakes / Food Stock /
 * Settings), not from re-reading [Routes] or [TopLevelDestinations].
 */
class RoutesTest {

    /** Tab routes in bar order, transcribed from the issue #24 spec. */
    private val specTabRoutes = listOf("calendar", "list", "food_stock", "settings")

    @Test
    fun settings_isATopLevelDestinationAlongsideTheExistingThree() {
        // Independent literals from the issue spec, not Routes constants.
        assertEquals(specTabRoutes.toSet(), Routes.topLevel)
    }

    @Test
    fun bottomNavDescriptors_areTheSpecTabsInBarOrder() {
        assertEquals(specTabRoutes, TopLevelDestinations.all.map { it.route })
    }

    @Test
    fun bottomNavDescriptors_carryStringResourceLabelsAndStableTestTags() {
        val descriptors = TopLevelDestinations.all
        // Labels come from string resources: a real (non-zero) resource id for
        // every tab, and no two tabs sharing the same copy.
        assertTrue(descriptors.all { it.labelRes != 0 })
        assertEquals(descriptors.size, descriptors.map { it.labelRes }.toSet().size)
        // Each tab is addressable from instrumented tests by a stable tag.
        assertEquals(
            listOf("nav_calendar", "nav_snakes", "nav_food_stock", "nav_settings"),
            descriptors.map { it.testTag }
        )
    }
}
