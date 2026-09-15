package com.snaketracker.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [BackupExportContentGateway] against the real [android.content.ContentResolver]:
 * the JSON must land in the picked document byte-for-byte and the returned
 * name must be the file that was actually saved (issue #26 WB2/WB3).
 *
 * A `file://` destination stands in for a SAF URI: writes go through the
 * same `ContentResolver.openOutputStream` path, and the display-name query
 * has no provider behind it, so it also pins the last-path-segment fallback.
 */
@RunWith(AndroidJUnit4::class)
class BackupExportContentGatewayTest {

    @Test
    fun save_writesJsonAndReportsSavedFileName() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val target = File(context.cacheDir, "picked/snake-tracker-backup-2026-04-01.json")
            .apply { parentFile!!.mkdirs(); delete() }
        val json = """{"schemaVersion":1,"exportedAt":"2026-04-01T10:15:30Z"}"""

        val savedName = BackupExportContentGateway(context.contentResolver)
            .save(Uri.fromFile(target), json)

        assertEquals("snake-tracker-backup-2026-04-01.json", savedName)
        assertEquals(json, target.readText())
    }

    @Test
    fun save_unusableDestination_throwsInsteadOfReturning() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        // A directory URI cannot be opened for writing.
        val directoryUri = Uri.fromFile(context.cacheDir)

        val failure = runCatching {
            BackupExportContentGateway(context.contentResolver).save(directoryUri, "{}")
        }.exceptionOrNull()

        assertEquals(true, failure != null)
    }
}
