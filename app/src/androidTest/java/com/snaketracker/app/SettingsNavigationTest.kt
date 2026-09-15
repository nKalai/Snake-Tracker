package com.snaketracker.app

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * On-device check for the Settings bottom-nav destination (issue #24),
 * following the single-flow pattern of [SnakeListScreenTest]: the app is
 * launched once and the whole acceptance flow runs in one test — open
 * Settings from the bottom bar, verify its title, switch to another
 * top-level tab, then return and verify the tab-selected state is preserved.
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
    val rule: TestRule = RuleChain
        .outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS))
        .around(grantExactAlarmPermission)
        .around(composeRule)

    @Test
    fun settingsDestination_reachableAndTabStatePreserved() {
        // The app opens on the Calendar tab; tap the fourth bottom-nav item.
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("settings_title").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_settings").assertIsSelected()

        // Move to another top-level tab: selection follows the visible tab.
        composeRule.onNodeWithTag("nav_snakes").performClick()
        composeRule.onNodeWithTag("nav_snakes").assertIsSelected()
        composeRule.onNodeWithTag("nav_settings").assertIsNotSelected()

        // Return to Settings: it is selected again and its screen is shown.
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("nav_settings").assertIsSelected()
        composeRule.onNodeWithTag("settings_title").assertIsDisplayed()
    }
}
