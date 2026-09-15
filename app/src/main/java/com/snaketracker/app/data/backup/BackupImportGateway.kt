package com.snaketracker.app.data.backup

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads a user-picked backup file as the JSON text the import engine
 * consumes. Failures surface as exceptions - typically [IOException] -
 * which the caller turns into dialog copy.
 */
interface BackupImportGateway {
    suspend fun read(source: Uri): String
}

/**
 * Storage Access Framework implementation: the source URI returned by
 * `ACTION_OPEN_DOCUMENT` is read through [ContentResolver] entirely on
 * [Dispatchers.IO] - no runtime permissions and no manifest storage
 * declarations are involved.
 */
class BackupImportContentGateway(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BackupImportGateway {

    override suspend fun read(source: Uri): String =
        withContext(ioDispatcher) {
            contentResolver.openInputStream(source)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: throw IOException("The chosen file could not be opened for reading.")
        }
}
