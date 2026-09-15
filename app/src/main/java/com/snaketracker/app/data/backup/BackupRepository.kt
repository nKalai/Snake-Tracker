package com.snaketracker.app.data.backup

import androidx.room.withTransaction
import com.snaketracker.app.data.AppDatabase
import com.snaketracker.app.data.entities.FeedingEvent
import com.snaketracker.app.data.entities.FoodStockItem
import com.snaketracker.app.data.entities.ShedEvent
import com.snaketracker.app.data.entities.Snake
import com.snaketracker.app.data.entities.WeightEntry
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dumps the whole database into the JSON backup format defined by
 * [BackupDocument]. The wire format is known only here; DAOs and entities
 * stay format-agnostic and the app's [com.snaketracker.app.data.Repository]
 * CRUD surface is untouched.
 */
class BackupRepository(
    private val db: AppDatabase,
    private val appVersion: String,
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BackupJsonSource {

    /**
     * All five tables as one JSON backup document. The document build and
     * its serialization run entirely on [ioDispatcher]: the five reads
     * already leave the caller's thread inside [androidx.room.withTransaction],
     * but the encode - the costliest step, growing with the user's data -
     * must not run in the caller's context either (issue #26 WB2).
     */
    override suspend fun exportAll(): String = withContext(ioDispatcher) {
        BackupJson.encodeToString(BackupDocument.serializer(), backupDocument())
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
