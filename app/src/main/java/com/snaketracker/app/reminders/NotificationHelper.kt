package com.snaketracker.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.snaketracker.app.MainActivity
import com.snaketracker.app.R

object NotificationHelper {
    const val CHANNEL_ID = "feeding_reminders"

    // Importance is immutable after creation, so this is the one creation site
    // (called from SnakeTrackerApp.onCreate) and the one importance value.
    // HIGH makes every post a heads-up banner (visible without the shade).
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun showFeedingDueNotification(context: Context, snakeId: Long, snakeName: String) {
        // POST_NOTIFICATIONS is a runtime permission on Android 13+; skip posting if the
        // user has not granted it, matching the app's "no notification when denied" intent.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // Tap-to-open: the content intent launches MainActivity (the launcher
        // activity); the request code is the per-snake notification id so each
        // snake's intent stays its own.
        val contentIntent = PendingIntent.getActivity(
            context,
            snakeId.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.notification_feeding_due_title, snakeName))
            .setContentText(context.getString(R.string.notification_feeding_due_text, snakeName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            // Auto-cancel: the tap lands in the log and clears the notification.
            .setAutoCancel(true)
            .build()

        // Each snake gets its own notification id so multiple reminders can stack;
        // re-posting the same id replaces that id's notification (notify-by-id rule).
        NotificationManagerCompat.from(context).notify(snakeId.toInt(), notification)
    }
}
