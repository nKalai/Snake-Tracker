package com.snaketracker.app.data.backup

import com.snaketracker.app.data.entities.FeedingEvent
import com.snaketracker.app.data.entities.FoodStockItem
import com.snaketracker.app.data.entities.ShedEvent
import com.snaketracker.app.data.entities.Snake
import com.snaketracker.app.data.entities.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pure five-tables-to-five-row-arrays assembly, kept out of the Room
 * transaction (mirroring [com.snaketracker.app.data.buildReminderCandidates])
 * so a missing or miswired table fails as a JVM test, not only on a device.
 */
class BackupDataTest {

    @Test
    fun backupData_mapsEveryTableIntoItsNamedRowArray() {
        val snake = Snake(id = 1, name = "Noodle")
        val feeding = FeedingEvent(id = 10, snakeId = 1, date = 1_750_000_000_000, foodType = "Mouse")
        val shed = ShedEvent(id = 20, snakeId = 1, date = 1_750_100_000_000)
        val weight = WeightEntry(id = 30, snakeId = 1, date = 1_750_120_000_000, grams = 1_450.5f)
        val foodStock = FoodStockItem(id = 100, name = "Frozen mice", foodType = "Mouse")

        val data = backupData(
            snakes = listOf(snake),
            feedings = listOf(feeding),
            sheds = listOf(shed),
            weights = listOf(weight),
            foodStock = listOf(foodStock)
        )

        assertEquals(listOf(snake), data.snakes.map { it.toEntity() })
        assertEquals(listOf(feeding), data.feedings.map { it.toEntity() })
        assertEquals(listOf(shed), data.sheds.map { it.toEntity() })
        assertEquals(listOf(weight), data.weights.map { it.toEntity() })
        assertEquals(listOf(foodStock), data.foodStock.map { it.toEntity() })
    }

    @Test
    fun backupData_preservesRowOrderWithinEachArray() {
        val snakes = listOf(Snake(id = 2, name = "Cobra"), Snake(id = 1, name = "Noodle"))

        val data = backupData(snakes, emptyList(), emptyList(), emptyList(), emptyList())

        assertEquals(listOf(2L, 1L), data.snakes.map { it.id })
    }
}
