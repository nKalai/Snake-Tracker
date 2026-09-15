package com.snaketracker.app.data.backup

import androidx.room.withTransaction
import com.snaketracker.app.data.AppDatabase
import com.snaketracker.app.data.entities.FeedingEvent
import com.snaketracker.app.data.entities.FoodStockItem
import com.snaketracker.app.data.entities.ShedEvent
import com.snaketracker.app.data.entities.Snake
import com.snaketracker.app.data.entities.WeightEntry
import java.time.Clock

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

    /**
     * Restores a full backup file produced by [exportAll], replacing
     * everything: on success the five tables hold exactly the file's rows,
     * with original ids preserved. Every rejection (see [inspectBackup]) is
     * all-or-nothing — the database stays untouched. A mid-insert SQL error
     * (e.g. duplicate ids inside the file) propagates after Room rolls the
     * transaction back, so the existing data survives byte-for-byte.
     */
    suspend fun importJson(json: String): ImportSummary {
        val data = when (val inspection = inspectBackup(json)) {
            is ImportInspection.Rejected -> return ImportSummary.Failure(inspection.reason)
            is ImportInspection.Accepted -> inspection.document.data
        }
        val resolved = resolveStockLinks(data)
        return db.withTransaction {
            // Children before parents keeps deletes independent of FK
            // cascade; snakes before children satisfies the FKs on insert.
            db.feedingDao().deleteAll()
            db.shedDao().deleteAll()
            db.weightDao().deleteAll()
            db.snakeDao().deleteAll()
            db.foodStockDao().deleteAll()
            resolved.snakes.forEach { db.snakeDao().insert(it.toEntity()) }
            resolved.foodStock.forEach { db.foodStockDao().insert(it.toEntity()) }
            resolved.feedings.forEach { db.feedingDao().insert(it.toEntity()) }
            resolved.sheds.forEach { db.shedDao().insert(it.toEntity()) }
            resolved.weights.forEach { db.weightDao().insert(it.toEntity()) }
            importSummaryOf(resolved)
        }
    }

    // The five reads run inside one Room transaction, so the document is a
    // consistent snapshot even if the tables are being written elsewhere.
    private suspend fun backupDocument(): BackupDocument = db.withTransaction {
        BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION,
            appVersion = appVersion,
            exportedAt = formatExportedAt(clock.instant()),
            data = backupData(
                snakes = db.snakeDao().getAllOnce(),
                feedings = db.feedingDao().getAllOnce(),
                sheds = db.shedDao().getAllOnce(),
                weights = db.weightDao().getAllOnce(),
                foodStock = db.foodStockDao().getAllOnce()
            )
        )
    }
}

// Pure pairing of the five entity tables with their row arrays, so the
// document assembly stays JVM-testable without a Room database (mirrors
// com.snaketracker.app.data.buildReminderCandidates). A sixth table has to
// pass through here, where a missing mapping fails a unit test.
internal fun backupData(
    snakes: List<Snake>,
    feedings: List<FeedingEvent>,
    sheds: List<ShedEvent>,
    weights: List<WeightEntry>,
    foodStock: List<FoodStockItem>
): BackupData = BackupData(
    snakes = snakes.map { it.toRow() },
    feedings = feedings.map { it.toRow() },
    sheds = sheds.map { it.toRow() },
    weights = weights.map { it.toRow() },
    foodStock = foodStock.map { it.toRow() }
)
