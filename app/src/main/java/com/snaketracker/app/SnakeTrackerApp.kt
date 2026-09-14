package com.snaketracker.app

import android.app.Application
import com.snaketracker.app.data.AppDatabase
import com.snaketracker.app.data.Repository
import com.snaketracker.app.reminders.NotificationHelper
import com.snaketracker.app.reminders.ReminderScheduler
import com.snaketracker.app.reminders.nextAlarmAtFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class SnakeTrackerApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: Repository by lazy { Repository.getInstance(database) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        observeReminders()
    }

    // Arms the single next-due feeding alarm on app start, then re-arms only
    // when a snake/feeding data change moves the computed instant (or cancels
    // when there is nothing left to remind about).
    private fun observeReminders() {
        appScope.launch {
            nextAlarmAtFlow(
                snakes = repository.getAllSnakes(),
                lastFeedings = repository.getLastFeedingPerSnake(),
                now = { Instant.now() },
                zone = ZoneId.systemDefault()
            ).collect { next ->
                ReminderScheduler.reschedule(this@SnakeTrackerApp, next)
            }
        }
    }
}
