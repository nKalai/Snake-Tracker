package com.snaketracker.app.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Machine-readable reason for refusing a picked file before reading it. */
enum class BackupImportRejectReason {
    /** The provider's reported size exceeds [BackupImportContentGateway.MAX_IMPORT_BYTES]. */
    FILE_TOO_LARGE
}

/**
 * Typed refusal of a picked import source, thrown before any bytes are
 * read. Like the export side, the [message] is log-only diagnostic text;
 * the dialog copy comes from a string resource mapped from [reason].
 * Extends [IOException] because every typed import-source failure is a
 * storage failure.
 */
class BackupImportRejectException(
    val reason: BackupImportRejectReason,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

/**
 * Reads a user-picked backup file as the JSON text the import engine
 * consumes. Failures surface as exceptions - [BackupImportRejectException]
 * for a typed refusal, [IOException] otherwise - which the caller turns
 * into dialog copy.
 */
interface BackupImportGateway {
    suspend fun read(source: Uri): String
}

/**
 * Storage Access Framework implementation: the source URI returned by
 * `ACTION_OPEN_DOCUMENT` is read through [ContentResolver] entirely on
 * [Dispatchers.IO] - no runtime permissions and no manifest storage
 * declarations are involved.
 *
 * A user-chosen file is untrusted input of unknown size, so the source is
 * sized before reading: `readBytes()` on a multi-GB pick would raise an
 * [OutOfMemoryError] over a whole heap's worth of allocation (PR #34
 * review 🟡). Providers are allowed to omit the size column or refuse the
 * projection - those stay openable and succeed or fail on their bytes.
 */
class BackupImportContentGateway(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BackupImportGateway {

    override suspend fun read(source: Uri): String = withContext(ioDispatcher) {
        rejectOversized(source)
        contentResolver.openInputStream(source)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        } ?: throw IOException("The chosen file could not be opened for reading.")
    }

    // The reported size bounds the read; a source whose SIZE exceeds the
    // bound is refused as a typed failure before a single byte is read.
    private fun rejectOversized(source: Uri) {
        val reportedSize = try {
            contentResolver
                .query(source, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                        cursor.getLong(index)
                    } else {
                        null
                    }
                }
        } catch (e: Exception) {
            null
        }
        if (reportedSize != null && reportedSize > MAX_IMPORT_BYTES) {
            throw BackupImportRejectException(
                BackupImportRejectReason.FILE_TOO_LARGE,
                "$source reports size $reportedSize above the $MAX_IMPORT_BYTES backup bound"
            )
        }
    }

    companion object {
        /**
         * The upper bound for an importable file: a generous multiple of
         * any plausible whole-collection backup (text-only JSON of five
         * small tables), low enough that reading within it cannot threaten
         * the heap.
         */
        const val MAX_IMPORT_BYTES = 32L * 1024 * 1024
    }
}
