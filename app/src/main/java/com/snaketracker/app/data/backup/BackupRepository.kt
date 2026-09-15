package com.snaketracker.app.data.backup

import androidx.room.withTransaction
import com.snaketracker.app.data.AppDatabase
import java.time.Clock
import java.time.Instant
import kotlinx.serialization.json.Json

/**
 * Dumps the whole database into the JSON backup format defined by
 * [BackupDocument]. The wire format is known only here; DAOs and entities
 * stay format-agnostic and the app's [com.snaketracker.app.data.Repository]
 * CRUD surface is untouched.
 */
class BackupRepository(
    private val db: AppDatabase,
    private val appVersion: String,
    private val clock: Clock = Clock.systemUTC()
) {

    /** All five tables as one JSON backup document. */
    suspend fun exportAll(): String =
        BackupJson.encodeToString(BackupDocument.serializer(), backupDocument())

    // The five reads run inside one Room transaction, so the document is a
    // consistent snapshot even if the tables are being written elsewhere.
    private suspend fun backupDocument(): BackupDocument = db.withTransaction {
        BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION,
            appVersion = appVersion,
            exportedAt = Instant.now(clock).toString(),
            data = BackupData(
                snakes = db.snakeDao().getAllOnce().map { it.toRow() },
                feedings = db.feedingDao().getAllOnce().map { it.toRow() },
                sheds = db.shedDao().getAllOnce().map { it.toRow() },
                weights = db.weightDao().getAllOnce().map { it.toRow() },
                foodStock = db.foodStockDao().getAllOnce().map { it.toRow() }
            )
        )
    }

    companion object {
        @Volatile private var INSTANCE: BackupRepository? = null

        fun getInstance(db: AppDatabase, appVersion: String): BackupRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: BackupRepository(db, appVersion).also { INSTANCE = it }
            }
    }
}

/** Shared encoder for the backup wire format: every field is written, even defaults. */
internal val BackupJson = Json { encodeDefaults = true }
