package com.snaketracker.app.ui.model

import com.snaketracker.app.R
import com.snaketracker.app.data.backup.BackupTable
import com.snaketracker.app.data.backup.ImportFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The import-side mapping test beside [backupImportMessageFor]: every
 * rejection class becomes its own message resource, and the resource ids
 * really resolve. Pairwise distinctness of the resolved *copy* is asserted
 * on the device (`SettingsBackupImportTest`), where strings exist.
 */
class BackupImportMessageForTest {

    @Test
    fun `each engine rejection maps to its own message resource`() {
        val rejections = listOf(
            ImportFailure.MalformedJson to R.string.backup_import_failure_not_backup,
            ImportFailure.UnsupportedSchemaVersion(found = 2) to R.string.backup_import_failure_newer_version,
            ImportFailure.OrphanChildRow(BackupTable.FEEDING, rowId = 7, snakeId = 99) to
                R.string.backup_import_failure_orphan_rows,
            ImportFailure.InvalidRowId(BackupTable.SNAKE, rowId = 0) to
                R.string.backup_import_failure_invalid_rows
        )
        for ((reason, expected) in rejections) {
            assertEquals(expected, backupImportMessageFor(reason))
        }
    }

    @Test
    fun `every import failure message resource resolves and stays distinct`() {
        val ids = listOf(
            R.string.backup_import_failure_not_backup,
            R.string.backup_import_failure_newer_version,
            R.string.backup_import_failure_orphan_rows,
            R.string.backup_import_failure_invalid_rows,
            R.string.backup_import_failure_unreadable,
            R.string.backup_import_failure_too_large,
            R.string.backup_import_failure_database
        )
        for (id in ids) {
            assertNotEquals("resource id did not resolve", 0, id)
        }
        assertEquals(7, ids.distinct().size)
    }
}
