package com.snaketracker.app

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
 *
 * The assertions target stable [androidx.compose.ui.platform.testTag]s rather
 * than user-facing copy, so this test stays isolated from both the persisted
 * Room database (it does not assert the "no snakes yet" empty state, which only
 * renders when the DB is empty) and from copy changes.
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
        composeRule.onNodeWithTag("nav_snakes").performClick()

        // The snake-list screen rendered: its title bar is displayed. We assert
        // on the title's test tag only; the empty-state text depends on the live
        // Room database, so asserting it would make this smoke test flaky on a
        // device that already holds snake rows.
        composeRule.onNodeWithTag("snake_list_title").assertIsDisplayed()
    }
}
