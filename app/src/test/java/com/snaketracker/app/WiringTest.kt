package com.snaketracker.app

import android.net.TestUri
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.snaketracker.app.data.backup.BackupEngine
import com.snaketracker.app.data.backup.BackupExportGateway
import com.snaketracker.app.data.backup.BackupImportGateway
import com.snaketracker.app.data.backup.BackupTable
import com.snaketracker.app.data.backup.ImportSummary
import com.snaketracker.app.data.backup.TableCount
import com.snaketracker.app.data.fakeTestRepository
import com.snaketracker.app.ui.model.BackupExportState
import com.snaketracker.app.ui.model.BackupImportState
import com.snaketracker.app.ui.viewmodel.SnakeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The production wiring (PR #34 review, suggested edge-case test 4): the
 * functions in Wiring.kt are the feature's composition root, so they are
 * tested exactly as [SnakeTrackerApp] calls them - default dispatchers and
 * all. This pins that one [BackupEngine] instance (production:
 * `BackupRepository`) serves both backup directions, the gateways are the
 * ones handed in, and the re-arm hook fires on a successful import - the
 * wiring the Settings tests all fake away.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WiringTest {

    private companion object {
        // Static so the nested fakes can use them.
        const val savedFileName = "snake-tracker-backup-2026-04-01.json"
        const val backupJson = """{"schemaVersion":1,"appVersion":"1.1","data":{}}"""
    }

    /** One object recording both halves - identity is what's under test. */
    private class RecordingEngine : BackupEngine {
        var exportCalls = 0
        var importCalls = 0

        override suspend fun exportAll(): String {
            exportCalls += 1
            return backupJson
        }

        override suspend fun importJson(json: String): ImportSummary {
            importCalls += 1
            assertEquals(backupJson, json)
            return ImportSummary.Success(
                listOf(TableCount(BackupTable.SNAKE, 1), TableCount(BackupTable.FEEDING, 2))
            )
        }
    }

    private class RecordingExportGateway : BackupExportGateway {
        var savedJson: String? = null
        override suspend fun save(destination: Uri, json: String): String {
            savedJson = json
            return savedFileName
        }
    }

    private class RecordingImportGateway : BackupImportGateway {
        var readCalls = 0
        override suspend fun read(source: Uri): String {
            readCalls += 1
            return backupJson
        }
    }

    @Test
    fun `the factory-built view model runs both backup directions through the single engine`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        try {
            val engine = RecordingEngine()
            val exportGateway = RecordingExportGateway()
            val importGateway = RecordingImportGateway()
            var rearmCalls = 0

            // Exactly SnakeTrackerApp's call shape - default dispatchers.
            val coordinator = createBackupCoordinator(
                backupEngine = engine,
                exportGateway = exportGateway,
                importGateway = importGateway,
                rearmReminders = { rearmCalls += 1 }
            )
            val factory = createViewModelFactory(fakeTestRepository(), coordinator)
            val viewModel = factory.create(SnakeViewModel::class.java)

            viewModel.exportBackup(TestUri)
            advanceUntilIdle()
            assertEquals(BackupExportState.Success(savedFileName), viewModel.backupExportState.value)
            assertEquals(1, engine.exportCalls)
            assertEquals(backupJson, exportGateway.savedJson)

            viewModel.requestBackupImport(TestUri)
            viewModel.confirmBackupImport()
            // The production wiring uses the real Dispatchers.IO inside the
            // coordinator, so the resumed result arrives from another
            // thread: wait for it (virtual-time delay) instead of assuming
            // one scheduler drain.
            withTimeout(10_000) {
                while (viewModel.backupImportState.value == null) {
                    delay(5)
                }
            }
            // The success dialog carries the engine's counts...
            assertEquals(true, viewModel.backupImportState.value is BackupImportState.Success)
            // ...and the re-arm hook - production's ReminderArming.reschedule
            // seam (its own behavior pinned in ReminderArmingTest) - fired.
            assertEquals(1, rearmCalls)

            // Both halves ran on the one engine object production wires in.
            assertEquals(1, engine.exportCalls)
            assertEquals(1, engine.importCalls)
            assertEquals(1, importGateway.readCalls)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `the factory creates only the SnakeViewModel`() {
        val factory = createViewModelFactory(
            fakeTestRepository(),
            createBackupCoordinator(RecordingEngine(), RecordingExportGateway(), RecordingImportGateway()) {}
        )

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(OtherViewModel::class.java)
        }
    }

    private class OtherViewModel : ViewModel()
}
