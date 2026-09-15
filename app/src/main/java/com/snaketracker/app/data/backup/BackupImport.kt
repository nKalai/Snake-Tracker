package com.snaketracker.app.data.backup

import kotlinx.serialization.SerializationException

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
    data class OrphanChildRow(val childTable: ChildTable, val rowId: Long, val snakeId: Long) : ImportFailure
}

/** Child table whose row failed the in-file snake reference check. */
enum class ChildTable { FEEDING, SHED, WEIGHT }

/**
 * Outcome of `BackupRepository.importJson`: per-table imported counts on
 * success, or the typed [ImportFailure] that stopped the import.
 */
sealed interface ImportSummary {
    data class Success(
        val snakes: Int,
        val feedings: Int,
        val sheds: Int,
        val weights: Int,
        val foodStock: Int
    ) : ImportSummary

    data class Failure(val reason: ImportFailure) : ImportSummary
}

/**
 * Result of the pure import gate: either the decoded document, ready for
 * restore, or the reason the file must not touch the database.
 */
internal sealed interface ImportInspection {
    data class Accepted(val document: BackupDocument) : ImportInspection
    data class Rejected(val reason: ImportFailure) : ImportInspection
}

/**
 * The whole rejection policy as one pure function over the wire format
 * (no Android or Room imports, so every rejection rule is JVM-testable):
 *
 * 1. malformed JSON or a missing locked-schema key → [ImportFailure.MalformedJson]
 * 2. `schemaVersion` other than 1 → [ImportFailure.UnsupportedSchemaVersion]
 * 3. child row whose `snakeId` has no snake in the file → [ImportFailure.OrphanChildRow]
 *
 * A dangling `foodStockItemId` is deliberately *not* a rejection: it is
 * nulled by [resolveStockLinks] and the row imports.
 */
internal fun inspectBackup(json: String): ImportInspection {
    val document = try {
        BackupJson.decodeFromString(BackupDocument.serializer(), json)
    } catch (_: SerializationException) {
        return ImportInspection.Rejected(ImportFailure.MalformedJson)
    }
    if (document.schemaVersion != BackupDocument.SCHEMA_VERSION) {
        return ImportInspection.Rejected(ImportFailure.UnsupportedSchemaVersion(document.schemaVersion))
    }
    findOrphanChildRow(document.data)?.let { return ImportInspection.Rejected(it) }
    return ImportInspection.Accepted(document)
}

/** First child row referencing a snake that is absent from the file, if any. */
private fun findOrphanChildRow(data: BackupData): ImportFailure? {
    val snakeIds = data.snakes.map { it.id }.toSet()
    data.feedings.firstOrNull { it.snakeId !in snakeIds }?.let {
        return ImportFailure.OrphanChildRow(ChildTable.FEEDING, it.id, it.snakeId)
    }
    data.sheds.firstOrNull { it.snakeId !in snakeIds }?.let {
        return ImportFailure.OrphanChildRow(ChildTable.SHED, it.id, it.snakeId)
    }
    data.weights.firstOrNull { it.snakeId !in snakeIds }?.let {
        return ImportFailure.OrphanChildRow(ChildTable.WEIGHT, it.id, it.snakeId)
    }
    return null
}

/**
 * Nulls `foodStockItemId` on feedings whose stock link points at no food-stock
 * row in the file (issue #27 WB2: import the row, drop the link). Resolving
 * links is safe because `feeding_events` has no FK to `food_stock`; ids of
 * rows that survive restore stay untouched.
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

/** Per-table counts of an accepted file, as reported in [ImportSummary.Success]. */
internal fun importSummaryOf(data: BackupData): ImportSummary.Success = ImportSummary.Success(
    snakes = data.snakes.size,
    feedings = data.feedings.size,
    sheds = data.sheds.size,
    weights = data.weights.size,
    foodStock = data.foodStock.size
)
