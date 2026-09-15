package com.snaketracker.app.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.snaketracker.app.data.backup.BackupData
import com.snaketracker.app.data.backup.BackupDocument
import com.snaketracker.app.data.backup.BackupJson
import com.snaketracker.app.data.backup.BackupRepository
import com.snaketracker.app.data.backup.ChildTable
import com.snaketracker.app.data.backup.FeedingRow
import com.snaketracker.app.data.backup.FoodStockRow
import com.snaketracker.app.data.backup.ImportFailure
import com.snaketracker.app.data.backup.ImportSummary
import com.snaketracker.app.data.backup.ShedRow
import com.snaketracker.app.data.backup.SnakeRow
import com.snaketracker.app.data.backup.WeightRow
import com.snaketracker.app.data.entities.FeedingEvent
import com.snaketracker.app.data.entities.FoodStockItem
import com.snaketracker.app.data.entities.ShedEvent
import com.snaketracker.app.data.entities.Snake
import com.snaketracker.app.data.entities.WeightEntry
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Import against a real (in-memory) Room database: a valid file replaces
 * everything with the file's exact rows and original ids, and every rejected
 * file leaves the device data untouched (issue #27 WB3/WB4).
 */
@RunWith(AndroidJUnit4::class)
class BackupRepositoryImportTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: BackupRepository

    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-04-01T10:15:30Z"), ZoneOffset.UTC)

    @Before
    fun createDatabase() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BackupRepository(db, appVersion = "1.1", clock = fixedClock)
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    private fun envelopeJson(data: BackupData, schemaVersion: Int = 1): String =
        BackupJson.encodeToString(
            BackupDocument.serializer(),
            BackupDocument(
                schemaVersion = schemaVersion,
                appVersion = "1.1",
                exportedAt = "2026-04-01T10:15:30Z",
                data = data
            )
        )

    // The device's own data: distinct ids so a missed wipe stays visible.
    private suspend fun seedDeviceData() {
        db.snakeDao().insert(Snake(id = 7, name = "Device Snake"))
        db.feedingDao().insert(FeedingEvent(id = 70, snakeId = 7, date = 100, foodType = "Rat"))
        db.shedDao().insert(ShedEvent(id = 71, snakeId = 7, date = 200))
        db.weightDao().insert(WeightEntry(id = 72, snakeId = 7, date = 300, grams = 500f))
        db.foodStockDao().insert(FoodStockItem(id = 90, name = "Device rats", foodType = "Rat"))
    }

    private suspend fun snapshotTables() = Quintuple(
        db.snakeDao().getAllOnce(),
        db.feedingDao().getAllOnce(),
        db.shedDao().getAllOnce(),
        db.weightDao().getAllOnce(),
        db.foodStockDao().getAllOnce()
    )

    private data class Quintuple(
        val snakes: List<Snake>,
        val feedings: List<FeedingEvent>,
        val sheds: List<ShedEvent>,
        val weights: List<WeightEntry>,
        val foodStock: List<FoodStockItem>
    )

    @Test
    fun importJson_validFile_deviceHoldsExactlyTheFilesRowsWithOriginalIds() = runBlocking {
        seedDeviceData()

        val fileData = BackupData(
            snakes = listOf(
                SnakeRow(1, "Noodle", "Python regius", "Banana", "Female", null, 1_700_000_000_000, "Rack 1", "Feedy", 9, false),
                SnakeRow(2, "Cobra", "", "", "Male", null, null, "", "", 7, true)
            ),
            feedings = listOf(
                FeedingRow(10, snakeId = 1, date = 1_750_000_000_000, foodType = "Mouse", foodSize = "Adult", accepted = true, assist = false, notes = "", foodStockItemId = 100),
                FeedingRow(11, snakeId = 2, date = 1_750_086_400_000, foodType = "Rat", foodSize = "", accepted = true, assist = false, notes = "", foodStockItemId = null)
            ),
            sheds = listOf(ShedRow(20, snakeId = 1, date = 1_750_100_000_000, complete = false, notes = "Partial")),
            weights = listOf(WeightRow(30, snakeId = 2, date = 1_750_120_000_000, grams = 900f, notes = "Fasting")),
            foodStock = listOf(FoodStockRow(100, "Frozen mice", "Mouse", "Pinky", 12, 3, ""))
        )

        val summary = repository.importJson(envelopeJson(fileData))

        assertEquals(ImportSummary.Success(snakes = 2, feedings = 2, sheds = 1, weights = 1, foodStock = 1), summary)

        // Device rows are gone; the file's rows stand with their original ids,
        // and the feeding -> snake and feeding -> food-stock references resolve.
        val tables = snapshotTables()
        assertEquals(listOf(1L, 2L), tables.snakes.map { it.id })
        assertEquals(listOf(10L, 11L), tables.feedings.map { it.id })
        assertEquals(listOf(20L), tables.sheds.map { it.id })
        assertEquals(listOf(30L), tables.weights.map { it.id })
        assertEquals(listOf(100L), tables.foodStock.map { it.id })

        val importedFeeding = tables.feedings.first { it.id == 10L }
        assertEquals(1L, importedFeeding.snakeId)
        assertEquals(100L, importedFeeding.foodStockItemId)
        assertEquals(
            Snake(1, "Noodle", "Python regius", "Banana", "Female", null, 1_700_000_000_000, "Rack 1", "Feedy", 9, false),
            tables.snakes.first { it.id == 1L }
        )
    }

    @Test
    fun importJson_danglingFoodStockItemId_nullsLinkAndStillImportsRow() = runBlocking {
        val fileData = BackupData(
            snakes = listOf(SnakeRow(1, "Noodle", "", "", "Unknown", null, null, "", "", 7, true)),
            feedings = listOf(
                FeedingRow(10, snakeId = 1, date = 100, foodType = "Mouse", foodSize = "", accepted = true, assist = false, notes = "", foodStockItemId = 555)
            )
        )

        val summary = repository.importJson(envelopeJson(fileData))

        assertEquals(ImportSummary.Success(snakes = 1, feedings = 1, sheds = 0, weights = 0, foodStock = 0), summary)
        val feeding = db.feedingDao().getAllOnce().single()
        assertEquals(10L, feeding.id)
        assertEquals(1L, feeding.snakeId)
        assertEquals(null, feeding.foodStockItemId)
    }

    @Test
    fun importJson_orphanSnakeId_rejectsFileAndLeavesEveryTableUnchanged() = runBlocking {
        seedDeviceData()
        val before = snapshotTables()

        val orphanFile = BackupData(
            snakes = listOf(SnakeRow(1, "Noodle", "", "", "Unknown", null, null, "", "", 7, true)),
            feedings = listOf(
                FeedingRow(10, snakeId = 99, date = 100, foodType = "Mouse", foodSize = "", accepted = true, assist = false, notes = "", foodStockItemId = null)
            )
        )

        val summary = repository.importJson(envelopeJson(orphanFile))

        assertEquals(
            ImportSummary.Failure(ImportFailure.OrphanChildRow(ChildTable.FEEDING, rowId = 10, snakeId = 99)),
            summary
        )
        assertEquals(before, snapshotTables())
    }

    @Test
    fun importJson_unsupportedSchemaVersion_rejectsFileAndLeavesEveryTableUnchanged() = runBlocking {
        seedDeviceData()
        val before = snapshotTables()

        val summary = repository.importJson(
            envelopeJson(
                BackupData(snakes = listOf(SnakeRow(1, "Noodle", "", "", "Unknown", null, null, "", "", 7, true))),
                schemaVersion = 2
            )
        )

        assertEquals(
            ImportSummary.Failure(ImportFailure.UnsupportedSchemaVersion(found = 2)),
            summary
        )
        assertEquals(before, snapshotTables())
    }

    @Test
    fun importJson_duplicateRowIds_failsMidTransactionAndRollsBackUnchanged() = runBlocking {
        // File passes validation (every snakeId resolves) but carries two
        // feedings sharing id 10: the second insert hits the PRIMARY KEY
        // constraint mid-transaction. Room must roll the whole replace back,
        // leaving the device data byte-for-byte unchanged (issue #27 WB4).
        seedDeviceData()
        val before = snapshotTables()

        val duplicateIdFile = BackupData(
            snakes = listOf(SnakeRow(1, "Noodle", "", "", "Unknown", null, null, "", "", 7, true)),
            feedings = listOf(
                FeedingRow(10, snakeId = 1, date = 100, foodType = "Mouse", foodSize = "", accepted = true, assist = false, notes = "", foodStockItemId = null),
                FeedingRow(10, snakeId = 1, date = 200, foodType = "Rat", foodSize = "", accepted = true, assist = false, notes = "", foodStockItemId = null)
            )
        )

        try {
            repository.importJson(envelopeJson(duplicateIdFile))
            fail("Expected a mid-insert SQLiteConstraintException")
        } catch (expected: SQLiteConstraintException) {
            // Expected: the failure happened mid-transaction; rollback below.
        }

        assertEquals(before, snapshotTables())
    }

    @Test
    fun importJson_importIntoEmptyDatabase_writesFileRows() = runBlocking {
        val fileData = BackupData(
            snakes = listOf(SnakeRow(5, "Solo", "", "", "Unknown", null, null, "", "", 7, true))
        )

        val summary = repository.importJson(envelopeJson(fileData))

        assertEquals(ImportSummary.Success(snakes = 1, feedings = 0, sheds = 0, weights = 0, foodStock = 0), summary)
        assertEquals(listOf(5L), db.snakeDao().getAllOnce().map { it.id })
    }
}
