package com.snaketracker.app.data.backup

import com.snaketracker.app.data.entities.FeedingEvent
import com.snaketracker.app.data.entities.FoodStockItem
import com.snaketracker.app.data.entities.ShedEvent
import com.snaketracker.app.data.entities.Snake
import com.snaketracker.app.data.entities.WeightEntry
import kotlinx.serialization.Serializable

/**
 * The wire format of a full backup file. This is the only place the JSON
 * schema is known; Room entities and DAOs stay format-agnostic.
 *
 * Row payloads carry their original Room ids verbatim, and row dates stay
 * epoch millis. Only [exportedAt] is an ISO-8601 string.
 */
@Serializable
data class BackupDocument(
    val schemaVersion: Int = SCHEMA_VERSION,
    val appVersion: String,
    val exportedAt: String,
    val data: BackupData
) {
    companion object {
        /** Locked schema version of the backup format (issue #25 WB2). */
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupData(
    val snakes: List<SnakeRow> = emptyList(),
    val feedings: List<FeedingRow> = emptyList(),
    val sheds: List<ShedRow> = emptyList(),
    val weights: List<WeightRow> = emptyList(),
    val foodStock: List<FoodStockRow> = emptyList()
)

@Serializable
data class SnakeRow(
    val id: Long,
    val name: String,
    val species: String = "",
    val morph: String = "",
    val sex: String = "Unknown",
    val birthDate: Long? = null,
    val acquisitionDate: Long? = null,
    val enclosure: String = "",
    val notes: String = "",
    val feedingIntervalDays: Int = 7,
    val remindersEnabled: Boolean = true
)

@Serializable
data class FeedingRow(
    val id: Long,
    val snakeId: Long,
    val date: Long,
    val foodType: String,
    val foodSize: String = "",
    val accepted: Boolean = true,
    val assist: Boolean = false,
    val notes: String = "",
    val foodStockItemId: Long? = null
)

@Serializable
data class ShedRow(
    val id: Long,
    val snakeId: Long,
    val date: Long,
    val complete: Boolean = true,
    val notes: String = ""
)

@Serializable
data class WeightRow(
    val id: Long,
    val snakeId: Long,
    val date: Long,
    val grams: Float,
    val notes: String = ""
)

@Serializable
data class FoodStockRow(
    val id: Long,
    val name: String,
    val foodType: String,
    val size: String = "",
    val quantity: Int = 0,
    val lowStockThreshold: Int = 5,
    val notes: String = ""
)

// Entity <-> row mapping. Kept beside the wire format so the mapping is
// versioned together with the schema it produces.

fun Snake.toRow() = SnakeRow(
    id = id,
    name = name,
    species = species,
    morph = morph,
    sex = sex,
    birthDate = birthDate,
    acquisitionDate = acquisitionDate,
    enclosure = enclosure,
    notes = notes,
    feedingIntervalDays = feedingIntervalDays,
    remindersEnabled = remindersEnabled
)

fun SnakeRow.toEntity() = Snake(
    id = id,
    name = name,
    species = species,
    morph = morph,
    sex = sex,
    birthDate = birthDate,
    acquisitionDate = acquisitionDate,
    enclosure = enclosure,
    notes = notes,
    feedingIntervalDays = feedingIntervalDays,
    remindersEnabled = remindersEnabled
)

fun FeedingEvent.toRow() = FeedingRow(
    id = id,
    snakeId = snakeId,
    date = date,
    foodType = foodType,
    foodSize = foodSize,
    accepted = accepted,
    assist = assist,
    notes = notes,
    foodStockItemId = foodStockItemId
)

fun FeedingRow.toEntity() = FeedingEvent(
    id = id,
    snakeId = snakeId,
    date = date,
    foodType = foodType,
    foodSize = foodSize,
    accepted = accepted,
    assist = assist,
    notes = notes,
    foodStockItemId = foodStockItemId
)

fun ShedEvent.toRow() = ShedRow(
    id = id,
    snakeId = snakeId,
    date = date,
    complete = complete,
    notes = notes
)

fun ShedRow.toEntity() = ShedEvent(
    id = id,
    snakeId = snakeId,
    date = date,
    complete = complete,
    notes = notes
)

fun WeightEntry.toRow() = WeightRow(
    id = id,
    snakeId = snakeId,
    date = date,
    grams = grams,
    notes = notes
)

fun WeightRow.toEntity() = WeightEntry(
    id = id,
    snakeId = snakeId,
    date = date,
    grams = grams,
    notes = notes
)

fun FoodStockItem.toRow() = FoodStockRow(
    id = id,
    name = name,
    foodType = foodType,
    size = size,
    quantity = quantity,
    lowStockThreshold = lowStockThreshold,
    notes = notes
)

fun FoodStockRow.toEntity() = FoodStockItem(
    id = id,
    name = name,
    foodType = foodType,
    size = size,
    quantity = quantity,
    lowStockThreshold = lowStockThreshold,
    notes = notes
)
