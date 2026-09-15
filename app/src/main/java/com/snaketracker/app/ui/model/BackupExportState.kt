package com.snaketracker.app.ui.model

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
