package com.snaketracker.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.snaketracker.app.data.AppDatabase
import com.snaketracker.app.data.Repository
import java.time.Instant
import java.time.ZoneId

/**
 * Fired by the single armed alarm: posts one notification per due-now snake via
 * the existing notification helper (per-snake ids, silent skip when the
 * notification permission is denied), then re-arms the next alarm.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FEEDING_DUE) return

        launchGoAsync(context) { appContext ->
            val repository = Repository.getInstance(AppDatabase.getInstance(appContext))
            val candidates = repository.getReminderSnapshot()
            val plan = ReminderPlanner.plan(
                candidates = candidates,
                now = Instant.now(),
                zone = ZoneId.systemDefault()
            )
            for (candidate in candidates) {
                if (candidate.snakeId in plan.dueSnakeIds) {
                    NotificationHelper.showFeedingDueNotification(
                        appContext, candidate.snakeId, candidate.name
                    )
                }
            }
            ReminderScheduler.reschedule(appContext, plan.nextAlarmAt)
        }
    }

    companion object {
        const val ACTION_FEEDING_DUE = "com.snaketracker.app.reminders.ACTION_FEEDING_DUE"
    }
}
