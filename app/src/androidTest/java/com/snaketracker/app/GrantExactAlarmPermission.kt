package com.snaketracker.app

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * GrantPermissionRule cannot grant SCHEDULE_EXACT_ALARM (it is an appop, not a
 * runtime permission — UiAutomation fails with "not a changeable permission
 * type"), so flip the appop with a shell command instead: the one-time
 * exact-alarm prompt in MainActivity must not obscure the UI under test.
 * Restores the system default afterwards.
 */
internal val grantExactAlarmPermission: TestRule = TestRule { base, _: Description ->
    object : Statement() {
        override fun evaluate() {
            val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
            // The app id from the instrumented target, so the shell commands
            // track the applicationId instead of hardcoding it.
            val appPackage = InstrumentationRegistry.getInstrumentation()
                .targetContext.packageName

            fun runAppOps(args: String) {
                // Drain the command output before closing: closing the
                // descriptor early can kill the shell process mid-command and
                // leave UiAutomation's single-command channel busy for the
                // next test in the same instrumentation session.
                ParcelFileDescriptor.AutoCloseInputStream(
                    uiAutomation.executeShellCommand(args)
                ).use { it.readBytes() }
            }

            runAppOps("appops set $appPackage SCHEDULE_EXACT_ALARM allow")
            try {
                base.evaluate()
            } finally {
                runAppOps("appops set $appPackage SCHEDULE_EXACT_ALARM default")
            }
        }
    }
}
