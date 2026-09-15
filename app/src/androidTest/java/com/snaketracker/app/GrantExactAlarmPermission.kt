package com.snaketracker.app

import android.Manifest
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * GrantPermissionRule cannot grant SCHEDULE_EXACT_ALARM (it is an appop, not a
 * runtime permission — UiAutomation fails with "not a changeable permission
 * type"), so flip the appop with a shell command instead: the one-time
 * exact-alarm prompt in MainActivity must not obscure the UI under test.
 *
 * The appop is deliberately left granted when the test finishes. Revoking it is
 * not a neutral cleanup: the platform kills the app process the moment
 * SCHEDULE_EXACT_ALARM is taken away (`ActivityManager: Killing
 * com.snaketracker.app: schedule_exact_alarm revoked`), and androidx.test runs
 * the instrumentation *inside* that process — so restoring it in teardown
 * SIGKILLs the running instrumentation session and everything after the first
 * test in it never reports. The grant is device state on a test device, reset by
 * reinstall or `pm clear`.
 */
internal val grantExactAlarmPermission: TestRule = TestRule { base, _: Description ->
    object : Statement() {
        override fun evaluate() {
            val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
            // The app id from the instrumented target, so the shell command
            // tracks the applicationId instead of hardcoding it.
            val appPackage = InstrumentationRegistry.getInstrumentation()
                .targetContext.packageName

            // Drain the command output before closing: closing the descriptor
            // early can kill the shell process mid-command and leave
            // UiAutomation's single-command channel busy for the next test in
            // the same instrumentation session.
            ParcelFileDescriptor.AutoCloseInputStream(
                uiAutomation.executeShellCommand(
                    "appops set $appPackage SCHEDULE_EXACT_ALARM allow"
                )
            ).use { it.readBytes() }

            base.evaluate()
        }
    }
}

/**
 * The up-front suppression policy every instrumented UI test in this app needs
 * — notification grant, then the exact-alarm appop — without the Compose rule,
 * which is per class. Compose it around your own rule
 * (`uiSuppressionChain.around(composeRule)`) instead of copying the chain: a
 * new instrumented test then inherits the whole policy.
 */
internal val uiSuppressionChain: RuleChain = RuleChain
    .outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS))
    .around(grantExactAlarmPermission)
