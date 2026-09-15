package com.snaketracker.app.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.OutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes a JSON backup document to a user-picked destination and reports the
 * name of the file that was actually saved (the picker UI lets the user
 * rename the suggested file, so the picked [Uri] is the only truth about the
 * name). Failures surface as [BackupExportException] with a machine-readable
 * [BackupExportFailureReason]; the caller maps that to dialog copy, never to
 * the exception's own text.
 */
interface BackupExportGateway {
    suspend fun save(destination: Uri, json: String): String
}

/**
 * Storage Access Framework implementation: the destination URI returned by
 * `ACTION_CREATE_DOCUMENT` is written through [ContentResolver] entirely on
 * [Dispatchers.IO] - no runtime permissions and no manifest storage
 * declarations are involved.
 */
class BackupExportContentGateway(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BackupExportGateway {

    override suspend fun save(destination: Uri, json: String): String =
        withContext(ioDispatcher) {
            writeJson(destination, json)

            savedFileName(destination)
        }

    // Opening the destination already truncates it (mode "w"), so every
    // throw from the write or the close - disk full, provider death - means
    // the user's file is now empty or partial. Those get their own typed
    // reason so the dialog can warn about the possibly-incomplete file
    // instead of reporting a generic "unexpected error" (PR #34 review 🔴).
    private fun writeJson(destination: Uri, json: String) {
        val stream = openDestinationStream(destination)
        try {
            stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        } catch (e: Exception) {
            throw BackupExportException(
                BackupExportFailureReason.DESTINATION_UNWRITABLE,
                "writing backup to $destination failed",
                e
            )
        }
    }

    // A destination that cannot be opened - null stream, refused grant,
    // unreadable target - is one typed failure: the reason reaches the
    // dialog, the provider's raw message only reaches the log.
    private fun openDestinationStream(destination: Uri): OutputStream =
        try {
            contentResolver.openOutputStream(destination)
                ?: throw BackupExportException(
                    BackupExportFailureReason.DESTINATION_UNOPENABLE,
                    "openOutputStream returned null for $destination"
                )
        } catch (e: BackupExportException) {
            throw e
        } catch (e: Exception) {
            throw BackupExportException(
                BackupExportFailureReason.DESTINATION_UNOPENABLE,
                "openOutputStream failed for $destination",
                e
            )
        }

    // The display name the document provider reports for the written file.
    // This runs after the bytes are already saved, so nothing here may fail
    // the export: providers are allowed to omit the name, refuse the
    // projection query, or throw on a write-only grant - every one of those
    // falls back to the URI's last segment and finally to a generic label.
    private fun savedFileName(uri: Uri): String {
        val displayName = try {
            contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        } catch (e: Exception) {
            null
        }
        return displayName ?: uri.lastPathSegment ?: FALLBACK_NAME
    }

    private companion object {
        const val FALLBACK_NAME = "backup.json"
    }
}
