package com.snaketracker.app.data

import com.snaketracker.app.data.backup.BackupData
import com.snaketracker.app.data.backup.BackupDocument
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locked backup schema (issue #25 WB2):
 *
 * { "schemaVersion": 1, "appVersion": "1.1", "exportedAt": "<ISO-8601>",
 *   "data": { "snakes": [], "feedings": [], "sheds": [], "weights": [], "foodStock": [] } }
 */
class BackupDocumentTest {

    private val json = Json { encodeDefaults = true }

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

        assertEquals(emptyEnvelopeJson, json.encodeToString(BackupDocument.serializer(), document))
    }

    @Test
    fun envelope_decodesLockedSchemaJson() {
        val document = json.decodeFromString(BackupDocument.serializer(), emptyEnvelopeJson)

        assertEquals(1, document.schemaVersion)
        assertEquals("1.1", document.appVersion)
        assertEquals("2026-04-01T10:15:30Z", document.exportedAt)
        assertEquals(BackupData(), document.data)
    }

    @Test
    fun schemaVersion_isLockedAtOne() {
        assertEquals(1, BackupDocument.SCHEMA_VERSION)
    }
}
