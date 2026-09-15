package com.snaketracker.app.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes a JSON backup document to a user-picked destination and reports the
 * name of the file that was actually saved (the picker UI lets the user
 * rename the suggested file, so the picked [Uri] is the only truth about the
 * name). Failures surface as exceptions - typically [IOException] - which
 * the caller turns into dialog copy.
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
            contentResolver.openOutputStream(destination)?.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("The chosen location could not be opened for writing.")

            savedFileName(destination)
        }

    // The display name the document provider reports for the written file;
    // providers are allowed to omit it, so fall back to the URI's last
    // segment and finally to a generic label.
    private fun savedFileName(uri: Uri): String {
        contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index)?.let { return it }
                }
            }
        return uri.lastPathSegment ?: FALLBACK_NAME
    }

    private companion object {
        const val FALLBACK_NAME = "backup.json"
    }
}
