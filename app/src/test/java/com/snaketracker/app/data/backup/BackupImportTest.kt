package com.snaketracker.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pure import gate (issue #27 WB2/WB5): everything that can reject a
 * file is a JVM-testable function over the backup DTOs — decode failure,
 * unsupported schemaVersion, invalid row ids, and orphan child rows — with
 * no Android or Room imports in the path under test.
 */
class BackupImportTest {

    // Fixture builders: sane defaults inside, only the fields a test cares
    // about passed by name (the convention in BackupRepositoryExportTest and
    // BackupDataTest). The default-less DTO constructors are a wire-schema
    // constraint, not a fixture constraint.

    private fun snakeRow(
        id: Long = 1,
        name: String = "Noodle",
        remindersEnabled: Boolean = true
    ): SnakeRow = SnakeRow(
        id = id,
        name = name,
        species = "",
        morph = "",
        sex = "Unknown",
        birthDate = null,
        acquisitionDate = null,
        enclosure = "",
        notes = "",
        feedingIntervalDays = 7,
        remindersEnabled = remindersEnabled
    )

    private fun feedingRow(
        id: Long = 10,
        snakeId: Long = 1,
        date: Long = 100,
        foodType: String = "Mouse",
        accepted: Boolean = true,
        foodStockItemId: Long? = null
    ): FeedingRow = FeedingRow(
        id = id,
        snakeId = snakeId,
        date = date,
        foodType = foodType,
        foodSize = "",
        accepted = accepted,
        assist = false,
        notes = "",
        foodStockItemId = foodStockItemId
    )

    private fun shedRow(
        id: Long = 20,
        snakeId: Long = 1,
        date: Long = 100,
        complete: Boolean = true
    ): ShedRow = ShedRow(id = id, snakeId = snakeId, date = date, complete = complete, notes = "")

    private fun weightRow(
        id: Long = 30,
        snakeId: Long = 1,
        date: Long = 100,
        grams: Float = 10f
    ): WeightRow = WeightRow(id = id, snakeId = snakeId, date = date, grams = grams, notes = "")

    private fun stockRow(id: Long = 100, name: String = "Mice"): FoodStockRow =
        FoodStockRow(id = id, name = name, foodType = "Mouse", size = "", quantity = 5, lowStockThreshold = 2, notes = "")

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

    private fun envelope(
        schemaVersion: Int = 1,
        data: BackupData = fileData()
    ): String = BackupJson.encodeToString(
        BackupDocument.serializer(),
        BackupDocument(
            schemaVersion = schemaVersion,
            appVersion = ENVELOPE_APP_VERSION,
            exportedAt = ENVELOPE_EXPORTED_AT,
            data = data
        )
    )

    private val oneSnake = fileData(snakes = listOf(snakeRow()))

    // --- parse rejections -------------------------------------------------

    @Test
    fun inspectBackup_malformedJson_rejectsAsMalformedJson() {
        val inspection = inspectBackup("{ not json")

        assertEquals(ImportInspection.Rejected(ImportFailure.MalformedJson), inspection)
    }

    @Test
    fun inspectBackup_missingFieldSet_rejectsAsMalformedJson() {
        // The locked schema writes every key, so a file missing one is
        // corrupt or hand-edited: same rejection class as malformed JSON.
        val withoutAppVersion =
            """{"schemaVersion":1,"exportedAt":"$ENVELOPE_EXPORTED_AT","data":""" +
                """{"snakes":[],"feedings":[],"sheds":[],"weights":[],"foodStock":[]}}"""

        val inspection = inspectBackup(withoutAppVersion)

        assertEquals(ImportInspection.Rejected(ImportFailure.MalformedJson), inspection)
    }

    @Test
    fun inspectBackup_dataMissingListKey_rejectsAsMalformedJson() {
        // Export always writes all five list keys, so one missing is a
        // corrupt or hand-edited file: the file must not import as
        // "that table is empty".
        val withoutFoodStock =
            """{"schemaVersion":1,"appVersion":"$ENVELOPE_APP_VERSION","exportedAt":"$ENVELOPE_EXPORTED_AT",""" +
                """"data":{"snakes":[],"feedings":[],"sheds":[],"weights":[]}}"""

        val inspection = inspectBackup(withoutFoodStock)

        assertEquals(ImportInspection.Rejected(ImportFailure.MalformedJson), inspection)
    }

    @Test
    fun inspectBackup_rowMissingKey_rejectsAsMalformedJson() {
        // The row DTOs carry no defaults precisely so a hand-edited row
        // missing a column fails here, not silently mid-insert: this snake
        // row has no "morph" key.
        val snakeWithoutMorph =
            """{"schemaVersion":1,"appVersion":"$ENVELOPE_APP_VERSION","exportedAt":"$ENVELOPE_EXPORTED_AT",""" +
                """"data":{"snakes":[{"id":1,"name":"Noodle","species":"","sex":"Unknown",""" +
                """"birthDate":null,"acquisitionDate":null,"enclosure":"","notes":"",""" +
                """"feedingIntervalDays":7,"remindersEnabled":true}],""" +
                """"feedings":[],"sheds":[],"weights":[],"foodStock":[]}}"""

        val inspection = inspectBackup(snakeWithoutMorph)

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
    fun inspectBackup_schemaVersionTwoWithUnknownField_rejectsAsUnsupportedVersion() {
        // The likeliest future-version file: schemaVersion 2 also changes the
        // shape, which the strict v1 decoder alone would report as corrupt.
        // The version policy must run first so #28 can say "unsupported".
        val versionTwoWithAddedField =
            """{"schemaVersion":2,"appVersion":"2.0","exportedAt":"2026-05-01T08:00:00Z",""" +
                """"data":{"snakes":[],"feedings":[],"sheds":[],"weights":[],"foodStock":[],"hydrationLog":[]}}"""

        val inspection = inspectBackup(versionTwoWithAddedField)

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
        val data = oneSnake.copy(feedings = listOf(feedingRow(snakeId = 99)))

        val inspection = inspectBackup(envelope(data = data))

        assertEquals(
            ImportInspection.Rejected(ImportFailure.OrphanChildRow(BackupTable.FEEDING, rowId = 10, snakeId = 99)),
            inspection
        )
    }

    @Test
    fun inspectBackup_orphanShed_rejectsWholeFile() {
        val data = oneSnake.copy(sheds = listOf(shedRow(snakeId = 99)))

        val inspection = inspectBackup(envelope(data = data))

        assertEquals(
            ImportInspection.Rejected(ImportFailure.OrphanChildRow(BackupTable.SHED, rowId = 20, snakeId = 99)),
            inspection
        )
    }

    @Test
    fun inspectBackup_orphanWeight_rejectsWholeFile() {
        val data = oneSnake.copy(weights = listOf(weightRow(snakeId = 99)))

        val inspection = inspectBackup(envelope(data = data))

        assertEquals(
            ImportInspection.Rejected(ImportFailure.OrphanChildRow(BackupTable.WEIGHT, rowId = 30, snakeId = 99)),
            inspection
        )
    }

    @Test
    fun inspectBackup_childRowWithNoSnakesAtAll_rejectsAsOrphan() {
        val data = fileData(feedings = listOf(feedingRow()))

        val inspection = inspectBackup(envelope(data = data))

        assertEquals(
            ImportInspection.Rejected(ImportFailure.OrphanChildRow(BackupTable.FEEDING, rowId = 10, snakeId = 1)),
            inspection
        )
    }

    // --- row id validity: Room would silently renumber id 0 ----------------

    @Test
    fun inspectBackup_snakeWithIdZero_isRejectedAsInvalidRowId() {
        val data = fileData(snakes = listOf(snakeRow(id = 0)))

        val inspection = inspectBackup(envelope(data = data))

        // Room's insert maps id 0 to a NEW id (nullif(?, 0)), so accepting
        // this row would violate "original ids preserved" — reject typed.
        assertEquals(
            ImportInspection.Rejected(ImportFailure.InvalidRowId(BackupTable.SNAKE, rowId = 0)),
            inspection
        )
    }

    @Test
    fun inspectBackup_stockIdZeroWithFeedingLinkedToIt_rejectsBeforeAnyLinkRepair() {
        // The worst sub-case: id-0 stock + a feeding linked to 0 used to
        // commit cleanly with a dangling stock reference after the silent
        // renumber.
        val data = fileData(
            snakes = oneSnake.snakes,
            feedings = listOf(feedingRow(foodStockItemId = 0)),
            foodStock = listOf(stockRow(id = 0))
        )

        val inspection = inspectBackup(envelope(data = data))

        assertEquals(
            ImportInspection.Rejected(ImportFailure.InvalidRowId(BackupTable.FOOD_STOCK, rowId = 0)),
            inspection
        )
    }

    @Test
    fun inspectBackup_negativeRowId_isRejectedAsInvalidRowId() {
        val data = oneSnake.copy(sheds = listOf(shedRow(id = -5)))

        val inspection = inspectBackup(envelope(data = data))

        assertEquals(
            ImportInspection.Rejected(ImportFailure.InvalidRowId(BackupTable.SHED, rowId = -5)),
            inspection
        )
    }

    // --- dangling stock link: nulled, never rejected -----------------------

    @Test
    fun inspectBackup_danglingFoodStockItemId_isAcceptedWithLinkAlreadyNulled() {
        val data = oneSnake.copy(feedings = listOf(feedingRow(foodStockItemId = 555)))
        val insertSafeData = data.copy(feedings = listOf(feedingRow()))

        val inspection = inspectBackup(envelope(data = data))

        // Accepted carries restore-ready rows: the gate nulls the link itself,
        // so no caller can forget the repair step (issue #27 WB2).
        assertEquals(
            ImportInspection.Accepted(BackupDocument(1, ENVELOPE_APP_VERSION, ENVELOPE_EXPORTED_AT, insertSafeData)),
            inspection
        )
    }

    @Test
    fun resolveStockLinks_danglingLinkNulled_rowStillImported() {
        val data = oneSnake.copy(feedings = listOf(feedingRow(foodStockItemId = 555)))

        val resolved = resolveStockLinks(data)

        // Row survives; only the stock link is dropped.
        assertEquals(1, resolved.feedings.size)
        assertEquals(null, resolved.feedings.single().foodStockItemId)
        assertEquals(10L, resolved.feedings.single().id)
    }

    @Test
    fun resolveStockLinks_resolvingLinksAndNullsPassThroughUnchanged() {
        val data = fileData(
            snakes = oneSnake.snakes,
            feedings = listOf(
                feedingRow(id = 10, date = 100, foodStockItemId = 100),
                feedingRow(id = 11, date = 200, foodType = "Rat")
            ),
            foodStock = listOf(stockRow())
        )

        assertEquals(data, resolveStockLinks(data))
    }

    // --- accepted files report per-table counts -----------------------------

    @Test
    fun inspectBackup_validEnvelope_isAccepted() {
        val data = fileData(
            snakes = oneSnake.snakes,
            feedings = listOf(feedingRow()),
            sheds = listOf(shedRow()),
            weights = listOf(weightRow()),
            foodStock = listOf(stockRow())
        )

        val inspection = inspectBackup(envelope(data = data))

        assertEquals(ImportInspection.Accepted(BackupDocument(1, ENVELOPE_APP_VERSION, ENVELOPE_EXPORTED_AT, data)), inspection)
    }

    @Test
    fun importSummaryOf_countsEveryTableOfTheFile() {
        val data = fileData(
            snakes = listOf(snakeRow(id = 1), snakeRow(id = 2, name = "Cobra")),
            feedings = listOf(
                feedingRow(id = 10, snakeId = 1, date = 100),
                feedingRow(id = 11, snakeId = 2, date = 200, foodType = "Rat"),
                feedingRow(id = 12, snakeId = 1, date = 300, accepted = false)
            ),
            sheds = listOf(shedRow()),
            weights = listOf(weightRow(id = 30), weightRow(id = 31, snakeId = 2, date = 200, grams = 20f)),
            foodStock = listOf(stockRow())
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
            importSummaryOf(fileData())
        )
    }

    private companion object {
        // The envelope constants one file should encode; tests that hand-
        // write raw JSON interpolate these instead of re-inlining literals.
        const val ENVELOPE_APP_VERSION = "1.1"
        const val ENVELOPE_EXPORTED_AT = "2026-04-01T10:15:30Z"
    }
}
