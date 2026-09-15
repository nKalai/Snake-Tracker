package com.snaketracker.app.data.backup

/**
 * The JSON-restoring half of backup, as the ViewModel sees it: consumes one
 * backup document and replaces everything with it behind the existing
 * validation gate. [BackupRepository] is the production implementation; JVM
 * tests substitute a fake, so the import result state is testable without
 * Room.
 */
interface BackupJsonSink {
    suspend fun importJson(json: String): ImportSummary
}
