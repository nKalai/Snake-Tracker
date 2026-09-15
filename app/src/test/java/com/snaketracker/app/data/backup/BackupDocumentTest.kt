package com.snaketracker.app.data.backup

import java.time.Instant
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Locked backup schema (issue #25 WB2):
 *
 * { "schemaVersion": 1, "appVersion": "1.1", "exportedAt": "<ISO-8601>",
 *   "data": { "snakes": [], "feedings": [], "sheds": [], "weights": [], "foodStock": [] } }
 */
class BackupDocumentTest {

    // Encodes and decodes through the production BackupJson, never a test
    // copy: the schema lock must guard the encoder that actually ships.

    private val emptyEnvelopeJson =
        """{"schemaVersion":1,"appVersion":"1.1","exportedAt":"2026-04-01T10:15:30Z","data":""" +
            """{"snakes":[],"feedings":[],"sheds":[],"weights":[],"foodStock":[]}}"""

    @Test
    fun envelope_encodesLockedSchemaKeysInOrder() {
        val document = BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION,
            appVersion = "1.1",
            exportedAt = "2026-04-01T10:15:30Z",
            data = BackupData()
        )

        assertEquals(emptyEnvelopeJson, BackupJson.encodeToString(BackupDocument.serializer(), document))
    }

    @Test
    fun envelope_decodesLockedSchemaJson() {
        val document = BackupJson.decodeFromString(BackupDocument.serializer(), emptyEnvelopeJson)

        assertEquals(1, document.schemaVersion)
        assertEquals("1.1", document.appVersion)
        assertEquals("2026-04-01T10:15:30Z", document.exportedAt)
        assertEquals(BackupData(), document.data)
    }

    @Test
    fun schemaVersion_isLockedAtOne() {
        assertEquals(1, BackupDocument.SCHEMA_VERSION)
    }

    @Test
    fun fullDocument_oneRowPerType_encodesLockedRowSchema() {
        // The schema #26 must read: one populated row per type, exact string,
        // through the production encoder. Locks row property names, order,
        // value shapes, and explicit nulls — not just the empty envelope.
        val document = BackupDocument(
            schemaVersion = 1,
            appVersion = "1.1",
            exportedAt = "2026-04-01T10:15:30Z",
            data = BackupData(
                snakes = listOf(
                    SnakeRow(1, "Noodle", "Python regius", "Banana", "Female", null, 1_700_000_000_000, "Rack 1", "Feedy", 9, false)
                ),
                feedings = listOf(
                    FeedingRow(10, 1, 1_750_000_000_000, "Mouse", "Adult", true, false, "", null)
                ),
                sheds = listOf(
                    ShedRow(20, 1, 1_750_100_000_000, false, "Partial")
                ),
                weights = listOf(
                    WeightRow(30, 1, 1_750_120_000_000, 1450.5f, "Post-feed")
                ),
                foodStock = listOf(
                    FoodStockRow(100, "Frozen mice", "Mouse", "Pinky", 12, 3, "")
                )
            )
        )

        val expected =
            """{"schemaVersion":1,"appVersion":"1.1","exportedAt":"2026-04-01T10:15:30Z","data":{"snakes":[{"id":1,"name":"Noodle","species":"Python regius","morph":"Banana","sex":"Female","birthDate":null,"acquisitionDate":1700000000000,"enclosure":"Rack 1","notes":"Feedy","feedingIntervalDays":9,"remindersEnabled":false}],""" +
            """"feedings":[{"id":10,"snakeId":1,"date":1750000000000,"foodType":"Mouse","foodSize":"Adult","accepted":true,"assist":false,"notes":"","foodStockItemId":null}],""" +
            """"sheds":[{"id":20,"snakeId":1,"date":1750100000000,"complete":false,"notes":"Partial"}],""" +
            """"weights":[{"id":30,"snakeId":1,"date":1750120000000,"grams":1450.5,"notes":"Post-feed"}],""" +
            """"foodStock":[{"id":100,"name":"Frozen mice","foodType":"Mouse","size":"Pinky","quantity":12,"lowStockThreshold":3,"notes":""}]}}"""

        assertEquals(expected, BackupJson.encodeToString(BackupDocument.serializer(), document))
    }

    @Test
    fun envelope_missingSchemaVersion_failsDecodeLoudly() {
        // A corrupt or hand-edited file without the version field must not
        // silently validate as v1: #26 branches on schemaVersion.
        val withoutVersion =
            """{"appVersion":"1.1","exportedAt":"2026-04-01T10:15:30Z","data":""" +
                """{"snakes":[],"feedings":[],"sheds":[],"weights":[],"foodStock":[]}}"""

        assertThrows(SerializationException::class.java) {
            BackupJson.decodeFromString(BackupDocument.serializer(), withoutVersion)
        }
    }

    // Row payloads carry no defaults: export always writes every key, so a
    // file missing one is corrupt or hand-edited and must fail fast at the
    // import gate instead of silently rewriting the restored row.

    @Test
    fun snakeRow_missingFormerlyDefaultedKey_failsDecodeLoudly() {
        val withoutSex =
            """{"id":1,"name":"N","species":"","morph":"","birthDate":null,"acquisitionDate":null,"enclosure":"","notes":"","feedingIntervalDays":7,"remindersEnabled":true}"""

        assertThrows(SerializationException::class.java) {
            BackupJson.decodeFromString(SnakeRow.serializer(), withoutSex)
        }
    }

    @Test
    fun snakeRow_missingNullableKey_failsDecodeLoudly() {
        // Absent key is not the same as an explicit null (ADR-0002): the key
        // must be present even when the value is null.
        val withoutBirthDate =
            """{"id":1,"name":"N","species":"","morph":"","sex":"Unknown","acquisitionDate":null,"enclosure":"","notes":"","feedingIntervalDays":7,"remindersEnabled":true}"""

        assertThrows(SerializationException::class.java) {
            BackupJson.decodeFromString(SnakeRow.serializer(), withoutBirthDate)
        }
    }

    @Test
    fun feedingRow_missingFormerlyDefaultedKey_failsDecodeLoudly() {
        val withoutAccepted =
            """{"id":1,"snakeId":2,"date":100,"foodType":"Mouse","foodSize":"","assist":false,"notes":"","foodStockItemId":null}"""

        assertThrows(SerializationException::class.java) {
            BackupJson.decodeFromString(FeedingRow.serializer(), withoutAccepted)
        }
    }

    @Test
    fun shedRow_missingFormerlyDefaultedKey_failsDecodeLoudly() {
        val withoutComplete = """{"id":1,"snakeId":2,"date":100,"notes":""}"""

        assertThrows(SerializationException::class.java) {
            BackupJson.decodeFromString(ShedRow.serializer(), withoutComplete)
        }
    }

    @Test
    fun weightRow_missingFormerlyDefaultedKey_failsDecodeLoudly() {
        val withoutNotes = """{"id":1,"snakeId":2,"date":100,"grams":10.5}"""

        assertThrows(SerializationException::class.java) {
            BackupJson.decodeFromString(WeightRow.serializer(), withoutNotes)
        }
    }

    @Test
    fun foodStockRow_missingFormerlyDefaultedKey_failsDecodeLoudly() {
        val withoutLowStockThreshold =
            """{"id":1,"name":"M","foodType":"Mouse","size":"","quantity":0,"notes":""}"""

        assertThrows(SerializationException::class.java) {
            BackupJson.decodeFromString(FoodStockRow.serializer(), withoutLowStockThreshold)
        }
    }

    // exportedAt has exactly one shape: ISO-8601 UTC always carrying seconds,
    // so #26 can validate a single pattern.

    @Test
    fun formatExportedAt_emitsIsoInstantWithSeconds() {
        assertEquals("2026-04-01T10:15:30Z", formatExportedAt(Instant.parse("2026-04-01T10:15:30Z")))
    }

    @Test
    fun formatExportedAt_zeroSeconds_stillEmitsSeconds() {
        // Instant.toString() drops the seconds field when seconds and nanos
        // are zero, which would give ~1 in 60 exports a different shape.
        assertEquals("2026-04-01T10:15:00Z", formatExportedAt(Instant.parse("2026-04-01T10:15:00Z")))
    }
}
