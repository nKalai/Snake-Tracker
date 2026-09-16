package com.snaketracker.app.ui.model

import com.snaketracker.app.R
import com.snaketracker.app.data.backup.BackupExportFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The export twin of the import mapping test: every
 * [BackupExportFailureReason] has its own resolvable message resource, and
 * the mapping is the single place that decides it (the dialog just calls
 * [backupExportMessageFor], so a new reason cannot skip this step without
 * stopping compilation).
 */
class BackupExportMessageForTest {

    @Test
    fun everyExportReasonMapsToItsOwnResolvableMessageResource() {
        val mapped = BackupExportFailureReason.entries.map { backupExportMessageFor(it) }

        for (id in mapped) {
            assertNotEquals("resource id did not resolve", 0, id)
        }
        assertEquals(
            "every export reason must carry its own message",
            BackupExportFailureReason.entries.size,
            mapped.distinct().size
        )
    }

    @Test
    fun destinationUnopenableMapsToTheLocationMessage() {
        assertEquals(
            R.string.backup_export_failure_reason_destination,
            backupExportMessageFor(BackupExportFailureReason.DESTINATION_UNOPENABLE)
        )
    }

    /** The write-phase reason gets its own copy: the file may be incomplete. */
    @Test
    fun destinationUnwritableMapsToTheIncompleteFileMessage() {
        assertEquals(
            R.string.backup_export_failure_reason_unwritable,
            backupExportMessageFor(BackupExportFailureReason.DESTINATION_UNWRITABLE)
        )
    }
}
