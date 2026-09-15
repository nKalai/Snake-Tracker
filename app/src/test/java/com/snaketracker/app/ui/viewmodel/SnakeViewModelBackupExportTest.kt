package com.snaketracker.app.ui.viewmodel

import android.net.TestUri
import android.net.Uri
import com.snaketracker.app.data.backup.BackupExportGateway
import com.snaketracker.app.data.backup.BackupJsonSource
import com.snaketracker.app.ui.model.BackupExportState
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

    private class RecordingGateway(private val displayName: String) : BackupExportGateway {
        var writtenJson: String? = null
        var writtenTo: Uri? = null

        override suspend fun save(destination: Uri, json: String): String {
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
    fun `exportBackup gateway failure exposes the write reason`() = runOnViewModelMain {
        val reason = "The chosen location could not be opened for writing."
        val viewModel = viewModelWith(
            object : BackupExportGateway {
                override suspend fun save(destination: Uri, json: String): String = throw IOException(reason)
            }
        )

        viewModel.exportBackup(TestUri)
        advanceUntilIdle()

        assertEquals(BackupExportState.Failure(reason), backupExportStateOf(viewModel))
    }

    @Test
    fun `exportBackup source failure exposes the export reason`() = runOnViewModelMain {
        val reason = "Database snapshot read failed."
        val viewModel = SnakeViewModel(
            repository = backupExportTestRepository(),
            backupJsonSource = object : BackupJsonSource {
                override suspend fun exportAll(): String = throw IllegalStateException(reason)
            },
            backupExportGateway = RecordingGateway(savedFileName)
        )

        viewModel.exportBackup(TestUri)
        advanceUntilIdle()

        assertEquals(BackupExportState.Failure(reason), backupExportStateOf(viewModel))
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

    private fun viewModelWith(gateway: BackupExportGateway) = SnakeViewModel(
        repository = backupExportTestRepository(),
        backupJsonSource = FakeJsonSource(backupJson),
        backupExportGateway = gateway
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
