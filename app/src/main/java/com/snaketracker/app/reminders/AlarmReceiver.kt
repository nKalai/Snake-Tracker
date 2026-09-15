package com.snaketracker.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fired by the single armed alarm: runs the shared plan→notify→re-arm sequence
 * — one notification per due-now snake via the existing notification helper
 * (per-snake ids, silent skip when the notification permission is denied),
 * then the next alarm is re-armed.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FEEDING_DUE) return

        launchGoAsync(context) { ReminderArming.refresh(it) }
    }

    companion object {
        const val ACTION_FEEDING_DUE = "com.snaketracker.app.reminders.ACTION_FEEDING_DUE"
    }
}
