package com.snaketracker.app

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * On-device smoke check that the upgraded app launches and the snake-list
 * screen renders. Grants the notification permission up front so the runtime
 * permission dialog does not obscure the UI under test.
 */
@RunWith(AndroidJUnit4::class)
class SnakeListScreenTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rule: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS))
        .around(composeRule)

    @Test
    fun snakeListScreen_renders() {
        // The app opens on the Calendar tab; tap the bottom-nav "Snakes" item.
        composeRule.onNodeWithText("Snakes").performClick()

        // The snake-list screen renders: its title bar and the empty state.
        composeRule.onNodeWithText("My Snakes").assertIsDisplayed()
        composeRule.onNodeWithText("No snakes yet. Tap + to add your first one.").assertIsDisplayed()
    }
}
