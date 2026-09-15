package com.snaketracker.app.data.backup

/**
 * The JSON-dumping half of backup, as the ViewModel sees it: a source that
 * yields the whole database as one backup document. [BackupRepository] is
 * the production implementation; JVM tests substitute a fake, so the export
 * result state is testable without Room.
 */
interface BackupJsonSource {
    suspend fun exportAll(): String
}
