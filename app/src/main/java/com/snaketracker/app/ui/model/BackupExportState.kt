package com.snaketracker.app.ui.model

/**
 * Outcome of a user-initiated backup export, rendered by the Settings screen
 * result dialog. [Success] names the file that was actually saved;
 * [Failure] carries the reason (an IO error message) for the dialog copy.
 */
sealed interface BackupExportState {
    data class Success(val fileName: String) : BackupExportState
    data class Failure(val reason: String) : BackupExportState
}
