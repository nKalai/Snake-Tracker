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
import com.snaketracker.app.data.backup.BackupTable
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

    // Fixture builders: sane defaults inside, overrides via named args
    // (the convention in BackupRepositoryExportTest / BackupDataTest).

    private fun snakeRow(
        id: Long = 1,
        name: String = "Noodle",
        species: String = "",
        morph: String = "",
        sex: String = "Unknown",
        acquisitionDate: Long? = null,
        enclosure: String = "",
        notes: String = "",
        feedingIntervalDays: Int = 7,
        remindersEnabled: Boolean = true
    ): SnakeRow = SnakeRow(
        id = id,
        name = name,
        species = species,
        morph = morph,
        sex = sex,
        birthDate = null,
        acquisitionDate = acquisitionDate,
        enclosure = enclosure,
        notes = notes,
        feedingIntervalDays = feedingIntervalDays,
        remindersEnabled = remindersEnabled
    )

    private fun feedingRow(
        id: Long = 10,
        snakeId: Long = 1,
        date: Long = 100,
        foodType: String = "Mouse",
        foodSize: String = "",
        accepted: Boolean = true,
        notes: String = "",
        foodStockItemId: Long? = null
    ): FeedingRow = FeedingRow(
        id = id,
        snakeId = snakeId,
        date = date,
        foodType = foodType,
        foodSize = foodSize,
        accepted = accepted,
        assist = false,
        notes = notes,
        foodStockItemId = foodStockItemId
    )

    private fun shedRow(
        id: Long = 20,
        snakeId: Long = 1,
        date: Long = 100,
        complete: Boolean = true,
        notes: String = ""
    ): ShedRow = ShedRow(id = id, snakeId = snakeId, date = date, complete = complete, notes = notes)

    private fun weightRow(
        id: Long = 30,
        snakeId: Long = 1,
        date: Long = 100,
        grams: Float = 10f,
        notes: String = ""
    ): WeightRow = WeightRow(id = id, snakeId = snakeId, date = date, grams = grams, notes = notes)

    private fun stockRow(
        id: Long = 100,
        name: String = "Mice",
        foodType: String = "Mouse",
        size: String = "",
        quantity: Int = 5,
        lowStockThreshold: Int = 2
    ): FoodStockRow = FoodStockRow(
        id = id,
        name = name,
        foodType = foodType,
        size = size,
        quantity = quantity,
        lowStockThreshold = lowStockThreshold,
        notes = ""
    )

    private fun fileData(
        snakes: List<SnakeRow> = emptyList(),
        feedings: List<FeedingRow> = emptyList(),
        sheds: List<ShedRow> = emptyList(),
        weights: List<WeightRow> = emptyList(),
        foodStock: List<FoodStockRow> = emptyList()
    ): BackupData = BackupData(
        snakes = snakes,
        feedings = feedings,
        sheds = sheds,
        weights = weights,
        foodStock = foodStock
    )

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

        val fileData = fileData(
            snakes = listOf(
                snakeRow(id = 1, name = "Noodle", species = "Python regius", morph = "Banana", sex = "Female", acquisitionDate = 1_700_000_000_000, enclosure = "Rack 1", notes = "Feedy", feedingIntervalDays = 9, remindersEnabled = false),
                snakeRow(id = 2, name = "Cobra", sex = "Male")
            ),
            feedings = listOf(
                feedingRow(id = 10, snakeId = 1, date = 1_750_000_000_000, foodSize = "Adult", foodStockItemId = 100),
                feedingRow(id = 11, snakeId = 2, date = 1_750_086_400_000, foodType = "Rat")
            ),
            sheds = listOf(shedRow(id = 20, snakeId = 1, date = 1_750_100_000_000, complete = false, notes = "Partial")),
            weights = listOf(weightRow(id = 30, snakeId = 2, date = 1_750_120_000_000, grams = 900f, notes = "Fasting")),
            foodStock = listOf(stockRow(id = 100, name = "Frozen mice", size = "Pinky", quantity = 12, lowStockThreshold = 3))
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
    fun importJson_validEmptyFile_wipesAllDeviceData() = runBlocking {
        // "Replaces everything" must hold for the least-export-looking valid
        // file too: five empty lists wipe the device completely.
        seedDeviceData()

        val summary = repository.importJson(envelopeJson(fileData()))

        assertEquals(ImportSummary.Success(snakes = 0, feedings = 0, sheds = 0, weights = 0, foodStock = 0), summary)
        val tables = snapshotTables()
        assertEquals(emptyList<Snake>(), tables.snakes)
        assertEquals(emptyList<FeedingEvent>(), tables.feedings)
        assertEquals(emptyList<ShedEvent>(), tables.sheds)
        assertEquals(emptyList<WeightEntry>(), tables.weights)
        assertEquals(emptyList<FoodStockItem>(), tables.foodStock)
    }

    @Test
    fun importJson_danglingFoodStockItemId_nullsLinkAndStillImportsRow() = runBlocking {
        val fileData = fileData(
            snakes = listOf(snakeRow()),
            feedings = listOf(feedingRow(foodStockItemId = 555))
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

        val orphanFile = fileData(
            snakes = listOf(snakeRow()),
            feedings = listOf(feedingRow(snakeId = 99))
        )

        val summary = repository.importJson(envelopeJson(orphanFile))

        assertEquals(
            ImportSummary.Failure(ImportFailure.OrphanChildRow(BackupTable.FEEDING, rowId = 10, snakeId = 99)),
            summary
        )
        assertEquals(before, snapshotTables())
    }

    @Test
    fun importJson_nonPositiveRowId_isRejectedBeforeAnyWrite() = runBlocking {
        // A file whose snake row carries Room's "new row" sentinel id 0 must
        // fail typed, before the transaction: never a renumbered insert.
        seedDeviceData()
        val before = snapshotTables()

        val summary = repository.importJson(envelopeJson(fileData(snakes = listOf(snakeRow(id = 0)))))

        assertEquals(
            ImportSummary.Failure(ImportFailure.InvalidRowId(BackupTable.SNAKE, rowId = 0)),
            summary
        )
        assertEquals(before, snapshotTables())
    }

    @Test
    fun importJson_unsupportedSchemaVersion_rejectsFileAndLeavesEveryTableUnchanged() = runBlocking {
        seedDeviceData()
        val before = snapshotTables()

        val summary = repository.importJson(
            envelopeJson(fileData(snakes = listOf(snakeRow())), schemaVersion = 2)
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
        // The exception propagates untyped by contract — see the importJson
        // KDoc in BackupRepository.
        seedDeviceData()
        val before = snapshotTables()

        val duplicateIdFile = fileData(
            snakes = listOf(snakeRow()),
            feedings = listOf(
                feedingRow(id = 10, date = 100),
                feedingRow(id = 10, date = 200, foodType = "Rat")
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
        val summary = repository.importJson(envelopeJson(fileData(snakes = listOf(snakeRow(id = 5, name = "Solo")))))

        assertEquals(ImportSummary.Success(snakes = 1, feedings = 0, sheds = 0, weights = 0, foodStock = 0), summary)
        assertEquals(listOf(5L), db.snakeDao().getAllOnce().map { it.id })
    }
}
