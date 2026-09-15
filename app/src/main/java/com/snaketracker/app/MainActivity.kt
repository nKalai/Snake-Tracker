package com.snaketracker.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.snaketracker.app.navigation.SnakeTrackerNavGraph
import com.snaketracker.app.reminders.ExactAlarmPromptPreferences
import com.snaketracker.app.reminders.ReminderArming
import com.snaketracker.app.reminders.exactPermissionHeld
import com.snaketracker.app.reminders.shouldShowExactAlarmPrompt
import com.snaketracker.app.ui.screens.ExactAlarmPromptDialog
import com.snaketracker.app.ui.theme.SnakeTrackerTheme
import com.snaketracker.app.ui.viewmodel.SnakeViewModel
import com.snaketracker.app.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: reminders simply won't show a system notification if denied */ }

    // Whether the one-time exact-alarm prompt is on screen. Decided once at
    // launch (the same permission-state model as the notification request
    // above); the dialog actions flip it back off.
    private var showExactAlarmPrompt by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        showExactAlarmPrompt = shouldShowExactAlarmPrompt(
            sdkInt = Build.VERSION.SDK_INT,
            canScheduleExactAlarms = exactPermissionHeld(this),
            dismissedByUser = ExactAlarmPromptPreferences.isDismissed(this)
        )

        val app = application as SnakeTrackerApp

        setContent {
            SnakeTrackerTheme {
                val viewModel: SnakeViewModel = viewModel(
                    factory = ViewModelFactory(
                        app.repository,
                        app.backupRepository,
                        app.backupImportGateway,
                        // Issue #28 WB5: after a successful import the alarm is
                        // recomputed from the new data through the reschedule-
                        // only entry — the same next-instant + re-arm pair the
                        // launch-time observer uses, with no due-now
                        // notification pass (importing is not a due-time
                        // event). Off Main, on the receivers' Dispatchers.
                        // Default convention.
                        rearmReminders = {
                            withContext(Dispatchers.Default) {
                                ReminderArming.reschedule(app)
                            }
                        }
                    )
                )
                SnakeTrackerNavGraph(viewModel = viewModel)

                if (showExactAlarmPrompt) {
                    ExactAlarmPromptDialog(
                        onEnable = {
                            // Deep-link to the system "Alarms & reminders"
                            // screen; no dismissal is persisted — if the user
                            // grants there, later launches see the permission
                            // held and never ask again.
                            showExactAlarmPrompt = false
                            startActivity(exactAlarmSettingsIntent())
                        },
                        onNotNow = {
                            // Persisted so the prompt never nags again.
                            ExactAlarmPromptPreferences.dismiss(this@MainActivity)
                            showExactAlarmPrompt = false
                        }
                    )
                }
            }
        }
    }

    private fun exactAlarmSettingsIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.parse("package:$packageName"))
}
