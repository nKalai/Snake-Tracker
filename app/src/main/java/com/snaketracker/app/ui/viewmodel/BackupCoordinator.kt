package com.snaketracker.app.ui.viewmodel

import android.net.Uri
import android.util.Log
import com.snaketracker.app.R
import com.snaketracker.app.data.backup.BackupEngine
import com.snaketracker.app.data.backup.BackupExportException
import com.snaketracker.app.data.backup.BackupExportFailureReason
import com.snaketracker.app.data.backup.BackupExportGateway
import com.snaketracker.app.data.backup.BackupImportGateway
import com.snaketracker.app.data.backup.BackupImportRejectException
import com.snaketracker.app.data.backup.ImportSummary
import com.snaketracker.app.ui.model.BackupExportState
import com.snaketracker.app.ui.model.BackupImportState
import com.snaketracker.app.ui.model.backupImportMessageFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The one backup collaborator behind the ViewModel (PR #34 review 🔴): it
 * runs a whole export or import and returns the typed dialog state the
 * Settings screen renders. Where the ViewModel once carried four backup
 * ports plus a re-arm lambda - clumped through ViewModel, Factory and
 * Activity - it now carries just this.
 */
interface BackupCoordinator {
    /** Runs one export to [destination] and reports the dialog outcome. */
    suspend fun exportBackup(destination: Uri): BackupExportState

    /**
     * Runs one confirmed import of [source] and reports the dialog outcome;
     * a success re-arms the feeding reminder before returning, so callers
     * observing the result know the alarm already follows the new data.
     */
    suspend fun importBackup(source: Uri): BackupImportState
}

/**
 * Production [BackupCoordinator] over the [BackupEngine] and the two file
 * gateways. Every half runs off the caller's thread: the engine restores on
 * [ioDispatcher] (issue #28 WB3 - a large backup must not freeze Settings)
 * and the reminder re-arm runs on [rearmDispatcher], keeping the scheduling
 * policy inside the feature instead of the composition root (PR #34
 * review 🟢).
 *
 * Every outcome is a returned dialog state - failures included. The only
 * exception that leaves these methods is [CancellationException].
 */
class DefaultBackupCoordinator(
    private val backupEngine: BackupEngine,
    private val exportGateway: BackupExportGateway,
    private val importGateway: BackupImportGateway,
    private val rearmReminders: suspend () -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val rearmDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /**
     * The module's logger boundary: production reaches [Log.e], JVM tests
     * pass a recorder. It exists so the module-wide `returnDefaultValues`
     * build flag has a named owner for backup's logging (PR #34 review 🟡).
     */
    private val logFailure: (String, Throwable) -> Unit = { message, throwable ->
        Log.e(LOG_TAG, message, throwable)
    }
) : BackupCoordinator {

    override suspend fun exportBackup(destination: Uri): BackupExportState =
        recoveringFrom(
            logMessage = "Backup export failed",
            onFailure = { e ->
                // The original exception (and its provider/database detail)
                // goes to the log; the user gets a string-resource reason.
                BackupExportState.Failure(
                    (e as? BackupExportException)?.reason
                        ?: BackupExportFailureReason.UNKNOWN
                )
            }
        ) {
            BackupExportState.Success(exportGateway.save(destination, backupEngine.exportAll()))
        }

    /**
     * Reads the picked file through the gateway, hands the bytes to the
     * engine - which fully parses the file before Room dispatches its
     * transaction - and reports the outcome (issue #28 WB3/WB4). A success
     * re-arms the feeding reminder through the shared reschedule-only entry
     * point, so the alarm follows the new data without waiting for the next
     * app launch (issue #28 WB5).
     */
    override suspend fun importBackup(source: Uri): BackupImportState {
        val state = recoveringFrom(
            // Only a failed read can stop the engine from running, so the
            // engine's own failure mapping stays nested inside this read gate.
            logMessage = "Backup import could not read the picked file",
            onFailure = { e ->
                BackupImportState.Failure(
                    if (e is BackupImportRejectException) {
                        R.string.backup_import_failure_too_large
                    } else {
                        R.string.backup_import_failure_unreadable
                    }
                )
            }
        ) {
            val json = importGateway.read(source)
            recoveringFrom(
                // A validated file that still fails mid-insert arrives as an
                // exception, not a typed rejection (see BackupRepository.importJson);
                // Room has already rolled the transaction back.
                logMessage = "Backup import failed while restoring",
                onFailure = { BackupImportState.Failure(R.string.backup_import_failure_database) }
            ) {
                when (val summary = withContext(ioDispatcher) { backupEngine.importJson(json) }) {
                    // The engine's counts travel to the dialog unchanged:
                    // one carrier, no per-field re-copy (PR #34 review 🟡).
                    is ImportSummary.Success -> BackupImportState.Success(summary.counts)
                    is ImportSummary.Failure ->
                        BackupImportState.Failure(backupImportMessageFor(summary.reason))
                }
            }
        }
        if (state is BackupImportState.Success) rearmAfterImport()
        return state
    }

    private suspend fun rearmAfterImport() {
        recoveringFrom(
            // The import itself completed; the receivers (alarm fire, boot,
            // permission re-grant) re-arm on their next event regardless.
            logMessage = "Reminder re-arm after import failed",
            onFailure = { }
        ) {
            withContext(rearmDispatcher) { rearmReminders() }
        }
    }

    /**
     * Runs [block], logging any failure through the [logFailure] seam and
     * recovering through [onFailure] — the one cancellation-safe catch
     * idiom behind every backup dialog path: a [CancellationException]
     * (scope death, navigation away) always propagates instead of being
     * reported as a backup outcome.
     *
     * Everything else — [Exception]s and [Error]s alike (an oversized file
     * read can raise [OutOfMemoryError]) — reaches the dialog, honoring the
     * PRD's "every outcome gets a dialog" criterion (PR #34 review 🟡): at
     * this last UI boundary, reporting beats crashing, and the stack still
     * goes to the log through [logFailure].
     */
    private inline fun <T> recoveringFrom(
        logMessage: String,
        onFailure: (Throwable) -> T,
        block: () -> T
    ): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logFailure(logMessage, e)
            onFailure(e)
        }

    private companion object {
        const val LOG_TAG = "BackupCoordinator"
    }
}
