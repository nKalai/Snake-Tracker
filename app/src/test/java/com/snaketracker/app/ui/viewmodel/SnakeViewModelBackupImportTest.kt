package com.snaketracker.app.ui.viewmodel

import android.net.TestUri
import android.net.Uri
import com.snaketracker.app.R
import com.snaketracker.app.data.fakeTestRepository
import com.snaketracker.app.ui.model.BackupExportState
import com.snaketracker.app.ui.model.BackupImportState
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
 * Import-dialog state management of [SnakeViewModel] against a fake
 * [BackupCoordinator] (issue #28, re-hosted on the consolidated seam from
 * the PR #34 review 🔴): the confirm-before-import gate, the surfaced
 * result state, and the rule that a cancelled pick never reaches the
 * coordinator. Reading, engine dispatch, reason mapping and the re-arm
 * live at the coordinator now - pinned at their one home,
 * [BackupCoordinatorTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SnakeViewModelBackupImportTest {

    /** Coordinator stand-in recording every confirmed import. */
    private class FakeCoordinator(
        var result: BackupImportState = BackupImportState.Success(
            BackupImportState.Counts(2, 5, 1, 3, 4)
        )
    ) : BackupCoordinator {
        var importCalls = 0
        var importedFrom: Uri? = null

        override suspend fun exportBackup(destination: Uri): BackupExportState =
            throw UnsupportedOperationException("import tests never export")

        override suspend fun importBackup(source: Uri): BackupImportState {
            importCalls += 1
            importedFrom = source
            return result
        }
    }

    /**
     * `viewModelScope` launches on `Dispatchers.Main.immediate`; pin Main to
     * this test's scheduler so [advanceUntilIdle] runs the launched work.
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

    private fun backupImportStateOf(viewModel: SnakeViewModel): BackupImportState? =
        viewModel.backupImportState.value

    // WB2 — the confirmation gate: picking a file never runs the import.

    @Test
    fun `requestBackupImport shows the confirm dialog without importing`() = runOnViewModelMain { coordinator ->
        val viewModel = viewModelWith(coordinator)

        viewModel.requestBackupImport(TestUri)

        // Uri equality is 'not mocked' on the JVM stub jar; identity is the contract.
        assertSame(TestUri, viewModel.pendingImportUri.value)
        assertEquals(0, coordinator.importCalls)
        assertEquals(null, backupImportStateOf(viewModel))
    }

    @Test
    fun `dismissing the confirm dialog imports nothing`() = runOnViewModelMain { coordinator ->
        val viewModel = viewModelWith(coordinator)
        viewModel.requestBackupImport(TestUri)

        viewModel.cancelBackupImport()
        viewModel.confirmBackupImport()
        advanceUntilIdle()

        assertEquals(null, viewModel.pendingImportUri.value)
        assertEquals(0, coordinator.importCalls)
        assertEquals(null, backupImportStateOf(viewModel))
    }

    @Test
    fun `confirming without a pending pick imports nothing`() = runOnViewModelMain { coordinator ->
        val viewModel = viewModelWith(coordinator)

        viewModel.confirmBackupImport()
        advanceUntilIdle()

        assertEquals(0, coordinator.importCalls)
        assertEquals(null, backupImportStateOf(viewModel))
    }

    // WB3 — a confirmed import hands the exact picked file to the
    // coordinator exactly once and shows what comes back.

    @Test
    fun `confirmed import runs the coordinator once on the exact pick and surfaces its state`() = runOnViewModelMain { coordinator ->
        val viewModel = viewModelWith(coordinator)
        viewModel.requestBackupImport(TestUri)

        viewModel.confirmBackupImport()
        advanceUntilIdle()

        assertEquals(1, coordinator.importCalls)
        assertSame(TestUri, coordinator.importedFrom)
        assertEquals(coordinator.result, backupImportStateOf(viewModel))
        // The gate closes: the confirm dialog is gone once the import runs.
        assertEquals(null, viewModel.pendingImportUri.value)
    }

    @Test
    fun `a failure state from the coordinator is surfaced unchanged`() = runOnViewModelMain { coordinator ->
        coordinator.result = BackupImportState.Failure(R.string.backup_import_failure_database)
        val viewModel = viewModelWith(coordinator)
        viewModel.requestBackupImport(TestUri)

        viewModel.confirmBackupImport()
        advanceUntilIdle()

        assertEquals(
            BackupImportState.Failure(R.string.backup_import_failure_database),
            backupImportStateOf(viewModel)
        )
    }

    @Test
    fun `dismissBackupImport hides the result dialog`() = runOnViewModelMain { coordinator ->
        val viewModel = viewModelWith(coordinator)
        viewModel.requestBackupImport(TestUri)
        viewModel.confirmBackupImport()
        advanceUntilIdle()
        check(backupImportStateOf(viewModel) != null)

        viewModel.dismissBackupImport()

        assertEquals(null, backupImportStateOf(viewModel))
    }
}
