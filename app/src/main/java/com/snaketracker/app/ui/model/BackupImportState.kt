package com.snaketracker.app.ui.model

import androidx.annotation.StringRes
import com.snaketracker.app.R
import com.snaketracker.app.data.backup.ImportFailure
import com.snaketracker.app.data.backup.ImportSummary

/**
 * Outcome of a user-initiated backup import, rendered by the Settings screen
 * result dialog. [Success] carries the per-table counts as the UI-side
 * [Counts] view of the engine's report; [Failure] carries the message
 * resource that names the reason (issue #28 WB4).
 */
sealed interface BackupImportState {
    data class Success(val counts: Counts) : BackupImportState
    data class Failure(@StringRes val messageRes: Int) : BackupImportState

    /**
     * The per-table imported counts the result dialog renders, in table
     * order — the UI's own view of [ImportSummary.Success], so the dialog's
     * call sites never spell two nested types with the same name.
     */
    data class Counts(
        val snakes: Int,
        val feedings: Int,
        val sheds: Int,
        val weights: Int,
        val foodStock: Int
    )
}

/**
 * The one place an engine rejection reason becomes dialog copy. Every
 * [ImportFailure] class must appear here - a new rejection type stops this
 * `when` from compiling until it has its own distinct message.
 */
internal fun backupImportMessageFor(reason: ImportFailure): Int = when (reason) {
    ImportFailure.MalformedJson -> R.string.backup_import_failure_not_backup
    is ImportFailure.UnsupportedSchemaVersion -> R.string.backup_import_failure_newer_version
    is ImportFailure.OrphanChildRow -> R.string.backup_import_failure_orphan_rows
    is ImportFailure.InvalidRowId -> R.string.backup_import_failure_invalid_rows
}
