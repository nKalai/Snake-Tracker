package com.snaketracker.app.data.backup

import com.snaketracker.app.data.entities.FeedingEvent
import com.snaketracker.app.data.entities.FoodStockItem
import com.snaketracker.app.data.entities.ShedEvent
import com.snaketracker.app.data.entities.Snake
import com.snaketracker.app.data.entities.WeightEntry
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Shared encoder for the backup wire format: every field is written, even
 * defaults, so explicit nulls survive on disk (ADR-0002). Lives beside the
 * schema it locks; tests of the format must encode/decode through this val,
 * never a test-local copy.
 */
internal val BackupJson = Json { encodeDefaults = true }

/**
 * Locked [BackupDocument.exportedAt] format: ISO-8601 UTC that always
 * carries seconds (and never a fraction). `Instant.toString`/`ISO_INSTANT`
 * drop the seconds field on some runtimes when seconds and nanos are zero,
 * which would give the envelope two shapes; one explicit pattern avoids
 * depending on that behaviour. Import (#26) validates exactly this pattern.
 */
private val ExportedAtFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneOffset.UTC)
        .withLocale(Locale.ROOT)

internal fun formatExportedAt(instant: Instant): String = ExportedAtFormatter.format(instant)

/**
 * The wire format of a full backup file. This is the only place the JSON
 * schema is known; Room entities and DAOs stay format-agnostic.
 *
 * Row payloads carry their original Room ids verbatim, and row dates stay
 * epoch millis. Only [exportedAt] is an ISO-8601 string.
 */
@Serializable
data class BackupDocument(
    // No default: a file without the key must fail loudly at the import
    // gate (#26), never silently validate as the current version.
    val schemaVersion: Int,
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

// Row payloads carry no defaults on purpose: export always writes every key,
// so a decoded file missing one is corrupt or hand-edited and must fail fast
// at the import gate. Defaults here would be a second, unlinked copy of the
// entity defaults silently rewriting restored data.

@Serializable
data class SnakeRow(
    val id: Long,
    val name: String,
    val species: String,
    val morph: String,
    val sex: String,
    val birthDate: Long?,
    val acquisitionDate: Long?,
    val enclosure: String,
    val notes: String,
    val feedingIntervalDays: Int,
    val remindersEnabled: Boolean
)

@Serializable
data class FeedingRow(
    val id: Long,
    val snakeId: Long,
    val date: Long,
    val foodType: String,
    val foodSize: String,
    val accepted: Boolean,
    val assist: Boolean,
    val notes: String,
    val foodStockItemId: Long?
)

@Serializable
data class ShedRow(
    val id: Long,
    val snakeId: Long,
    val date: Long,
    val complete: Boolean,
    val notes: String
)

@Serializable
data class WeightRow(
    val id: Long,
    val snakeId: Long,
    val date: Long,
    val grams: Float,
    val notes: String
)

@Serializable
data class FoodStockRow(
    val id: Long,
    val name: String,
    val foodType: String,
    val size: String,
    val quantity: Int,
    val lowStockThreshold: Int,
    val notes: String
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
