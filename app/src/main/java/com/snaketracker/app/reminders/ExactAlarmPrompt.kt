package com.snaketracker.app.reminders

import android.content.Context
import android.os.Build

/**
 * The one-time launch gate for the exact-alarm prompt (pure, unit-tested):
 * the prompt only makes sense on Android 12+ (below that the exact API needs
 * no permission), and only while the app cannot schedule exact alarms — once
 * the permission is held (or the user has dismissed the prompt with
 * "Not now") it never nags again. Granting via the system screen needs no
 * further prompting: the scheduling layer re-arms on
 * [android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED].
 */
fun shouldShowExactAlarmPrompt(
    sdkInt: Int,
    canScheduleExactAlarms: Boolean,
    dismissedByUser: Boolean
): Boolean =
    sdkInt >= Build.VERSION_CODES.S && !canScheduleExactAlarms && !dismissedByUser

/**
 * Persists the prompt's "Not now" dismissal so the prompt is shown at most
 * once per install (until the app's data is cleared).
 */
object ExactAlarmPromptPreferences {
    private const val PREFS_FILE = "reminder_preferences"
    private const val KEY_DISMISSED = "exact_alarm_prompt_dismissed"

    fun isDismissed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DISMISSED, false)

    fun dismiss(context: Context) {
        prefs(context).edit().putBoolean(KEY_DISMISSED, true).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
}
