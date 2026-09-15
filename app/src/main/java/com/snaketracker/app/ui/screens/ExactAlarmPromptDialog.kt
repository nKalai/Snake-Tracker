package com.snaketracker.app.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.snaketracker.app.R

/**
 * One-time prompt explaining that enabling "Alarms & reminders" makes feeding
 * reminders minute-exact. Stateless: the launch gate lives in
 * `reminders/ExactAlarmPrompt.kt`; this composable only renders the copy and
 * delegates the two user choices. Dismissing by tapping outside counts as
 * "Not now" (both routes never nag again).
 */
@Composable
fun ExactAlarmPromptDialog(
    onEnable: () -> Unit,
    onNotNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text(stringResource(R.string.exact_alarm_prompt_title)) },
        text = { Text(stringResource(R.string.exact_alarm_prompt_message)) },
        confirmButton = {
            TextButton(onClick = onEnable) {
                Text(stringResource(R.string.exact_alarm_prompt_enable))
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) {
                Text(stringResource(R.string.exact_alarm_prompt_not_now))
            }
        }
    )
}
