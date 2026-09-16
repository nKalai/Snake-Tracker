package com.snaketracker.app.testing

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.snaketracker.app.data.AppDatabase
import com.snaketracker.app.data.Repository
import com.snaketracker.app.data.backup.BackupEngine
import com.snaketracker.app.data.backup.BackupExportGateway
import com.snaketracker.app.data.backup.BackupImportGateway
import com.snaketracker.app.data.backup.ImportSummary
import com.snaketracker.app.ui.viewmodel.DefaultBackupCoordinator
import com.snaketracker.app.ui.viewmodel.SnakeViewModel
import java.io.File

/**
 * The one instrumented harness for Settings backup tests (PR #34 review
 * 🟢 "instrumented tests duplicate the port-fake harness"): every suite
 * builds its [SnakeViewModel] here over the production
 * [DefaultBackupCoordinator] with fakes swapped per test. Faking stays
 * honest - the coordinator's mapping and dispatcher behavior run for real,
 * only the engine and the file gateways are stand-ins.
 */
object SettingsBackupHarness {

    const val MINIMAL_BACKUP_JSON = """{"schemaVersion":1,"appVersion":"1.1","data":{}}"""

    fun viewModel(
        db: AppDatabase,
        backup: BackupEngine = UnusedBackupEngine,
        exportGateway: BackupExportGateway = UnusedExportGateway,
        importGateway: BackupImportGateway = UnusedImportGateway,
        onRearm: () -> Unit = {}
    ): SnakeViewModel = SnakeViewModel(
        repository = Repository(db),
        backup = DefaultBackupCoordinator(
            backupEngine = backup,
            exportGateway = exportGateway,
            importGateway = importGateway,
            rearmReminders = { onRearm() }
        )
    )

    /** Engine stand-in: exports [json], returns [result] from every import. */
    fun engineReturning(result: ImportSummary, json: String = MINIMAL_BACKUP_JSON): BackupEngine =
        object : BackupEngine {
            override suspend fun exportAll(): String = json
            override suspend fun importJson(json: String): ImportSummary = result
        }

    /** Export gateway stand-in: [saveWith] decides what a write returns or throws. */
    fun exportGatewaySavingWith(saveWith: suspend (String) -> String): BackupExportGateway =
        object : BackupExportGateway {
            override suspend fun save(destination: Uri, json: String): String = saveWith(json)
        }

    /** Import gateway stand-in: every read returns [json]. */
    fun importGatewayReading(json: String = MINIMAL_BACKUP_JSON): BackupImportGateway =
        object : BackupImportGateway {
            override suspend fun read(source: Uri): String = json
        }

    /** Import gateway stand-in: every read throws [failure]. */
    fun importGatewayFailingWith(failure: Exception): BackupImportGateway =
        object : BackupImportGateway {
            override suspend fun read(source: Uri): String = throw failure
        }

    /** Stands in for a SAF result URI; the fakes never dereference it. */
    fun pickedUri(fileName: String = "backup.json"): Uri = Uri.fromFile(
        File(ApplicationProvider.getApplicationContext<Context>().cacheDir, fileName)
    )

    // Halves a suite never reaches: failing stubs keep the shared
    // constructor honest.

    val UnusedBackupEngine: BackupEngine = object : BackupEngine {
        override suspend fun exportAll(): String =
            throw UnsupportedOperationException("this test never exports")

        override suspend fun importJson(json: String): ImportSummary =
            throw UnsupportedOperationException("this test never imports")
    }

    val UnusedExportGateway: BackupExportGateway = object : BackupExportGateway {
        override suspend fun save(destination: Uri, json: String): String =
            throw UnsupportedOperationException("this test never writes")
    }

    val UnusedImportGateway: BackupImportGateway = object : BackupImportGateway {
        override suspend fun read(source: Uri): String =
            throw UnsupportedOperationException("this test never reads")
    }
}
