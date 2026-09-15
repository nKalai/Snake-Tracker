package com.snaketracker.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snaketracker.app.R
import com.snaketracker.app.ui.model.BackupExportState
import com.snaketracker.app.ui.viewmodel.SnakeViewModel
import java.time.LocalDate

/**
 * Top-level Settings screen. Its first capability is the JSON backup export
 * (issue #26): the row launches the Storage Access Framework create-document
 * picker with a date-stamped suggested name, and the ViewModel's export
 * result is reported in a dialog.
 *
 * The picker lives behind [BackupDestinationPicker] so instrumented tests
 * can drive the result dialog without the system file UI; the real default
 * is the SAF contract wired straight into [SnakeViewModel.exportBackup].
 *
 * INSERTION POINT (import/export): the import action (issue #28) joins the
 * export row in the `Scaffold` body below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SnakeViewModel,
    modifier: Modifier = Modifier,
    backupDestinationPicker: BackupDestinationPicker =
        rememberCreateDocumentPicker { uri -> if (uri != null) viewModel.exportBackup(uri) }
) {
    val exportState by viewModel.backupExportState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        modifier = Modifier.testTag("settings_title")
                    )
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_export_backup)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_export_backup_summary))
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = null
                    )
                },
                modifier = Modifier
                    .testTag("export_backup_row")
                    .clickable {
                        backupDestinationPicker.launch(backupFileName(LocalDate.now()))
                    }
            )
        }
    }

    val state = exportState
    if (state != null) {
        BackupExportResultDialog(
            state = state,
            onDismiss = viewModel::dismissBackupExport
        )
    }
}

/** Outcome dialog for a backup export: success names the saved file, failure states the reason. */
@Composable
private fun BackupExportResultDialog(
    state: BackupExportState,
    onDismiss: () -> Unit
) {
    val title: String
    val message: String
    when (state) {
        is BackupExportState.Success -> {
            title = stringResource(R.string.backup_export_success_title)
            message = stringResource(R.string.backup_export_success_message, state.fileName)
        }

        is BackupExportState.Failure -> {
            title = stringResource(R.string.backup_export_failure_title)
            message = stringResource(R.string.backup_export_failure_message, state.reason)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, modifier = Modifier.testTag("backup_export_dialog_title"))
        },
        text = {
            Text(message, modifier = Modifier.testTag("backup_export_message"))
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("backup_export_dismiss")) {
                Text(stringResource(R.string.backup_export_dismiss))
            }
        }
    )
}

/**
 * The suggested name for a backup document: `snake-tracker-backup-<yyyy-MM-dd>.json`,
 * dated with the device's local calendar day ([LocalDate.toString] is ISO
 * `yyyy-MM-dd`). The picker lets the user change it; the gateway then reports
 * the name that was actually saved.
 */
internal fun backupFileName(date: LocalDate): String = "snake-tracker-backup-$date.json"

/** Launches the create-document picker with [suggestedFileName]. */
fun interface BackupDestinationPicker {
    fun launch(suggestedFileName: String)
}

/**
 * Production [BackupDestinationPicker]: `ACTION_CREATE_DOCUMENT` for JSON via
 * [ActivityResultContracts.CreateDocument]; the user-chosen URI (or null on
 * cancel) is handed to [onDocumentPicked].
 */
@Composable
private fun rememberCreateDocumentPicker(
    onDocumentPicked: (Uri?) -> Unit
): BackupDestinationPicker {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(JSON_MIME_TYPE)
    ) { uri -> onDocumentPicked(uri) }
    return remember(launcher) {
        BackupDestinationPicker { name -> launcher.launch(name) }
    }
}

private const val JSON_MIME_TYPE = "application/json"
