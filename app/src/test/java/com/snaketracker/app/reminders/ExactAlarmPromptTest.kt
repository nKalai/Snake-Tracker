package com.snaketracker.app.reminders

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactAlarmPromptTest {

    @Test
    fun android12Plus_withoutExactAlarms_andNeverDismissed_showsThePrompt() {
        // Android 14+ default: SCHEDULE_EXACT_ALARM is denied on install, so the
        // app cannot arm minute-exact reminders until the user allows it.
        assertTrue(
            shouldShowExactAlarmPrompt(
                sdkInt = 34,
                canScheduleExactAlarms = false,
                dismissedByUser = false
            )
        )
    }

    @Test
    fun belowAndroid12_neverShowsThePrompt() {
        // SCHEDULE_EXACT_ALARM does not exist below API 31; the exact API is
        // free, so there is nothing to ask about.
        assertFalse(
            shouldShowExactAlarmPrompt(
                sdkInt = 26,
                canScheduleExactAlarms = false,
                dismissedByUser = false
            )
        )
        assertFalse(
            shouldShowExactAlarmPrompt(
                sdkInt = 30,
                canScheduleExactAlarms = false,
                dismissedByUser = false
            )
        )
    }

    @Test
    fun android12Plus_withExactAlarmsAlreadyAllowed_neverShowsThePrompt() {
        // Granting via the system screen is enough on its own; a later launch
        // must not ask again.
        assertFalse(
            shouldShowExactAlarmPrompt(
                sdkInt = 34,
                canScheduleExactAlarms = true,
                dismissedByUser = false
            )
        )
    }

    @Test
    fun android12Plus_afterNotNow_neverShowsThePromptAgain() {
        // "Not now" is persisted, so the prompt never nags on later launches —
        // even though the permission is still missing.
        assertFalse(
            shouldShowExactAlarmPrompt(
                sdkInt = 34,
                canScheduleExactAlarms = false,
                dismissedByUser = true
            )
        )
    }
}
