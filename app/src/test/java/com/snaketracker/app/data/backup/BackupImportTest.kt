package com.snaketracker.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pure import gate (issue #27 WB2/WB5): everything that can reject a
 * file is a JVM-testable function over the backup DTOs — decode failure,
 * unsupported schemaVersion, and orphan child rows — with no Android or
 * Room imports in the path under test.
 */
class BackupImportTest {

    private fun envelope(
        schemaVersion: Int = 1,
        data: BackupData = BackupData()
    ): String = BackupJson.encodeToString(
        BackupDocument.serializer(),
        BackupDocument(
            schemaVersion = schemaVersion,
            appVersion = "1.1",
            exportedAt = "2026-04-01T10:15:30Z",
            data = data
        )
    )

    private val oneSnake = BackupData(snakes = listOf(SnakeRow(1, "Noodle", "", "", "Unknown", null, null, "", "", 7, true)))

    // --- parse rejections -------------------------------------------------

    @Test
    fun inspectBackup_malformedJson_rejectsAsParseError() {
        val inspection = inspectBackup("{ not json")

        assertEquals(ImportInspection.Rejected(ImportFailure.MalformedJson), inspection)
    }

    @Test
    fun inspectBackup_missingFieldSet_rejectsAsParseError() {
        // The locked schema writes every key, so a file missing one is
        // corrupt or hand-edited: same parse-error class as malformed JSON.
        val withoutAppVersion =
            """{"schemaVersion":1,"exportedAt":"2026-04-01T10:15:30Z","data":""" +
                """{"snakes":[],"feedings":[],"sheds":[],"weights":[],"foodStock":[]}}"""

        val inspection = inspectBackup(withoutAppVersion)

        assertEquals(ImportInspection.Rejected(ImportFailure.MalformedJson), inspection)
    }

    // --- version rejection --------------------------------------------------

    @Test
    fun inspectBackup_schemaVersionTwo_rejectsAsUnsupportedVersion() {
        val inspection = inspectBackup(envelope(schemaVersion = 2, data = oneSnake))

        assertEquals(
            ImportInspection.Rejected(ImportFailure.UnsupportedSchemaVersion(found = 2)),
            inspection
        )
    }

    @Test
    fun inspectBackup_unknownSchemaVersion_rejectsWhateverTheNumberIs() {
        val inspection = inspectBackup(envelope(schemaVersion = 99))

        assertEquals(
            ImportInspection.Rejected(ImportFailure.UnsupportedSchemaVersion(found = 99)),
            inspection
        )
    }

    // --- orphan rejection (snakeId must resolve inside the file) ----------

    @Test
    fun inspectBackup_orphanFeeding_rejectsWholeFileWithOrphanRow() {
        val data = oneSnake.copy(
            feedings = listOf(FeedingRow(10, snakeId = 99, date = 100, foodType = "Mouse", foodSize = "", accepted = true, assist = false, notes = "", foodStockItemId = null))
        )

        val inspection = inspectBackup(envelope(data = data))

        assertEquals(
            ImportInspection.Rejected(ImportFailure.OrphanChildRow(ChildTable.FEEDING, rowId = 10, snakeId = 99)),
            inspection
        )
    }

    @Test
    fun inspectBackup_orphanShed_rejectsWholeFile() {
        val data = oneSnake.copy(
            sheds = listOf(ShedRow(id = 20, snakeId = 99, date = 100, complete = true, notes = ""))
        )

        val inspection = inspectBackup(envelope(data = data))

        assertEquals(
            ImportInspection.Rejected(ImportFailure.OrphanChildRow(ChildTable.SHED, rowId = 20, snakeId = 99)),
            inspection
        )
    }

    @Test
    fun inspectBackup_orphanWeight_rejectsWholeFile() {
        val data = oneSnake.copy(
            weights = listOf(WeightRow(id = 30, snakeId = 99, date = 100, grams = 10f, notes = ""))
        )

        val inspection = inspectBackup(envelope(data = data))

        assertEquals(
            ImportInspection.Rejected(ImportFailure.OrphanChildRow(ChildTable.WEIGHT, rowId = 30, snakeId = 99)),
            inspection
        )
    }

    @Test
    fun inspectBackup_childRowWithNoSnakesAtAll_rejectsAsOrphan() {
        val data = BackupData(
            feedings = listOf(FeedingRow(10, snakeId = 1, date = 100, foodType = "Mouse", foodSize = "", accepted = true, assist = false, notes = "", foodStockItemId = null))
        )

        val inspection = inspectBackup(envelope(data = data))

        assertEquals(
            ImportInspection.Rejected(ImportFailure.OrphanChildRow(ChildTable.FEEDING, rowId = 10, snakeId = 1)),
            inspection
        )
    }

    // --- dangling stock link: nulled, never rejected -----------------------

    @Test
    fun inspectBackup_danglingFoodStockItemId_isAcceptedNotRejected() {
        val data = oneSnake.copy(
            feedings = listOf(FeedingRow(10, snakeId = 1, date = 100, foodType = "Mouse", foodSize = "", accepted = true, assist = false, notes = "", foodStockItemId = 555))
        )

        val inspection = inspectBackup(envelope(data = data))

        assertEquals(ImportInspection.Accepted(BackupDocument(1, "1.1", "2026-04-01T10:15:30Z", data)), inspection)
    }

    @Test
    fun resolveStockLinks_danglingLinkNulled_rowStillImported() {
        val data = oneSnake.copy(
            feedings = listOf(FeedingRow(10, snakeId = 1, date = 100, foodType = "Mouse", foodSize = "", accepted = true, assist = false, notes = "", foodStockItemId = 555))
        )

        val resolved = resolveStockLinks(data)

        // Row survives; only the stock link is dropped.
        assertEquals(1, resolved.feedings.size)
        assertEquals(null, resolved.feedings.single().foodStockItemId)
        assertEquals(10L, resolved.feedings.single().id)
    }

    @Test
    fun resolveStockLinks_resolvingLinksAndNullsPassThroughUnchanged() {
        val data = BackupData(
            snakes = oneSnake.snakes,
            feedings = listOf(
                FeedingRow(10, snakeId = 1, date = 100, foodType = "Mouse", foodSize = "", accepted = true, assist = false, notes = "", foodStockItemId = 100),
                FeedingRow(11, snakeId = 1, date = 200, foodType = "Rat", foodSize = "", accepted = true, assist = false, notes = "", foodStockItemId = null)
            ),
            foodStock = listOf(FoodStockRow(100, "Mice", "Mouse", "", 5, 2, ""))
        )

        assertEquals(data, resolveStockLinks(data))
    }

    // --- accepted files report per-table counts -----------------------------

    @Test
    fun inspectBackup_validEnvelope_isAccepted() {
        val data = BackupData(
            snakes = oneSnake.snakes,
            feedings = listOf(FeedingRow(10, snakeId = 1, date = 100, foodType = "Mouse", foodSize = "", accepted = true, assist = false, notes = "", foodStockItemId = null)),
            sheds = listOf(ShedRow(20, 1, 100, true, "")),
            weights = listOf(WeightRow(30, 1, 100, 10f, "")),
            foodStock = listOf(FoodStockRow(100, "Mice", "Mouse", "", 5, 2, ""))
        )

        val inspection = inspectBackup(envelope(data = data))

        assertEquals(ImportInspection.Accepted(BackupDocument(1, "1.1", "2026-04-01T10:15:30Z", data)), inspection)
    }

    @Test
    fun importSummaryOf_countsEveryTableOfTheFile() {
        val data = BackupData(
            snakes = oneSnake.snakes + SnakeRow(2, "Cobra", "", "", "Unknown", null, null, "", "", 7, true),
            feedings = listOf(
                FeedingRow(10, snakeId = 1, date = 100, foodType = "Mouse", foodSize = "", accepted = true, assist = false, notes = "", foodStockItemId = null),
                FeedingRow(11, snakeId = 2, date = 200, foodType = "Rat", foodSize = "", accepted = true, assist = false, notes = "", foodStockItemId = null),
                FeedingRow(12, snakeId = 1, date = 300, foodType = "Mouse", foodSize = "", accepted = false, assist = false, notes = "", foodStockItemId = null)
            ),
            sheds = listOf(ShedRow(20, 1, 100, true, "")),
            weights = listOf(WeightRow(30, 1, 100, 10f, ""), WeightRow(31, 2, 200, 20f, "")),
            foodStock = listOf(FoodStockRow(100, "Mice", "Mouse", "", 5, 2, ""))
        )

        assertEquals(
            ImportSummary.Success(snakes = 2, feedings = 3, sheds = 1, weights = 2, foodStock = 1),
            importSummaryOf(data)
        )
    }

    @Test
    fun importSummaryOf_emptyFile_countsZeros() {
        assertEquals(
            ImportSummary.Success(snakes = 0, feedings = 0, sheds = 0, weights = 0, foodStock = 0),
            importSummaryOf(BackupData())
        )
    }
}
