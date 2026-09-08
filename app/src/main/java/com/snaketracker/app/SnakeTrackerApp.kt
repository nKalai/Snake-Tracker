package com.snaketracker.app

import android.app.Application
import com.snaketracker.app.data.AppDatabase
import com.snaketracker.app.data.Repository
import com.snaketracker.app.reminders.NotificationHelper
import com.snaketracker.app.reminders.ReminderScheduler

class SnakeTrackerApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: Repository by lazy { Repository.getInstance(database) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        // Schedules a local, on-device daily check for due feedings.
        // This never contacts a server - it just runs a periodic WorkManager job.
        ReminderScheduler.scheduleDailyCheck(this)
    }
}
