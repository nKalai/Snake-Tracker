package com.snaketracker.app.ui.viewmodel

import android.net.TestUri
import android.net.Uri
import com.snaketracker.app.R
import com.snaketracker.app.data.backup.BackupImportGateway
import com.snaketracker.app.data.backup.BackupJsonSink
import com.snaketracker.app.data.backup.BackupTable
import com.snaketracker.app.data.backup.ImportFailure
import com.snaketracker.app.data.backup.ImportSummary
import com.snaketracker.app.ui.model.BackupImportState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Import-backup behavior of [SnakeViewModel] against a fake file gateway and
 * a fake backup sink (issue #28): the confirm-before-import gate, the result
 * dialog state the Settings screen renders, and the reminder re-arm trigger
 * are all decided here, so the whole contract is a JVM test — the Storage
 * Access Framework picker and the alarm itself stay outside this seam.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SnakeViewModelBackupImportTest {

    // Fixed literals: the state must carry what the seams produced, verbatim.
    private val backupJson = """{"schemaVersion":1,"appVersion":"1.1","data":{}}"""
    private val counts = ImportSummary.Success(snakes = 2, feedings = 5, sheds = 1, weights = 3, foodStock = 4)

    /** Gateway stand-in; [failWith] makes every read fail (unreadable file). */
    private class FakeGateway(private val json: String, private val failWith: Exception? = null) : BackupImportGateway {
        var readCalls = 0
        var readFrom: Uri? = null

        override suspend fun read(source: Uri): String {
            readCalls += 1
            readFrom = source
            failWith?.let { throw it }
            return json
        }
    }

    /** Sink stand-in returning [result]; [failWith] models a database-level error. */
    private class FakeSink(
        private val result: ImportSummary,
        private val failWith: Exception? = null
    ) : BackupJsonSink {
        var importCalls = 0
        var importedJson: String? = null

        override suspend fun importJson(json: String): ImportSummary {
            importCalls += 1
            importedJson = json
            failWith?.let { throw it }
            return result
        }
    }

    private class RecordingRearm {
        var calls = 0
        val hook: suspend () -> Unit = { calls += 1 }
    }

    /**
     * `viewModelScope` launches on `Dispatchers.Main.immediate`; pin Main to
     * this test's scheduler so [advanceUntilIdle] runs the launched work
     * (relying on runTest's implicit Main swap leaks across tests).
     */
    private fun runOnViewModelMain(testBody: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            testBody()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun viewModelWith(
        gateway: BackupImportGateway,
        sink: BackupJsonSink,
        rearm: RecordingRearm = RecordingRearm()
    ) = SnakeViewModel(
        repository = backupImportTestRepository(),
        backupJsonSink = sink,
        backupImportGateway = gateway,
        rearmReminders = rearm.hook
    )

    // WB2 — the confirmation gate: picking a file never runs the engine.

    @Test
    fun `requestBackupImport shows the confirm dialog without reading or importing`() {
        val gateway = FakeGateway(backupJson)
        val sink = FakeSink(counts)
        val viewModel = viewModelWith(gateway, sink)

        viewModel.requestBackupImport(TestUri)

        // Uri equality is 'not mocked' on the JVM stub jar; identity is the contract.
        assertSame(TestUri, viewModel.pendingImportUri.value)
        assertEquals(0, gateway.readCalls)
        assertEquals(0, sink.importCalls)
        assertEquals(null, backupImportStateOf(viewModel))
    }

    @Test
    fun `dismissing the confirm dialog leaves the engine untouched`() = runOnViewModelMain {
        val gateway = FakeGateway(backupJson)
        val sink = FakeSink(counts)
        val rearm = RecordingRearm()
        val viewModel = viewModelWith(gateway, sink, rearm)
        viewModel.requestBackupImport(TestUri)

        viewModel.cancelBackupImport()
        viewModel.confirmBackupImport()
        advanceUntilIdle()

        assertEquals(null, viewModel.pendingImportUri.value)
        assertEquals(0, gateway.readCalls)
        assertEquals(0, sink.importCalls)
        assertEquals(0, rearm.calls)
        assertEquals(null, backupImportStateOf(viewModel))
    }

    @Test
    fun `confirming without a pending pick imports nothing`() = runOnViewModelMain {
        val gateway = FakeGateway(backupJson)
        val sink = FakeSink(counts)
        val viewModel = viewModelWith(gateway, sink)

        viewModel.confirmBackupImport()
        advanceUntilIdle()

        assertEquals(0, gateway.readCalls)
        assertEquals(0, sink.importCalls)
        assertEquals(null, backupImportStateOf(viewModel))
    }

    // WB3 + WB5 — a confirmed import reads the picked file, hands its bytes
    // to the engine, and re-arms the reminder exactly once.

    @Test
    fun `confirmed import restores the picked file and carries the per-table counts`() = runOnViewModelMain {
        val gateway = FakeGateway(backupJson)
        val sink = FakeSink(counts)
        val rearm = RecordingRearm()
        val viewModel = viewModelWith(gateway, sink, rearm)
        viewModel.requestBackupImport(TestUri)

        viewModel.confirmBackupImport()
        advanceUntilIdle()

        assertEquals(BackupImportState.Success(counts), backupImportStateOf(viewModel))
        // The exact picked Uri is what gets read, and the exact bytes read
        // are what get handed to the engine — nothing rewritten en route.
        assertSame(TestUri, gateway.readFrom)
        assertEquals(backupJson, sink.importedJson)
        // The gate closes: the confirm dialog is gone once the import runs.
        assertEquals(null, viewModel.pendingImportUri.value)
        // WB5: the re-arm seam fires exactly once on success.
        assertEquals(1, rearm.calls)
    }

    @Test
    fun `dismissBackupImport hides the result dialog`() = runOnViewModelMain {
        val viewModel = viewModelWith(FakeGateway(backupJson), FakeSink(counts))
        viewModel.requestBackupImport(TestUri)
        viewModel.confirmBackupImport()
        advanceUntilIdle()
        check(backupImportStateOf(viewModel) != null)

        viewModel.dismissBackupImport()

        assertEquals(null, backupImportStateOf(viewModel))
    }

    // WB4 — every failure kind maps to its own reason message, and none of
    // them re-arms the reminder.

    @Test
    fun `each engine rejection maps to its own failure message`() = runOnViewModelMain {
        val rejections = listOf(
            ImportFailure.MalformedJson to R.string.backup_import_failure_not_backup,
            ImportFailure.UnsupportedSchemaVersion(found = 2) to R.string.backup_import_failure_newer_version,
            ImportFailure.OrphanChildRow(BackupTable.FEEDING, rowId = 7, snakeId = 99) to
                R.string.backup_import_failure_orphan_rows,
            ImportFailure.InvalidRowId(BackupTable.SNAKE, rowId = 0) to R.string.backup_import_failure_invalid_rows
        )

        for ((reason, expectedMessage) in rejections) {
            val rearm = RecordingRearm()
            val viewModel = viewModelWith(
                FakeGateway(backupJson),
                FakeSink(ImportSummary.Failure(reason)),
                rearm
            )
            viewModel.requestBackupImport(TestUri)

            viewModel.confirmBackupImport()
            advanceUntilIdle()

            assertEquals(BackupImportState.Failure(expectedMessage), backupImportStateOf(viewModel))
            assertEquals(0, rearm.calls)
        }
    }

    @Test
    fun `an unreadable file maps to its own message and never reaches the engine`() = runOnViewModelMain {
        val gateway = FakeGateway(backupJson, failWith = IOException("could not open"))
        val sink = FakeSink(counts)
        val rearm = RecordingRearm()
        val viewModel = viewModelWith(gateway, sink, rearm)
        viewModel.requestBackupImport(TestUri)

        viewModel.confirmBackupImport()
        advanceUntilIdle()

        assertEquals(
            BackupImportState.Failure(R.string.backup_import_failure_unreadable),
            backupImportStateOf(viewModel)
        )
        assertEquals(0, sink.importCalls)
        assertEquals(0, rearm.calls)
    }

    @Test
    fun `a database-level import error maps to its own message without re-arming`() = runOnViewModelMain {
        val sink = FakeSink(counts, failWith = IllegalStateException("SQLITE_BUSY"))
        val rearm = RecordingRearm()
        val viewModel = viewModelWith(FakeGateway(backupJson), sink, rearm)
        viewModel.requestBackupImport(TestUri)

        viewModel.confirmBackupImport()
        advanceUntilIdle()

        assertEquals(
            BackupImportState.Failure(R.string.backup_import_failure_database),
            backupImportStateOf(viewModel)
        )
        assertEquals(0, rearm.calls)
    }

    @Test
    fun `failureMessages_areDistinctAndReal`() {
        val messages = setOf(
            R.string.backup_import_failure_not_backup,
            R.string.backup_import_failure_newer_version,
            R.string.backup_import_failure_orphan_rows,
            R.string.backup_import_failure_invalid_rows,
            R.string.backup_import_failure_unreadable,
            R.string.backup_import_failure_database
        )
        assertNotEquals(0, R.string.backup_import_failure_not_backup)
        assertEquals(6, messages.size)
    }
}
