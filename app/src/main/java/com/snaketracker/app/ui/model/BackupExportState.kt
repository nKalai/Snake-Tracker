package com.snaketracker.app.ui.model

import androidx.annotation.StringRes
import com.snaketracker.app.R
import com.snaketracker.app.data.backup.BackupExportFailureReason

/**
 * Outcome of a user-initiated backup export, rendered by the Settings screen
 * result dialog. [Success] names the file that was actually saved;
 * [Failure] carries a machine-readable reason the dialog maps to a string
 * resource, so no data-layer or provider text reaches the user (issue #26 WB3).
 */
sealed interface BackupExportState {
    data class Success(val fileName: String) : BackupExportState
    data class Failure(val reason: BackupExportFailureReason) : BackupExportState
}

/**
 * The one place an export failure reason becomes dialog copy — the export
 * twin of [backupImportMessageFor]. Every [BackupExportFailureReason] must
 * appear here: a new reason stops this `when` from compiling until it has
 * its own distinct message, so the composable never owns the mapping.
 */
@StringRes
internal fun backupExportMessageFor(reason: BackupExportFailureReason): Int = when (reason) {
    BackupExportFailureReason.DESTINATION_UNOPENABLE -> R.string.backup_export_failure_reason_destination
    BackupExportFailureReason.DESTINATION_UNWRITABLE -> R.string.backup_export_failure_reason_unwritable
    BackupExportFailureReason.UNKNOWN -> R.string.backup_export_failure_reason_unknown
}
