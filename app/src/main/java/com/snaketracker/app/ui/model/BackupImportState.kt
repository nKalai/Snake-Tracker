package com.snaketracker.app.ui.model

import androidx.annotation.StringRes
import com.snaketracker.app.R
import com.snaketracker.app.data.backup.ImportFailure
import com.snaketracker.app.data.backup.TableCount

/**
 * Outcome of a user-initiated backup import, rendered by the Settings screen
 * result dialog. [Success] carries the engine's per-table counts verbatim —
 * one [TableCount] list from the engine to the dialog, no field-by-field
 * mirrors (PR #34 review 🟡); [Failure] carries the message resource that
 * names the reason (issue #28 WB4).
 */
sealed interface BackupImportState {
    data class Success(val counts: List<TableCount>) : BackupImportState
    data class Failure(@StringRes val messageRes: Int) : BackupImportState
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
