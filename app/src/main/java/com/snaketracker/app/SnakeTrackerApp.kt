package com.snaketracker.app

import android.app.Application
import android.util.Log
import com.snaketracker.app.data.AppDatabase
import com.snaketracker.app.data.Repository
import com.snaketracker.app.data.backup.BackupExportContentGateway
import com.snaketracker.app.data.backup.BackupExportGateway
import com.snaketracker.app.data.backup.BackupImportContentGateway
import com.snaketracker.app.data.backup.BackupImportGateway
import com.snaketracker.app.data.backup.BackupRepository
import com.snaketracker.app.reminders.NotificationHelper
import com.snaketracker.app.reminders.ReminderScheduler
import com.snaketracker.app.reminders.nextAlarmAtFlow
import com.snaketracker.app.reminders.restartOnFailure
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class SnakeTrackerApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: Repository by lazy { Repository.getInstance(database) }
    val backupRepository: BackupRepository by lazy {
        BackupRepository(database, appVersion = BuildConfig.VERSION_NAME)
    }
    val backupExportGateway: BackupExportGateway by lazy {
        BackupExportContentGateway(contentResolver)
    }
    val backupImportGateway: BackupImportGateway by lazy {
        BackupImportContentGateway(contentResolver)
    }

    // Last-resort net: nothing launched on this scope may take the process down
    // over a reminder failure; the arming paths log and recover instead.
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            Log.e(LOG_TAG, "Unhandled failure on the app scope", e)
        }
    )

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        observeReminders()
    }

    // Arms the single next-due feeding alarm on app start, then re-arms only
    // when a snake/feeding data change moves the computed instant (or cancels
    // when there is nothing left to remind about). A failure under the Room
    // flows is logged and the collection restarts, so the alarm keeps
    // following data changes for the lifetime of the process.
    private fun observeReminders() {
        appScope.launch {
            restartOnFailure(onError = { e ->
                Log.e(LOG_TAG, "Reminder observation failed; restarting", e)
            }) {
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

    private companion object {
        const val LOG_TAG = "SnakeTrackerApp"
    }
}
