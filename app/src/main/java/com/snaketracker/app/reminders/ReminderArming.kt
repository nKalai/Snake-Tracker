package com.snaketracker.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import com.snaketracker.app.data.AppDatabase
import com.snaketracker.app.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Recomputes the next due instant from the Repository snapshot and re-arms the
 * single alarm (or cancels everything when there is none). Used by the
 * system-event receivers that must re-arm outside any data change: device
 * reboot and exact-alarm permission re-grant (the system already cancels the
 * alarm on revoke).
 */
object ReminderArming {
    suspend fun refresh(context: Context) {
        val appContext = context.applicationContext
        val repository = Repository.getInstance(AppDatabase.getInstance(appContext))
        val plan = ReminderPlanner.plan(
            candidates = repository.getReminderSnapshot(),
            now = Instant.now(),
            zone = ZoneId.systemDefault()
        )
        ReminderScheduler.reschedule(appContext, plan.nextAlarmAt)
    }
}

// goAsync() must run synchronously on the main thread inside onReceive; the
// refresh then continues off-thread until the receiver's result is finished.
internal fun BroadcastReceiver.rearmInBackground(context: Context) {
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            ReminderArming.refresh(context.applicationContext)
        } finally {
            pendingResult.finish()
        }
    }
}
