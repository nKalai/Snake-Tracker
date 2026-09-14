package com.snaketracker.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant

/**
 * The exact-alarm permission ladder (pure, unit-tested): on platforms older than
 * Android 12 the exact API needs no permission; on Android 12+ it is used only
 * when SCHEDULE_EXACT_ALARM is held, otherwise the caller falls back to the
 * permission-free inexact API.
 */
internal fun shouldUseExactApi(sdkInt: Int, exactPermissionHeld: Boolean): Boolean =
    sdkInt < Build.VERSION_CODES.S || exactPermissionHeld

/**
 * Arms exactly one alarm for the next feeding-due instant. Exact while idle
 * when the permission ladder allows it (degrading to the inexact fallback if
 * the permission is revoked between check and set), inexact while idle
 * (~15-minute tolerance) otherwise — the reminder still lands on the correct
 * day.
 */
object ReminderScheduler {
    private const val ALARM_REQUEST_CODE = 4001

    fun reschedule(context: Context, at: Instant?) {
        if (at == null) {
            cancel(context)
        } else {
            arm(context, at)
        }
    }

    fun arm(context: Context, at: Instant) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val operation = pendingIntent(context)
        val triggerAtMillis = at.toEpochMilli()

        if (shouldUseExactApi(Build.VERSION.SDK_INT, exactPermissionHeld(context))) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, operation
                )
                return
            } catch (_: SecurityException) {
                // Permission revoked between the check above and this call —
                // degrade to the inexact fallback instead of crashing.
            }
        }
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, triggerAtMillis, operation
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntent(context))
    }

    private fun exactPermissionHeld(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FEEDING_DUE)
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
