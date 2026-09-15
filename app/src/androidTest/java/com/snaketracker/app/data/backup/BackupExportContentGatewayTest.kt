package com.snaketracker.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [BackupExportContentGateway] against the real [android.content.ContentResolver]:
 * the JSON must land in the picked document byte-for-byte and the returned
 * name must be the file that was actually saved (issue #26 WB2/WB3).
 *
 * A `file://` destination stands in for a plain SAF write: it goes through
 * the same `ContentResolver.openOutputStream` path and its display-name
 * query has no provider behind it, pinning the last-path-segment fallback.
 * Provider behaviors a scratch file cannot produce - a real DISPLAY_NAME
 * answer, a throwing query, a null stream - are driven through
 * [GatewayTestProvider], because `ContentResolver`'s own query/openOutputStream
 * methods are final and cannot be stubbed by subclassing.
 */
@RunWith(AndroidJUnit4::class)
class BackupExportContentGatewayTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun pickedFile(name: String): File =
        File(context.cacheDir, "picked/$name").apply { parentFile!!.mkdirs(); delete() }

    @Test
    fun save_writesJsonAndReportsSavedFileName() = runBlocking {
        val target = pickedFile("snake-tracker-backup-2026-04-01.json")
        val json = """{"schemaVersion":1,"exportedAt":"2026-04-01T10:15:30Z"}"""

        val savedName = BackupExportContentGateway(context.contentResolver)
            .save(Uri.fromFile(target), json)

        assertEquals("snake-tracker-backup-2026-04-01.json", savedName)
        assertEquals(json, target.readText())
    }

    /** The provider's DISPLAY_NAME wins over the URI's own last segment. */
    @Test
    fun save_reportsProviderDisplayName_whenQuerySucceeds() = runBlocking {
        val destination = GatewayTestProvider.uri("named", "requested-name.json")

        val savedName = BackupExportContentGateway(context.contentResolver)
            .save(destination, "{}")

        // Different from the URI's own name: proves the value came from the
        // query, not from the last-path-segment fallback.
        assertEquals(GatewayTestProvider.PROVIDER_DISPLAY_NAME, savedName)
    }

    /**
     * Regression for the false-failure bug: a provider that throws on the
     * post-write display-name query must not turn a saved file into a failed
     * export - the name falls back to the URI's last segment.
     */
    @Test
    fun save_succeedsAndFallsBackToUriName_whenDisplayNameQueryThrows() = runBlocking {
        val destination = GatewayTestProvider.uri("throws", "query-throws.json")

        val savedName = BackupExportContentGateway(context.contentResolver)
            .save(destination, "{}")

        assertEquals("query-throws.json", savedName)
    }

    @Test
    fun save_unusableDestination_throwsIoFailure() = runBlocking {
        // A directory URI cannot be opened for writing.
        val directoryUri = Uri.fromFile(context.cacheDir)

        val failure = runCatching {
            BackupExportContentGateway(context.contentResolver).save(directoryUri, "{}")
        }.exceptionOrNull()

        assertTrue("expected an IOException, got: $failure", failure is IOException)
    }

    /** The documented null-stream branch: typed, machine-readable, still an IO failure.

     * A provider cannot hand back a null descriptor - it refuses with
     * FileNotFoundException, and the resolver surfaces that as the null
     * stream the gateway must type as DESTINATION_UNOPENABLE. */
    @Test
    fun save_nullStream_throwsTypedUnopenableIoFailure() = runBlocking {
        val destination = GatewayTestProvider.uri("nullstream", "null-stream.json")

        val failure = runCatching {
            BackupExportContentGateway(context.contentResolver).save(destination, "{}")
        }.exceptionOrNull()

        val typed = failure as? BackupExportException
        assertNotNull("expected a BackupExportException, got: $failure", typed)
        assertEquals(BackupExportFailureReason.DESTINATION_UNOPENABLE, typed?.reason)
        assertTrue(typed is IOException)
    }
}
