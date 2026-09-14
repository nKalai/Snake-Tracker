package com.snaketracker.app.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The system broadcasts this when the user grants (or revokes) SCHEDULE_EXACT_ALARM.
 * On revoke the system already cancels our alarms; on grant we re-arm so the
 * next reminder uses the exact API again.
 */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            rearmInBackground(context)
        }
    }
}
