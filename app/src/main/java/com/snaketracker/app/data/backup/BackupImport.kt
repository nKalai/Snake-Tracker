package com.snaketracker.app.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Why a backup file was rejected. The UI (issue #28) names the reason to the
 * user from the type — every rejection class is distinguishable.
 */
sealed interface ImportFailure {
    /** Malformed JSON or a missing key from the locked schema. */
    data object MalformedJson : ImportFailure

    /** `schemaVersion` other than [BackupDocument.SCHEMA_VERSION]. */
    data class UnsupportedSchemaVersion(val found: Int) : ImportFailure

    /**
     * A child row (feeding/shed/weight) whose `snakeId` matches no snake
     * in the file — the whole file is rejected because partial restore
     * would silently orphan rows on the device.
     */
    data class OrphanChildRow(val childTable: BackupTable, val rowId: Long, val snakeId: Long) : ImportFailure

    /**
     * A row whose id cannot be a stored Room id (`id <= 0`): the device
     * would silently renumber it on insert, breaking the "original ids
     * preserved" contract, so the file is rejected instead.
     */
    data class InvalidRowId(val table: BackupTable, val rowId: Long) : ImportFailure
}

/** Table a backup row belongs to; the vocabulary rejection reasons name. */
enum class BackupTable { SNAKE, FEEDING, SHED, WEIGHT, FOOD_STOCK }

/** How many rows one restored file contributed for one table. */
data class TableCount(val table: BackupTable, val count: Int)

/**
 * Outcome of `BackupRepository.importJson`: per-table imported counts on
 * success, or the typed [ImportFailure] that stopped the import.
 *
 * The counts travel as one list in table order, so a sixth table extends
 * them here instead of cloning an N-field record through the engine, the
 * ViewModel state and the dialog (PR #34 review 🟡).
 */
sealed interface ImportSummary {
    data class Success(val counts: List<TableCount>) : ImportSummary

    data class Failure(val reason: ImportFailure) : ImportSummary
}

/**
 * Result of the pure import gate: either the validated, repaired document —
 * insert-safe by construction, ready for restore — or the reason the file
 * must not touch the database.
 */
internal sealed interface ImportInspection {
    data class Accepted(val document: BackupDocument) : ImportInspection
    data class Rejected(val reason: ImportFailure) : ImportInspection
}

/**
 * Lenient view of just the envelope header, so the version policy runs
 * before the strict decode: a `schemaVersion != 1` file may legitimately
 * change the shape, and the strict v1 decoder would report it corrupt.
 * "Too new" must stay distinguishable from "broken" (issue #27 WB2).
 */
@Serializable
private class BackupHeader(
    // No default: a file without the key stays malformed, as before.
    val schemaVersion: Int
)

private val HeaderJson = Json { ignoreUnknownKeys = true }

/**
 * The whole rejection policy as one pure function over the wire format
 * (no Android or Room imports, so every rejection rule is JVM-testable):
 *
 * 1. `schemaVersion` other than 1 (checked on a lenient header peek, so a
 *    future-version file with a changed shape is still recognized) →
 *    [ImportFailure.UnsupportedSchemaVersion]
 * 2. malformed JSON or a missing locked-schema key → [ImportFailure.MalformedJson]
 * 3. row with a non-positive Room id → [ImportFailure.InvalidRowId]
 * 4. child row whose `snakeId` has no snake in the file → [ImportFailure.OrphanChildRow]
 *
 * A dangling `foodStockItemId` is deliberately *not* a rejection: it is
 * nulled by [resolveStockLinks] inside [ImportInspection.Accepted] itself,
 * so an accepted document is insert-safe by construction.
 */
internal fun inspectBackup(json: String): ImportInspection {
    val foundVersion = try {
        HeaderJson.decodeFromString(BackupHeader.serializer(), json).schemaVersion
    } catch (_: SerializationException) {
        return ImportInspection.Rejected(ImportFailure.MalformedJson)
    }
    if (foundVersion != BackupDocument.SCHEMA_VERSION) {
        return ImportInspection.Rejected(ImportFailure.UnsupportedSchemaVersion(foundVersion))
    }
    val document = try {
        BackupJson.decodeFromString(BackupDocument.serializer(), json)
    } catch (_: SerializationException) {
        return ImportInspection.Rejected(ImportFailure.MalformedJson)
    }
    findInvalidRowId(document.data)?.let { return ImportInspection.Rejected(it) }
    findOrphanChildRow(document.data)?.let { return ImportInspection.Rejected(it) }
    return ImportInspection.Accepted(document.copy(data = resolveStockLinks(document.data)))
}

/**
 * File rows seen through just the fields the gate validates — one private
 * view so each rule is stated once for all tables instead of per list.
 * `snakeId` is null for the tables that carry no snake reference.
 */
private data class RowRef(val table: BackupTable, val id: Long, val snakeId: Long?)

private fun BackupData.rowRefs(): Sequence<RowRef> = sequenceOf(
    snakes.map { RowRef(BackupTable.SNAKE, it.id, snakeId = null) },
    feedings.map { RowRef(BackupTable.FEEDING, it.id, it.snakeId) },
    sheds.map { RowRef(BackupTable.SHED, it.id, it.snakeId) },
    weights.map { RowRef(BackupTable.WEIGHT, it.id, it.snakeId) },
    foodStock.map { RowRef(BackupTable.FOOD_STOCK, it.id, snakeId = null) }
).flatten()

/**
 * First row whose id cannot be a stored Room id, if any. Room's generated
 * inserts use `nullif(?, 0)` for these autoGenerate primary keys, so an
 * `id: 0` row from a hand-edited file would be silently renumbered and
 * break the "original ids preserved" contract (and any reference to it).
 * Export never emits such an id, so a typed rejection beats a renumbered
 * insert or a mid-FK `SQLiteConstraintException`.
 */
private fun findInvalidRowId(data: BackupData): ImportFailure? =
    data.rowRefs().firstOrNull { it.id <= 0L }
        ?.let { ImportFailure.InvalidRowId(it.table, it.id) }

/** First child row referencing a snake that is absent from the file, if any. */
private fun findOrphanChildRow(data: BackupData): ImportFailure? {
    val snakeIds = data.snakes.mapTo(HashSet()) { it.id }
    return data.rowRefs().firstNotNullOfOrNull { row ->
        val snakeId = row.snakeId
        if (snakeId != null && snakeId !in snakeIds) {
            ImportFailure.OrphanChildRow(row.table, row.id, snakeId)
        } else {
            null
        }
    }
}

/**
 * Nulls `foodStockItemId` on feedings whose stock link points at no food-stock
 * row in the file (issue #27 WB2: import the row, drop the link). Applied by
 * [inspectBackup] before [ImportInspection.Accepted] is handed out, so no
 * caller can skip the repair. Resolving links is safe because
 * `feeding_events` has no FK to `food_stock`; ids of surviving rows stay put.
 */
internal fun resolveStockLinks(data: BackupData): BackupData {
    val stockIds = data.foodStock.map { it.id }.toSet()
    return data.copy(
        feedings = data.feedings.map { feeding ->
            val link = feeding.foodStockItemId
            if (link != null && link !in stockIds) feeding.copy(foodStockItemId = null) else feeding
        }
    )
}

/**
 * Per-table counts of an accepted file, in table order, as reported in
 * [ImportSummary.Success] - the one place the five tables are enumerated
 * for counting.
 */
internal fun importSummaryOf(data: BackupData): ImportSummary.Success = ImportSummary.Success(
    counts = listOf(
        TableCount(BackupTable.SNAKE, data.snakes.size),
        TableCount(BackupTable.FEEDING, data.feedings.size),
        TableCount(BackupTable.SHED, data.sheds.size),
        TableCount(BackupTable.WEIGHT, data.weights.size),
        TableCount(BackupTable.FOOD_STOCK, data.foodStock.size)
    )
)
