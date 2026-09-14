package com.snaketracker.app

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.junit.runner.RunWith

/**
 * GrantPermissionRule cannot grant SCHEDULE_EXACT_ALARM (it is an appop, not a
 * runtime permission — UiAutomation fails with "not a changeable permission
 * type"), so flip the appop with a shell command instead: the one-time
 * exact-alarm prompt in MainActivity must not obscure the UI under test.
 * Restores the system default afterwards.
 */
private val grantExactAlarmPermission: TestRule = TestRule { base, _: Description ->
    object : Statement() {
        override fun evaluate() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val uiAutomation = instrumentation.uiAutomation
            // The app id from the instrumented target, so the shell commands
            // track the applicationId instead of hardcoding it.
            val appPackage = instrumentation.targetContext.packageName
            uiAutomation.executeShellCommand(
                "appops set $appPackage SCHEDULE_EXACT_ALARM allow"
            ).close()
            try {
                base.evaluate()
            } finally {
                uiAutomation.executeShellCommand(
                    "appops set $appPackage SCHEDULE_EXACT_ALARM default"
                ).close()
            }
        }
    }
}

/**
 * On-device smoke check that the upgraded app launches and the snake-list
 * screen renders. Grants the notification permission and allows exact alarms
 * up front so neither runtime permission dialog nor the exact-alarm prompt
 * obscures the UI under test.
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
        .around(grantExactAlarmPermission)
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
