package com.snaketracker.app.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.snaketracker.app.data.AppDatabase
import com.snaketracker.app.data.Repository
import java.util.concurrent.TimeUnit

class ReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = Repository.getInstance(AppDatabase.getInstance(applicationContext))
        val snakes = repository.getSnakesWithRemindersEnabled()

        val now = System.currentTimeMillis()

        for (snake in snakes) {
            val lastFeeding = repository.getLastFeeding(snake.id)
            val lastFedDate = lastFeeding?.date
            val intervalMillis = TimeUnit.DAYS.toMillis(snake.feedingIntervalDays.toLong())

            val isDue = if (lastFedDate == null) {
                true // never fed / never logged -> remind
            } else {
                (now - lastFedDate) >= intervalMillis
            }

            if (isDue) {
                NotificationHelper.showFeedingDueNotification(applicationContext, snake.id, snake.name)
            }
        }

        return Result.success()
    }
}
