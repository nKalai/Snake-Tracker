package com.snaketracker.app.ui.viewmodel

import android.net.TestUri
import android.net.Uri
import com.snaketracker.app.data.backup.BackupExportFailureReason
import com.snaketracker.app.data.fakeTestRepository
import com.snaketracker.app.ui.model.BackupExportState
import com.snaketracker.app.ui.model.BackupImportState
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
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Export-dialog state management of [SnakeViewModel] against a fake
 * [BackupCoordinator] (issue #26, re-hosted on the consolidated seam from
 * the PR #34 review 🔴): what the Settings screen renders is decided here,
 * and the ViewModel must pass the picked destination through verbatim and
 * surface exactly the state the coordinator returns. The failure-reason
 * mapping itself is pinned at its one home, [BackupCoordinatorTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SnakeViewModelBackupExportTest {

    private companion object {
        const val savedFileName = "snake-tracker-backup-2026-04-01.json"
    }

    /** Coordinator stand-in; [gate] makes an export suspend until released. */
    private class FakeCoordinator(
        var result: BackupExportState = BackupExportState.Success(savedFileName),
        var gate: CompletableDeferred<Unit>? = null
    ) : BackupCoordinator {
        var exportCalls = 0
        var exportedTo: Uri? = null

        override suspend fun exportBackup(destination: Uri): BackupExportState {
            exportCalls += 1
            exportedTo = destination
            gate?.await()
            return result
        }

        override suspend fun importBackup(source: Uri): BackupImportState =
            throw UnsupportedOperationException("export tests never import")
    }

    /**
     * `viewModelScope` launches on `Dispatchers.Main.immediate`; pin Main to
     * this test's scheduler so [advanceUntilIdle] runs the launched work
     * (relying on runTest's implicit Main swap leaks across tests).
     */
    private fun runOnViewModelMain(testBody: suspend TestScope.(FakeCoordinator) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            testBody(FakeCoordinator())
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun viewModelWith(coordinator: FakeCoordinator) =
        SnakeViewModel(repository = fakeTestRepository(), backup = coordinator)

    @Test
    fun `exportBackup initial state shows no dialog`() {
        val viewModel = viewModelWith(FakeCoordinator())

        assertEquals(null, backupExportStateOf(viewModel))
    }

    @Test
    fun `exportBackup success state names the file and carries the exact destination`() = runOnViewModelMain { coordinator ->
        val viewModel = viewModelWith(coordinator)

        viewModel.exportBackup(TestUri)
        advanceUntilIdle()

        assertEquals(BackupExportState.Success(savedFileName), backupExportStateOf(viewModel))
        // Uri equality is 'not mocked' on the JVM stub jar; the destination
        // must be the exact object the caller handed in.
        assertSame(TestUri, coordinator.exportedTo)
    }

    @Test
    fun `exportBackup surfaces exactly the failure state the coordinator returns`() = runOnViewModelMain { coordinator ->
        coordinator.result = BackupExportState.Failure(
            BackupExportFailureReason.DESTINATION_UNWRITABLE
        )
        val viewModel = viewModelWith(coordinator)

        viewModel.exportBackup(TestUri)
        advanceUntilIdle()

        assertEquals(coordinator.result, backupExportStateOf(viewModel))
    }

    /** Hides the export result dialog. */
    @Test
    fun `dismissBackupExport hides the result dialog`() = runOnViewModelMain { coordinator ->
        val viewModel = viewModelWith(coordinator)
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
    fun `exportBackup second call while in flight does not start a second export`() = runOnViewModelMain { coordinator ->
        coordinator.gate = CompletableDeferred()
        val viewModel = viewModelWith(coordinator)

        viewModel.exportBackup(TestUri)
        runCurrent() // first export reaches the gate and suspends
        viewModel.exportBackup(TestUri) // second tap while in flight
        coordinator.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, coordinator.exportCalls)
        assertEquals(BackupExportState.Success(savedFileName), backupExportStateOf(viewModel))
    }

    /** After one export settles, the next tap may export again. */
    @Test
    fun `exportBackup after a settled export runs again`() = runOnViewModelMain { coordinator ->
        val viewModel = viewModelWith(coordinator)

        viewModel.exportBackup(TestUri)
        advanceUntilIdle()
        viewModel.dismissBackupExport()
        viewModel.exportBackup(TestUri)
        advanceUntilIdle()

        assertEquals(2, coordinator.exportCalls)
        assertEquals(BackupExportState.Success(savedFileName), backupExportStateOf(viewModel))
    }
}

private fun backupExportStateOf(viewModel: SnakeViewModel): BackupExportState? =
    viewModel.backupExportState.value
