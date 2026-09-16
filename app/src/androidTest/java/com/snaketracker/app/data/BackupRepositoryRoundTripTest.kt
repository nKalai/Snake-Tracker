package com.snaketracker.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.snaketracker.app.data.backup.BackupRepository
import com.snaketracker.app.data.backup.ImportSummary
import com.snaketracker.app.data.backup.TableCount
import com.snaketracker.app.data.backup.BackupTable
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The export -> wipe -> import round trip on a real Room database
 * (PR #34 review, suggested edge-case test 1): the JVM DTO round trips
 * never cross the whole-document encode/decode and the engine's
 * transaction against the same database. A restore of one's own export
 * must return the device to exactly the rows it had, ids included.
 */
@RunWith(AndroidJUnit4::class)
class BackupRepositoryRoundTripTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: BackupRepository

    @Before
    fun createDatabase() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BackupRepository(
            db,
            appVersion = "1.1",
            clock = Clock.fixed(Instant.parse("2026-04-01T10:15:30Z"), ZoneOffset.UTC)
        )
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    /** Rows chosen to survive nothing-by-luck: non-ASCII, nulls, links, defaults. */
    private suspend fun seed() {
        db.snakeDao().insert(
            Snake(
                id = 5,
                name = "Café 🐍",
                species = "Python regius",
                morph = "Banana",
                sex = "Female",
                birthDate = null,
                acquisitionDate = 1_700_000_000_000,
                enclosure = "Rack 1",
                notes = "Feedy\nmultiline",
                feedingIntervalDays = 9,
                remindersEnabled = false
            )
        )
        db.snakeDao().insert(Snake(id = 8, name = "Noodle", remindersEnabled = true, feedingIntervalDays = 7))
        db.foodStockDao().insert(FoodStockItem(id = 11, name = "Mice", foodType = "Mouse", quantity = 3))
        db.feedingDao().insert(
            FeedingEvent(id = 21, snakeId = 5, date = 1_750_000_000_000, foodType = "Mouse", foodStockItemId = 11, assist = true)
        )
        db.feedingDao().insert(FeedingEvent(id = 22, snakeId = 8, date = 1_750_086_400_000, foodType = "Rat", accepted = false))
        db.shedDao().insert(ShedEvent(id = 31, snakeId = 5, date = 1_750_100_000_000, complete = false, notes = "Partial"))
        db.weightDao().insert(WeightEntry(id = 41, snakeId = 8, date = 1_750_120_000_000, grams = 900.5f))
    }

    private suspend fun snapshot() = listOf(
        db.snakeDao().getAllOnce(),
        db.feedingDao().getAllOnce(),
        db.shedDao().getAllOnce(),
        db.weightDao().getAllOnce(),
        db.foodStockDao().getAllOnce()
    )

    @Test
    fun exportWipeImport_restoresEveryTableRowWithOriginalIds() = runBlocking {
        seed()
        val before = snapshot()

        val exported = repository.exportAll()

        // Wipe the five tables (children before snakes, the engine's own
        // wipe order), then restore the export through the engine.
        db.feedingDao().deleteAll()
        db.shedDao().deleteAll()
        db.weightDao().deleteAll()
        db.foodStockDao().deleteAll()
        db.snakeDao().deleteAll()

        val summary = repository.importJson(exported)

        assertEquals(
            ImportSummary.Success(
                listOf(
                    TableCount(BackupTable.SNAKE, 2),
                    TableCount(BackupTable.FEEDING, 2),
                    TableCount(BackupTable.SHED, 1),
                    TableCount(BackupTable.WEIGHT, 1),
                    TableCount(BackupTable.FOOD_STOCK, 1)
                )
            ),
            summary
        )
        // Row-for-row identical to the pre-export state, ids included -
        // snake links, the food-stock link, nulls and non-ASCII included.
        assertEquals(before, snapshot())
        val feeding = db.feedingDao().getAllOnce().first { it.id == 21L }
        assertEquals(5L, feeding.snakeId)
        assertEquals(11L, feeding.foodStockItemId)
    }
}
