package com.snaketracker.app.ui.viewmodel

import android.net.TestUri
import android.net.Uri
import com.snaketracker.app.R
import com.snaketracker.app.data.backup.BackupEngine
import com.snaketracker.app.data.backup.BackupExportException
import com.snaketracker.app.data.backup.BackupExportFailureReason
import com.snaketracker.app.data.backup.BackupExportGateway
import com.snaketracker.app.data.backup.BackupImportGateway
import com.snaketracker.app.data.backup.BackupTable
import com.snaketracker.app.data.backup.ImportFailure
import com.snaketracker.app.data.backup.ImportSummary
import com.snaketracker.app.ui.model.BackupExportState
import com.snaketracker.app.ui.model.BackupImportState
import java.io.IOException
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one backup collaborator behind the ViewModel (PR #34 review 🔴): every
 * backup dialog state is decided here, against fake engine and file
 * gateways, so the whole success/failure/re-arm contract is a JVM test.
 *
 * [DefaultBackupCoordinator.logFailure] is the injected logger seam (PR #34
 * review 🟡): the suite records every logged failure and production logging
 * never reaches `android.util.Log` from a JVM test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupCoordinatorTest {

    private companion object {
        // Static so the nested fakes can use them as defaults.
        val backupJson = """{"schemaVersion":1,"appVersion":"1.1","data":{}}"""
        val counts = ImportSummary.Success(snakes = 2, feedings = 5, sheds = 1, weights = 3, foodStock = 4)
        const val savedFileName = "snake-tracker-backup-2026-04-01.json"
    }

    // Uri.parse is 'not mocked' on the JVM stub jar (returns null); TestUri
    // is the stand-in every backup test uses - identity is the contract.
    private val destination: Uri = TestUri
    private val source: Uri = TestUri

    /** Records which engine half ran on which dispatcher, and can fail either. */
    private class FakeEngine(
        private val json: String = backupJson,
        private val result: ImportSummary = ImportSummary.Success(0, 0, 0, 0, 0),
        private val exportFailWith: Exception? = null,
        private val importFailWith: Exception? = null
    ) : BackupEngine {
        var exportCalls = 0
        var importCalls = 0
        var importedJson: String? = null
        var importInterceptor: ContinuationInterceptor? = null

        override suspend fun exportAll(): String {
            exportCalls += 1
            exportFailWith?.let { throw it }
            return json
        }

        override suspend fun importJson(json: String): ImportSummary {
            importCalls += 1
            importedJson = json
            importInterceptor = currentCoroutineContext()[ContinuationInterceptor]
            importFailWith?.let { throw it }
            return result
        }
    }

    private class FakeExportGateway(private val saveWith: suspend (Uri, String) -> String) : BackupExportGateway {
        var calls = 0
        override suspend fun save(destination: Uri, json: String): String {
            calls += 1
            return saveWith(destination, json)
        }
    }

    private class FakeImportGateway(private val readWith: suspend (Uri) -> String) : BackupImportGateway {
        var calls = 0
        var readFrom: Uri? = null
        override suspend fun read(source: Uri): String {
            calls += 1
            readFrom = source
            return readWith(source)
        }
    }

    private class RecordingRearm(private val failWith: Exception? = null) {
        var calls = 0
        var interceptor: ContinuationInterceptor? = null
        val hook: suspend () -> Unit = {
            calls += 1
            interceptor = currentCoroutineContext()[ContinuationInterceptor]
            failWith?.let { throw it }
        }
    }

    private class RecordingLog {
        val entries = mutableListOf<Pair<String, Throwable>>()
        val seam: (String, Throwable) -> Unit = { message, throwable -> entries += message to throwable }
    }

    private class Fixture(
        val io: CoroutineDispatcher,
        val rearmDispatcher: CoroutineDispatcher,
        engine: FakeEngine = FakeEngine(),
        exportGateway: BackupExportGateway = FakeExportGateway { _, _ -> savedFileName },
        importGateway: BackupImportGateway = FakeImportGateway { backupJson },
        rearm: RecordingRearm = RecordingRearm(),
        log: RecordingLog = RecordingLog()
    ) {
        val coordinator = DefaultBackupCoordinator(
            backupEngine = engine,
            exportGateway = exportGateway,
            importGateway = importGateway,
            rearmReminders = rearm.hook,
            ioDispatcher = io,
            rearmDispatcher = rearmDispatcher,
            logFailure = log.seam
        )
        val engine = engine
        val exportGateway = exportGateway
        val importGateway = importGateway
        val rearm = rearm
        val log = log
    }

    /**
     * Runs the body with `Dispatchers.Main` pinned to the test scheduler and
     * the coordinator's two dispatchers as distinct named schedulers, so
     * dispatcher routing is observable.
     */
    private fun runOnMain(
        ioName: String = "backup-io",
        rearmName: String = "backup-rearm",
        testBody: suspend Fixture.(CoroutineDispatcher) -> Unit
    ) = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        try {
            val fixture = Fixture(
                io = StandardTestDispatcher(testScheduler, name = ioName),
                rearmDispatcher = StandardTestDispatcher(testScheduler, name = rearmName)
            )
            fixture.testBody(main)
            advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    // ---- export ----

    @Test
    fun `export returns Success naming the saved file and hands the engine json to the gateway`() = runOnMain {
        var writtenJson: String? = null
        var writtenTo: Uri? = null
        val coordinator = copyWith(
            exportGateway = FakeExportGateway { dest, json ->
                writtenTo = dest
                writtenJson = json
                savedFileName
            }
        )

        val state = coordinator.exportBackup(destination)

        assertEquals(BackupExportState.Success(savedFileName), state)
        // The JSON produced by the engine is what got handed to the picker
        // destination — nothing rewritten on the way through.
        assertEquals(backupJson, writtenJson)
        assertSame(destination, writtenTo)
    }

    @Test
    fun `export typed gateway failure maps to its own reason`() = runOnMain {
        val coordinator = copyWith(
            exportGateway = FakeExportGateway { _, _ ->
                throw BackupExportException(BackupExportFailureReason.DESTINATION_UNOPENABLE, "detail")
            }
        )

        assertEquals(
            BackupExportState.Failure(BackupExportFailureReason.DESTINATION_UNOPENABLE),
            coordinator.exportBackup(destination)
        )
    }

    @Test
    fun `export typed unwritable failure keeps the incomplete-file reason`() = runOnMain {
        val coordinator = copyWith(
            exportGateway = FakeExportGateway { _, _ ->
                throw BackupExportException(BackupExportFailureReason.DESTINATION_UNWRITABLE, "detail")
            }
        )

        assertEquals(
            BackupExportState.Failure(BackupExportFailureReason.DESTINATION_UNWRITABLE),
            coordinator.exportBackup(destination)
        )
    }

    @Test
    fun `export untyped failure maps to UNKNOWN logs once and never leaks the raw message`() = runOnMain {
        val rawMessage = "SQLiteLog: (14) cannot open /data/user/0/com.snaketracker.app/databases/snake.db"
        val log = RecordingLog()
        val coordinator = copyWith(
            engine = FakeEngine(exportFailWith = IllegalStateException(rawMessage)),
            exportGateway = FakeExportGateway { _, _ -> error("never") },
            log = log
        )

        val state = coordinator.exportBackup(destination)

        assertEquals(BackupExportState.Failure(BackupExportFailureReason.UNKNOWN), state)
        // The raw detail reached the log seam - nowhere else: the reason
        // carries no text at all, so there is nothing to leak.
        assertEquals(1, log.entries.size)
        assertTrue(log.entries[0].second.message!!.contains(rawMessage))
        assertFalse(state.toString().contains(rawMessage))
    }

    // ---- import ----

    @Test
    fun `confirmed import returns the engine counts and rearms exactly once`() = runOnMain {
        val engine = FakeEngine(result = counts)
        val coordinator = copyWith(engine = engine)

        val state = coordinator.importBackup(source)

        assertEquals(
            BackupImportState.Success(
                BackupImportState.Counts(snakes = 2, feedings = 5, sheds = 1, weights = 3, foodStock = 4)
            ),
            state
        )
        // The exact bytes the gateway read are what the engine got.
        assertEquals(backupJson, engine.importedJson)
    }

    @Test
    fun `import runs the engine on the io dispatcher never on Main`() = runOnMain { main ->
        val engine = FakeEngine(result = counts)
        val coordinator = copyWith(engine = engine)

        coordinator.importBackup(source)

        assertNotSame(main, engine.importInterceptor)
    }

    @Test
    fun `import re-arms on the rearm dispatcher never on Main`() = runOnMain { main ->
        val rearm = RecordingRearm()
        val coordinator = copyWith(rearm = rearm)

        coordinator.importBackup(source)

        assertEquals(1, rearm.calls)
        assertNotSame(main, rearm.interceptor)
    }

    @Test
    fun `unreadable file maps to its own message and never reaches the engine`() = runOnMain {
        val engine = FakeEngine(result = counts)
        val log = RecordingLog()
        val gateway = FakeImportGateway { throw IOException("could not open") }
        val coordinator = copyWith(engine = engine, importGateway = gateway, log = log)

        val state = coordinator.importBackup(source)

        assertEquals(BackupImportState.Failure(R.string.backup_import_failure_unreadable), state)
        assertEquals(0, engine.importCalls)
        assertEquals(0, rearm.calls)
        assertEquals(1, log.entries.size)
    }

    @Test
    fun `each engine rejection maps to its own failure message without re-arming`() = runOnMain {
        val rejections = listOf(
            ImportFailure.MalformedJson to R.string.backup_import_failure_not_backup,
            ImportFailure.UnsupportedSchemaVersion(found = 2) to R.string.backup_import_failure_newer_version,
            ImportFailure.OrphanChildRow(BackupTable.FEEDING, rowId = 7, snakeId = 99) to
                R.string.backup_import_failure_orphan_rows,
            ImportFailure.InvalidRowId(BackupTable.SNAKE, rowId = 0) to
                R.string.backup_import_failure_invalid_rows
        )
        for ((reason, expectedMessage) in rejections) {
            val rearm = RecordingRearm()
            val coordinator = copyWith(
                engine = FakeEngine(result = ImportSummary.Failure(reason)),
                rearm = rearm
            )

            assertEquals(
                BackupImportState.Failure(expectedMessage),
                coordinator.importBackup(source)
            )
            assertEquals(0, rearm.calls)
        }
    }

    @Test
    fun `database-level import error maps to its own message without re-arming`() = runOnMain {
        val rearm = RecordingRearm()
        val coordinator = copyWith(
            engine = FakeEngine(result = counts, importFailWith = IllegalStateException("SQLITE_BUSY")),
            rearm = rearm
        )

        assertEquals(
            BackupImportState.Failure(R.string.backup_import_failure_database),
            coordinator.importBackup(source)
        )
        assertEquals(0, rearm.calls)
    }

    /**
     * The import itself completed - the receivers (alarm fire, boot,
     * permission re-grant) re-arm on their next event regardless, so a
     * failed re-arm is logged and the Success dialog still shows.
     */
    @Test
    fun `failed reminder re-arm is logged and keeps the success dialog`() = runOnMain {
        val log = RecordingLog()
        val rearm = RecordingRearm(failWith = IllegalStateException("WorkManager unavailable"))
        val coordinator = copyWith(engine = FakeEngine(result = counts), rearm = rearm, log = log)

        val state = coordinator.importBackup(source)

        assertTrue(state is BackupImportState.Success)
        assertEquals(1, rearm.calls)
        assertEquals(1, log.entries.size)
    }

    // ---- cancellation ----

    @Test
    fun `cancellation always propagates instead of becoming a dialog state`() = runOnMain {
        val gateway = FakeImportGateway { throw CancellationException("scope died") }
        val coordinator = copyWith(importGateway = gateway)

        var propagated = false
        try {
            coordinator.importBackup(source)
        } catch (e: CancellationException) {
            propagated = true
        }
        assertTrue("CancellationException must propagate, not show a dialog", propagated)
    }

    // ---- helper: fresh coordinator sharing the fixture's dispatchers ----

    private fun Fixture.copyWith(
        engine: FakeEngine = this.engine,
        exportGateway: BackupExportGateway = this.exportGateway,
        importGateway: BackupImportGateway = this.importGateway,
        rearm: RecordingRearm = this.rearm,
        log: RecordingLog = this.log
    ): DefaultBackupCoordinator = DefaultBackupCoordinator(
        backupEngine = engine,
        exportGateway = exportGateway,
        importGateway = importGateway,
        rearmReminders = rearm.hook,
        ioDispatcher = io,
        rearmDispatcher = rearmDispatcher,
        logFailure = log.seam
    )
}
