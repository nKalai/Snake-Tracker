package com.snaketracker.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.snaketracker.app.data.AppDatabase
import com.snaketracker.app.data.Repository
import com.snaketracker.app.data.backup.BackupExportGateway
import com.snaketracker.app.data.backup.BackupImportGateway
import com.snaketracker.app.data.backup.BackupJsonSink
import com.snaketracker.app.data.backup.BackupJsonSource
import com.snaketracker.app.data.backup.ImportSummary
import com.snaketracker.app.ui.screens.SettingsScreen
import com.snaketracker.app.ui.theme.SnakeTrackerTheme
import com.snaketracker.app.ui.viewmodel.SnakeViewModel
import com.snaketracker.app.testing.RecordingActivityResultActivity
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #28 WB1, production default: every other Settings import test replaces
 * [com.snaketracker.app.ui.screens.BackupSourcePicker] with a fake, so this is
 * the only test that pins the real picker — tapping the import row must launch
 * `ACTION_OPEN_DOCUMENT` restricted to JSON (no runtime permissions, no
 * manifest storage declarations).
 */
@RunWith(AndroidJUnit4::class)
class SettingsPickerContractTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<RecordingActivityResultActivity>()

    private lateinit var db: AppDatabase
    private lateinit var viewModel: SnakeViewModel

    @Before
    fun createViewModel() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        viewModel = SnakeViewModel(
            repository = Repository(db),
            backupJsonSource = object : BackupJsonSource {
                override suspend fun exportAll(): String =
                    throw UnsupportedOperationException("picker contract never exports")
            },
            backupJsonSink = object : BackupJsonSink {
                override suspend fun importJson(json: String): ImportSummary =
                    ImportSummary.Success(0, 0, 0, 0, 0)
            },
            backupExportGateway = object : BackupExportGateway {
                override suspend fun save(destination: Uri, json: String): String =
                    throw UnsupportedOperationException("picker contract never writes")
            },
            backupImportGateway = object : BackupImportGateway {
                override suspend fun read(source: Uri): String = ""
            },
            rearmReminders = { }
        )
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    @Test
    fun tappingImportBackupRow_launchesRealOpenDocumentContractRestrictedToJson() {
        // No backupSourcePicker argument: the production default is the unit.
        composeRule.setContent {
            SnakeTrackerTheme {
                SettingsScreen(viewModel = viewModel)
            }
        }

        composeRule.onNodeWithTag("import_backup_row").performClick()

        val launched = composeRule.activity.launchedIntents
        assertEquals("exactly one activity launch per tap", 1, launched.size)
        val intent = launched[0]
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        // The bundled contract (androidx.activity 1.9.x) carries the mime
        // filter as EXTRA_MIME_TYPES over a */* base type — the
        // open-document action by definition hands back openable content
        // URIs, so this extra is the full extent of the JSON restriction
        // the production picker default must pin.
        assertArrayEquals(
            "the picker must offer JSON files only",
            arrayOf("application/json"),
            intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
        )
    }
}
