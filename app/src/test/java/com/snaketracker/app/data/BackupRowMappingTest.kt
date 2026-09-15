package com.snaketracker.app.data

import com.snaketracker.app.data.entities.FeedingEvent
import com.snaketracker.app.data.entities.FoodStockItem
import com.snaketracker.app.data.entities.ShedEvent
import com.snaketracker.app.data.entities.Snake
import com.snaketracker.app.data.entities.WeightEntry
import com.snaketracker.app.data.backup.toEntity
import com.snaketracker.app.data.backup.toRow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The backup wire format must lose nothing: every entity field survives
 * entity -> row -> entity, including the nullable optionals.
 */
class BackupRowMappingTest {

    @Test
    fun snakeRow_roundTriips_withNullOptionalDates() {
        val snake = Snake(
            id = 7,
            name = "Noodle",
            species = "Python regius",
            morph = "Banana",
            sex = "Female",
            birthDate = null,
            acquisitionDate = 1_700_000_000_000,
            enclosure = " Rack 1 ",
            notes = "Feedy",
            feedingIntervalDays = 9,
            remindersEnabled = false
        )

        assertEquals(snake, snake.toRow().toEntity())
    }

    @Test
    fun snakeRow_keepsOriginalIdAndBothDates() {
        val snake = Snake(id = 42, name = "Cobra", birthDate = 1_600_000_000_000L)

        val row = snake.toRow()

        assertEquals(42L, row.id)
        assertEquals(1_600_000_000_000L, row.birthDate)
        assertEquals(null, row.acquisitionDate)
        assertEquals(snake, row.toEntity())
    }

    @Test
    fun feedingRow_roundTriips_withNullFoodStockReference() {
        val event = FeedingEvent(
            id = 3,
            snakeId = 7,
            date = 1_750_000_000_000,
            foodType = "Mouse",
            foodSize = "Adult",
            accepted = false,
            assist = true,
            notes = "Refused twice",
            foodStockItemId = null
        )

        assertEquals(event, event.toRow().toEntity())
    }

    @Test
    fun feedingRow_keepsFoodStockReferenceWhenPresent() {
        val event = FeedingEvent(
            id = 4,
            snakeId = 7,
            date = 1_750_000_000_000,
            foodType = "Rat",
            foodStockItemId = 12
        )

        val row = event.toRow()

        assertEquals(12L, row.foodStockItemId)
        assertEquals(event, row.toEntity())
    }

    @Test
    fun shedRow_roundTriips() {
        val event = ShedEvent(id = 5, snakeId = 7, date = 1_750_000_000_000, complete = false, notes = "Partial")

        assertEquals(event, event.toRow().toEntity())
    }

    @Test
    fun weightRow_roundTriips() {
        val entry = WeightEntry(id = 6, snakeId = 7, date = 1_750_000_000_000, grams = 1_450.5f, notes = "Post-feed")

        assertEquals(entry, entry.toRow().toEntity())
    }

    @Test
    fun foodStockRow_roundTriips() {
        val item = FoodStockItem(
            id = 8,
            name = "Frozen mice - small",
            foodType = "Mouse",
            size = "Pinky",
            quantity = 0,
            lowStockThreshold = 3,
            notes = "Restock"
        )

        assertEquals(item, item.toRow().toEntity())
    }
}
