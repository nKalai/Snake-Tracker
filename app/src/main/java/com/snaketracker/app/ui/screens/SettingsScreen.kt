package com.snaketracker.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snaketracker.app.R
import com.snaketracker.app.ui.model.BackupImportState
import com.snaketracker.app.ui.viewmodel.SnakeViewModel

/**
 * Top-level Settings screen. Its first capability is the JSON backup import
 * (issue #28): the row launches the Storage Access Framework open-document
 * picker for JSON files, a destructive-worded confirmation dialog gates the
 * replace-everything run, and the outcome is reported in a result dialog.
 *
 * The picker lives behind [BackupSourcePicker] so instrumented tests can
 * drive the dialogs without the system file UI; the real default is the SAF
 * contract wired straight into [SnakeViewModel.requestBackupImport].
 *
 * INSERTION POINT (import/export): the export action (issue #26) joins the
 * import row in the `Scaffold` body below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SnakeViewModel,
    modifier: Modifier = Modifier,
    backupSourcePicker: BackupSourcePicker =
        rememberOpenDocumentPicker { uri -> if (uri != null) viewModel.requestBackupImport(uri) }
) {
    val pendingImportUri by viewModel.pendingImportUri.collectAsStateWithLifecycle()
    val importState by viewModel.backupImportState.collectAsStateWithLifecycle()

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
                headlineContent = { Text(stringResource(R.string.settings_import_backup)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_import_backup_summary))
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null
                    )
                },
                modifier = Modifier
                    .testTag("import_backup_row")
                    .clickable { backupSourcePicker.launch(JSON_MIME_TYPES) }
            )
        }
    }

    if (pendingImportUri != null) {
        BackupImportConfirmDialog(
            onConfirm = viewModel::confirmBackupImport,
            onDismiss = viewModel::cancelBackupImport
        )
    }

    val state = importState
    if (state != null) {
        BackupImportResultDialog(
            state = state,
            onDismiss = viewModel::dismissBackupImport
        )
    }
}

/**
 * The replace-everything warning shown between picking a file and running
 * the import (issue #28 WB2): destructive-worded, and every path out of it
 * is explicit — [onDismiss] (button or tap-outside) imports nothing.
 */
@Composable
private fun BackupImportConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.backup_import_confirm_title),
                modifier = Modifier.testTag("backup_import_confirm_title")
            )
        },
        text = { Text(stringResource(R.string.backup_import_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("backup_import_confirm_yes")) {
                Text(
                    stringResource(R.string.backup_import_confirm_import),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("backup_import_confirm_cancel")) {
                Text(stringResource(R.string.backup_import_confirm_cancel))
            }
        }
    )
}

/**
 * Outcome dialog for a backup import: success lists the per-table imported
 * counts, failure names the reason (issue #28 WB4).
 */
@Composable
private fun BackupImportResultDialog(
    state: BackupImportState,
    onDismiss: () -> Unit
) {
    val title: String
    val message: String
    when (state) {
        is BackupImportState.Success -> {
            title = stringResource(R.string.backup_import_success_title)
            val counts = state.counts
            message = listOf(
                pluralStringResource(R.plurals.import_count_snakes, counts.snakes, counts.snakes),
                pluralStringResource(R.plurals.import_count_feedings, counts.feedings, counts.feedings),
                pluralStringResource(R.plurals.import_count_sheds, counts.sheds, counts.sheds),
                pluralStringResource(R.plurals.import_count_weights, counts.weights, counts.weights),
                pluralStringResource(
                    R.plurals.import_count_food_stock,
                    counts.foodStock,
                    counts.foodStock
                )
            ).joinToString("\n")
        }

        is BackupImportState.Failure -> {
            title = stringResource(R.string.backup_import_failure_title)
            message = stringResource(state.messageRes)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, modifier = Modifier.testTag("backup_import_dialog_title"))
        },
        text = {
            Text(message, modifier = Modifier.testTag("backup_import_message"))
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("backup_import_dismiss")) {
                Text(stringResource(R.string.backup_import_dismiss))
            }
        }
    )
}

/** Launches the open-document picker restricted to [mimeTypes]. */
fun interface BackupSourcePicker {
    fun launch(mimeTypes: Array<String>)
}

/**
 * Production [BackupSourcePicker]: `ACTION_OPEN_DOCUMENT` for JSON via
 * [ActivityResultContracts.OpenDocument]; the user-chosen URI (or null on
 * cancel) is handed to [onDocumentPicked].
 */
@Composable
private fun rememberOpenDocumentPicker(
    onDocumentPicked: (Uri?) -> Unit
): BackupSourcePicker {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> onDocumentPicked(uri) }
    return remember(launcher) {
        BackupSourcePicker { mimeTypes -> launcher.launch(mimeTypes) }
    }
}

private val JSON_MIME_TYPES = arrayOf("application/json")
