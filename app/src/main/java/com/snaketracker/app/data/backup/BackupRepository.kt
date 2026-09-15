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
) : BackupJsonSink, BackupJsonSource {

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

    /**
     * Restores a full backup file produced by [exportAll], replacing
     * everything: on success the five tables hold exactly the file's rows,
     * with original ids preserved. Every rejection (see [inspectBackup])
     * arrives as an [ImportSummary.Failure] and is all-or-nothing — the
     * database stays untouched.
     *
     * Contract for database-level errors: if a validated file still fails
     * mid-insert (duplicate row ids, disk failure, `SQLITE_BUSY`
     * contention), the SQL exception propagates out of this call *after*
     * Room rolls the transaction back. Data safety is kept, but such an
     * error is deliberately not part of the typed [ImportSummary] surface
     * (it is not a locked rejection rule) — callers, including the #28
     * import UI, must try/catch it if they want to name it to the user.
     *
     * Handoff to #28: after a successful import the armed reminder alarm
     * still reflects the pre-import plan, because [com.snaketracker.app.
     * reminders.ReminderArming] refreshes only from its receivers. The
     * import UI triggers a reminder re-arm on [ImportSummary.Success]
     * (issue #28 WB5) so reminder-enabled snakes coming from the file get
     * their alarms without waiting for the next app launch.
     */
    override suspend fun importJson(json: String): ImportSummary {
        val data = when (val inspection = inspectBackup(json)) {
            is ImportInspection.Rejected -> return ImportSummary.Failure(inspection.reason)
            is ImportInspection.Accepted -> inspection.document.data
        }
        return db.withTransaction {
            // The five tables and their order are stated exactly once, in
            // insert order: snakes before their FK children. The wipe
            // reuses the same list reversed, so children clear before
            // snakes regardless of FK cascade, and a sixth table is added
            // in one place.
            val tables = listOf(
                TableBridge(wipe = { db.snakeDao().deleteAll() }) {
                    data.snakes.forEach { db.snakeDao().insert(it.toEntity()) }
                },
                TableBridge(wipe = { db.foodStockDao().deleteAll() }) {
                    data.foodStock.forEach { db.foodStockDao().insert(it.toEntity()) }
                },
                TableBridge(wipe = { db.feedingDao().deleteAll() }) {
                    data.feedings.forEach { db.feedingDao().insert(it.toEntity()) }
                },
                TableBridge(wipe = { db.shedDao().deleteAll() }) {
                    data.sheds.forEach { db.shedDao().insert(it.toEntity()) }
                },
                TableBridge(wipe = { db.weightDao().deleteAll() }) {
                    data.weights.forEach { db.weightDao().insert(it.toEntity()) }
                }
            )
            tables.asReversed().forEach { it.wipe() }
            tables.forEach { it.restore() }
            importSummaryOf(data)
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

// One table's replace-everything pair. Import order is the list order in
// importJson; the wipe runs the same list reversed.
private class TableBridge(
    val wipe: suspend () -> Unit,
    val restore: suspend () -> Unit
)

// Pure pairing of the five entity tables with their row arrays, so the
// document assembly stays JVM-testable without a Room database (mirrors
// com.snaketracker.app.data.buildReminderCandidates). BackupData has no
// list defaults, so a sixth table breaks this named-arg call at compile
// time and the mapping tests, never a silent empty list.
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
