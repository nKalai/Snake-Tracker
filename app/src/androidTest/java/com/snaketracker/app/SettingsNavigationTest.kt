package com.snaketracker.app

import android.view.KeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * On-device check for the Settings bottom-nav destination (issue #24),
 * following the single-flow pattern of [SnakeListScreenTest]: the app is
 * launched once and the whole acceptance flow runs in one test — open
 * Settings from the bottom bar, verify its title, switch to another
 * top-level tab, then return and verify the selected tab follows the visible
 * destination. Tab *selection* is what is asserted: the shared
 * `navigateTopLevel` pattern pops back to Calendar, so revisiting Settings
 * recreates that destination rather than preserving its state.
 *
 * A second test covers the other half of that pattern — system back from
 * Settings lands on Calendar instead of leaving the app.
 *
 * Runtime permission dialogs and the one-time exact-alarm prompt are
 * suppressed up front, and assertions target stable
 * [androidx.compose.ui.platform.testTag]s so the test stays isolated from the
 * persisted Room database and from copy changes.
 */
@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rule: TestRule = uiSuppressionChain.around(composeRule)

    @Test
    fun settingsDestination_reachableAndTabSelectionPreserved() {
        // The app opens on the Calendar tab; tap the fourth bottom-nav item.
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("settings_title").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_settings").assertIsSelected()

        // Move to another top-level tab: the visible destination swaps and the
        // selection follows it. Settings is gone from the composition, not just
        // hidden, so its title node no longer exists.
        composeRule.onNodeWithTag("nav_snakes").performClick()
        composeRule.onNodeWithTag("snake_list_title").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_title").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_snakes").assertIsSelected()
        composeRule.onNodeWithTag("nav_settings").assertIsNotSelected()

        // Return to Settings: it is selected again and its screen is shown.
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("nav_settings").assertIsSelected()
        composeRule.onNodeWithTag("settings_title").assertIsDisplayed()
    }

    /**
     * WB2 "back-safe": `navigateTopLevel` keeps Calendar at the base of the
     * stack (`popUpTo(CALENDAR, inclusive = false)`), so pressing system back
     * on Settings pops back to Calendar instead of exiting the app.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun settingsTab_backPressReturnsToCalendar() {
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("settings_title").assertIsDisplayed()

        pressSystemBack()

        // Calendar is on screen and Settings is gone; had the app exited, the
        // composition would be gone with it and these lookups would fail.
        composeRule.waitUntilExactlyOneExists(hasTestTag("calendar_title"))
        composeRule.onNodeWithTag("calendar_title").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_title").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_calendar").assertIsSelected()
    }

    /**
     * Injects a real system back key into the focused window, so the assertion
     * covers the platform back path rather than only the back dispatcher.
     * Must not be called from the app's main thread — the instrumentation test
     * thread is not it.
     */
    private fun pressSystemBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
    }
}
