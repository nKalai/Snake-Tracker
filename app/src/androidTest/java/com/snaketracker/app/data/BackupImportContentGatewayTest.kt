package com.snaketracker.app.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.snaketracker.app.data.backup.BackupImportContentGateway
import java.io.File
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one production class the import slice touches with no JVM seam to
 * stand in for it: [BackupImportContentGateway] reading through the real
 * [android.content.ContentResolver]. The file stands in for a SAF pick with
 * a `file:` URI — same resolver path the picked `content:` URI takes from
 * here on.
 */
@RunWith(AndroidJUnit4::class)
class BackupImportContentGatewayTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val gateway = BackupImportContentGateway(context.contentResolver)

    /** Byte-exact round-trip, including non-ASCII backup content. */
    @Test
    fun pickedBackupFile_readsBackByteExactText() {
        val json = """{"schemaVersion":1,"appVersion":"1.1","data":{"snakes":[{"id":1,"name":"Café 🐍"}]}}"""
        val file = File(context.cacheDir, "gateway-roundtrip.json").apply {
            writeBytes(json.toByteArray(Charsets.UTF_8))
            deleteOnExit()
        }

        val read = runBlocking { gateway.read(Uri.fromFile(file)) }

        assertEquals(json, read)
        assertArrayEquals(
            "read must not rewrite bytes en route",
            file.readBytes(),
            read.toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * A file the resolver cannot open for reading surfaces as [IOException]
     * (here [java.io.FileNotFoundException]) — the contract the ViewModel
     * maps to the "unreadable file" dialog copy. Note the null-stream branch
     * the gateway also covers is unreachable on current platforms:
     * `ContentResolver.openInputStream` is final and throws rather than
     * returning null.
     */
    @Test
    fun missingBackupFile_readThrowsIoException() {
        val missing = File(context.cacheDir, "gateway-absent-${Random.nextLong()}.json")

        assertThrows(IOException::class.java) {
            runBlocking { gateway.read(Uri.fromFile(missing)) }
        }
    }
}
