package com.snaketracker.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.snaketracker.app.R

/**
 * One bottom-navigation tab: where it navigates, what it shows, and the tag
 * instrumented tests use to tap it.
 *
 * @param route the navigation route this tab selects (a [Routes] constant).
 * @param icon the leading icon in the navigation bar.
 * @param labelRes string resource used for both the visible label and the
 *   icon's content description.
 * @param testTag stable `Modifier.testTag` value for instrumented tests.
 */
data class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
    val testTag: String,
)

/**
 * The single source of truth for the bottom navigation bar: one entry per
 * top-level tab, in bar order. [Routes.topLevel] is derived from this list, so
 * adding a tab means adding one entry here plus its `composable` block — no
 * second list to keep in sync.
 */
object TopLevelDestinations {

    val all: List<TopLevelDestination> = listOf(
        TopLevelDestination(
            route = Routes.CALENDAR,
            icon = Icons.Filled.CalendarMonth,
            labelRes = R.string.nav_calendar,
            testTag = "nav_calendar",
        ),
        TopLevelDestination(
            route = Routes.LIST,
            icon = Icons.Filled.Pets,
            labelRes = R.string.nav_snakes,
            testTag = "nav_snakes",
        ),
        TopLevelDestination(
            route = Routes.FOOD_STOCK,
            icon = Icons.Filled.Kitchen,
            labelRes = R.string.nav_food_stock,
            testTag = "nav_food_stock",
        ),
        TopLevelDestination(
            route = Routes.SETTINGS,
            icon = Icons.Filled.Settings,
            labelRes = R.string.nav_settings,
            testTag = "nav_settings",
        ),
    )
}
