package com.snaketracker.app.data.backup

import java.io.IOException

/**
 * Machine-readable reason for a failed backup export. The UI maps each
 * reason to its own string resource, so user-facing copy never originates
 * in the data layer (issue #26 WB3).
 */
enum class BackupExportFailureReason {
    /** The picked destination could not be opened for writing at all. */
    DESTINATION_UNOPENABLE,

    /** Anything unexpected; the original exception is logged, not shown. */
    UNKNOWN
}

/**
 * Typed failure of [BackupExportGateway.save]. The [message] is diagnostic
 * text for the log - never shown to the user; the dialog copy is derived
 * from [reason] in the UI layer. Extends [IOException] because every typed
 * export failure is a storage failure.
 */
class BackupExportException(
    val reason: BackupExportFailureReason,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)
