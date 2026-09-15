package com.snaketracker.app.ui.viewmodel

import android.net.TestUri
import android.net.Uri
import com.snaketracker.app.data.backup.BackupExportException
import com.snaketracker.app.data.backup.BackupExportFailureReason
import com.snaketracker.app.data.backup.BackupExportGateway
import com.snaketracker.app.data.backup.BackupImportGateway
import com.snaketracker.app.data.backup.BackupJsonSink
import com.snaketracker.app.data.backup.BackupJsonSource
import com.snaketracker.app.data.backup.ImportSummary
import com.snaketracker.app.data.fakeTestRepository
import com.snaketracker.app.ui.model.BackupExportState
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Export-backup behavior of [SnakeViewModel] against a fake backup source and
 * a fake export gateway (issue #26): the dialog state the Settings screen
 * renders is fully decided here, so the success/failure contract is a JVM
 * test — the Storage Access Framework picker itself stays in the composable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SnakeViewModelBackupExportTest {

    // Fixed literals: the state must carry what the seams produced, verbatim.
    private val backupJson = """{"schemaVersion":1,"data":{}}"""
    private val savedFileName = "snake-tracker-backup-2026-04-01.json"

    private class FakeJsonSource(private val json: String) : BackupJsonSource {
        override suspend fun exportAll(): String = json
    }

    // Import-side collaborators the export suite never reaches: failing
    // stubs keep the shared ViewModel constructor honest.
    private object UnusedImportSink : BackupJsonSink {
        override suspend fun importJson(json: String): ImportSummary =
            throw UnsupportedOperationException("export tests never import")
    }

    private object UnusedImportGateway : BackupImportGateway {
        override suspend fun read(source: Uri): String =
            throw UnsupportedOperationException("export tests never read")
    }

    private class RecordingGateway(private val displayName: String) : BackupExportGateway {
        var writtenJson: String? = null
        var writtenTo: Uri? = null
        var saveCalls = 0

        override suspend fun save(destination: Uri, json: String): String {
            saveCalls++
            writtenJson = json
            writtenTo = destination
            return displayName
        }
    }

    /**
     * `viewModelScope` launches on `Dispatchers.Main.immediate`; pin Main to
     * this test's scheduler so [advanceUntilIdle] runs the launched work
     * (relying on runTest's implicit Main swap leaks across tests).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun runOnViewModelMain(testBody: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            testBody()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `exportBackup initial state shows no dialog`() {
        val viewModel = viewModelWith(RecordingGateway(savedFileName))

        assertEquals(null, backupExportStateOf(viewModel))
    }

    @Test
    fun `exportBackup gateway failure exposes the typed destination reason`() = runOnViewModelMain {
        val viewModel = viewModelWith(
            object : BackupExportGateway {
                override suspend fun save(destination: Uri, json: String): String =
                    throw BackupExportException(
                        BackupExportFailureReason.DESTINATION_UNOPENABLE,
                        "The chosen location could not be opened for writing."
                    )
            }
        )

        viewModel.exportBackup(TestUri)
        advanceUntilIdle()

        assertEquals(
            BackupExportState.Failure(BackupExportFailureReason.DESTINATION_UNOPENABLE),
            backupExportStateOf(viewModel)
        )
    }

    @Test
    fun `exportBackup untyped failure reports unknown reason and never leaks the raw message`() = runOnViewModelMain {
        // A raw Room/SQLite-style message with a storage path: exactly what
        // must never reach the dialog (issue #26 WB3).
        val rawMessage = "SQLiteLog: (14) cannot open /data/user/0/com.snaketracker.app/databases/snake.db"
        val viewModel = SnakeViewModel(
            repository = fakeTestRepository(),
            backupJsonSource = object : BackupJsonSource {
                override suspend fun exportAll(): String = throw IllegalStateException(rawMessage)
            },
            backupJsonSink = UnusedImportSink,
            backupExportGateway = RecordingGateway(savedFileName),
            backupImportGateway = UnusedImportGateway,
            rearmReminders = {}
        )

        viewModel.exportBackup(TestUri)
        advanceUntilIdle()

        val state = backupExportStateOf(viewModel)
        assertEquals(BackupExportState.Failure(BackupExportFailureReason.UNKNOWN), state)
        // The reason carries no text at all, so there is nothing to leak.
        assertFalse(state.toString().contains(rawMessage))
    }

    @Test
    fun `exportBackup failure without a message still reports the unknown reason`() = runOnViewModelMain {
        val viewModel = viewModelWith(
            object : BackupExportGateway {
                override suspend fun save(destination: Uri, json: String): String = throw IOException()
            }
        )

        viewModel.exportBackup(TestUri)
        advanceUntilIdle()

        assertEquals(
            BackupExportState.Failure(BackupExportFailureReason.UNKNOWN),
            backupExportStateOf(viewModel)
        )
    }

    @Test
    fun `dismissBackupExport hides the result dialog`() = runOnViewModelMain {
        val viewModel = viewModelWith(RecordingGateway(savedFileName))
        viewModel.exportBackup(TestUri)
        advanceUntilIdle()
        check(backupExportStateOf(viewModel) != null)

        viewModel.dismissBackupExport()

        assertEquals(null, backupExportStateOf(viewModel))
    }

    /**
     * A second tap while an export is in flight must not start a second
     * concurrent export - otherwise the coroutine that finishes last wins
     * the dialog and can overwrite a newer result.
     */
    @Test
    fun `exportBackup second call while in flight does not start a second export`() = runOnViewModelMain {
        val gate = CompletableDeferred<Unit>()
        val gateway = object : BackupExportGateway {
            var calls = 0
            override suspend fun save(destination: Uri, json: String): String {
                calls++
                gate.await()
                return savedFileName
            }
        }
        val viewModel = viewModelWith(gateway)

        viewModel.exportBackup(TestUri)
        runCurrent() // first export reaches the gate and suspends
        viewModel.exportBackup(TestUri) // second tap while in flight
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, gateway.calls)
        assertEquals(
            BackupExportState.Success(savedFileName),
            backupExportStateOf(viewModel)
        )
    }

    /** After one export settles, the next tap may export again. */
    @Test
    fun `exportBackup after a settled export runs again`() = runOnViewModelMain {
        val gateway = RecordingGateway(savedFileName)
        val viewModel = viewModelWith(gateway)

        viewModel.exportBackup(TestUri)
        advanceUntilIdle()
        viewModel.dismissBackupExport()
        viewModel.exportBackup(TestUri)
        advanceUntilIdle()

        assertEquals(2, gateway.saveCalls)
        assertEquals(
            BackupExportState.Success(savedFileName),
            backupExportStateOf(viewModel)
        )
    }

    private fun viewModelWith(gateway: BackupExportGateway) = SnakeViewModel(
        repository = fakeTestRepository(),
        backupJsonSource = FakeJsonSource(backupJson),
        backupJsonSink = UnusedImportSink,
        backupExportGateway = gateway,
        backupImportGateway = UnusedImportGateway,
        rearmReminders = {}
    )

    @Test
    fun `exportBackup success exposes the saved file name`() = runOnViewModelMain {
        val gateway = RecordingGateway(savedFileName)
        val viewModel = viewModelWith(gateway)

        viewModel.exportBackup(TestUri)
        advanceUntilIdle()

        assertEquals(
            BackupExportState.Success(savedFileName),
            backupExportStateOf(viewModel)
        )
        // The JSON produced by the source is what got handed to the picker
        // destination — nothing rewritten on the way through.
        assertEquals(backupJson, gateway.writtenJson)
        // Uri equality is 'not mocked' on the JVM stub jar; the destination
        // must be the exact object the caller handed in.
        assertSame(TestUri, gateway.writtenTo)
    }
}

private fun backupExportStateOf(viewModel: SnakeViewModel): BackupExportState? =
    viewModel.backupExportState.value
