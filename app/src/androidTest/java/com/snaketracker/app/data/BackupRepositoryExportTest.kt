package com.snaketracker.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.snaketracker.app.data.backup.BackupDocument
import com.snaketracker.app.data.backup.BackupRepository
import com.snaketracker.app.data.backup.toEntity
import com.snaketracker.app.data.entities.FeedingEvent
import com.snaketracker.app.data.entities.FoodStockItem
import com.snaketracker.app.data.entities.ShedEvent
import com.snaketracker.app.data.entities.Snake
import com.snaketracker.app.data.entities.WeightEntry
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Export against a real (in-memory) Room database: the JSON dumped by
 * [BackupRepository.exportAll] must contain exactly the seeded rows of all
 * five tables, with their original ids.
 */
@RunWith(AndroidJUnit4::class)
class BackupRepositoryExportTest {

    private lateinit var db: AppDatabase

    // Fixed clock so exportedAt is assertable instead of wall-clock dependent.
    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-04-01T10:15:30Z"), ZoneOffset.UTC)

    @Before
    fun createDatabase() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    @Test
    fun exportAll_dumpsAllFiveTablesWithOriginalIds() = runBlocking {
        val foodStock = FoodStockItem(id = 100, name = "Frozen mice - small", foodType = "Mouse", quantity = 12)
        val noodle = Snake(id = 1, name = "Noodle", species = "Python regius", birthDate = 1_600_000_000_000)
        val cobra = Snake(id = 2, name = "Cobra", sex = "Male")
        db.foodStockDao().insert(foodStock)
        db.snakeDao().insert(noodle)
        db.snakeDao().insert(cobra)

        val feedings = listOf(
            FeedingEvent(id = 10, snakeId = 1, date = 1_750_000_000_000, foodType = "Mouse", foodSize = "Adult"),
            FeedingEvent(id = 11, snakeId = 2, date = 1_750_086_400_000, foodType = "Rat", foodStockItemId = 100)
        )
        feedings.forEach { db.feedingDao().insert(it) }

        val sheds = listOf(
            ShedEvent(id = 20, snakeId = 1, date = 1_750_100_000_000, complete = false, notes = "Partial")
        )
        sheds.forEach { db.shedDao().insert(it) }

        val weights = listOf(
            WeightEntry(id = 30, snakeId = 1, date = 1_750_120_000_000, grams = 1_450.5f),
            WeightEntry(id = 31, snakeId = 2, date = 1_750_130_000_000, grams = 900f, notes = "Fasting")
        )
        weights.forEach { db.weightDao().insert(it) }

        val document = Json.decodeFromString(
            BackupDocument.serializer(),
            BackupRepository(db, appVersion = "1.1", clock = fixedClock).exportAll()
        )

        assertEquals(1, document.schemaVersion)
        assertEquals("1.1", document.appVersion)
        assertEquals("2026-04-01T10:15:30Z", document.exportedAt)

        assertEquals(listOf(noodle, cobra), document.data.snakes.map { it.toEntity() })
        assertEquals(feedings, document.data.feedings.map { it.toEntity() })
        assertEquals(sheds, document.data.sheds.map { it.toEntity() })
        assertEquals(weights, document.data.weights.map { it.toEntity() })
        assertEquals(listOf(foodStock), document.data.foodStock.map { it.toEntity() })
    }

    @Test
    fun exportAll_emptyDatabase_writesEnvelopeWithEmptySections() = runBlocking {
        val json = BackupRepository(db, appVersion = "1.1", clock = fixedClock).exportAll()

        val document = Json.decodeFromString(BackupDocument.serializer(), json)

        assertTrue(document.data.snakes.isEmpty())
        assertTrue(document.data.feedings.isEmpty())
        assertTrue(document.data.sheds.isEmpty())
        assertTrue(document.data.weights.isEmpty())
        assertTrue(document.data.foodStock.isEmpty())
        assertEquals("2026-04-01T10:15:30Z", document.exportedAt)
    }

    @Test
    fun exportAll_preservesNullOptionalFields() = runBlocking {
        db.snakeDao().insert(Snake(id = 5, name = "Null-dates"))
        db.feedingDao().insert(
            FeedingEvent(id = 50, snakeId = 5, date = 1_750_000_000_000, foodType = "Mouse")
        )

        val document = Json.decodeFromString(
            BackupDocument.serializer(),
            BackupRepository(db, appVersion = "1.1", clock = fixedClock).exportAll()
        )

        val snake = document.data.snakes.single()
        assertEquals(null, snake.birthDate)
        assertEquals(null, snake.acquisitionDate)
        assertEquals(null, document.data.feedings.single().foodStockItemId)
    }
}
