package com.snaketracker.app.data.backup

/**
 * The whole-database backup document as one port: [exportAll] dumps every
 * table into the locked JSON format, [importJson] restores a document over
 * everything behind the validation gate. [BackupRepository] is the
 * production implementation; JVM tests substitute a fake, so the backup
 * dialog states are testable without Room.
 *
 * This single port replaces the separate `BackupJsonSource` and
 * `BackupJsonSink` cuts (PR #34 review 🔴): the only implementation
 * implemented both halves and the only consumer used both, so the split
 * earned nothing - export-only tests had to fake the sink just to build.
 */
interface BackupEngine {
    suspend fun exportAll(): String
    suspend fun importJson(json: String): ImportSummary
}
