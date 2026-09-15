package com.snaketracker.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.activity.ComponentActivity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.snaketracker.app.data.AppDatabase
import com.snaketracker.app.data.Repository
import com.snaketracker.app.data.backup.BackupExportGateway
import com.snaketracker.app.data.backup.BackupJsonSource
import com.snaketracker.app.ui.screens.BackupDestinationPicker
import com.snaketracker.app.ui.screens.SettingsScreen
import com.snaketracker.app.ui.theme.SnakeTrackerTheme
import com.snaketracker.app.ui.viewmodel.SnakeViewModel
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Settings export-backup UI (issue #26) driven through [SettingsScreen]'s
 * public seam: a fake [BackupDestinationPicker] plays the SAF picker (it
 * records the suggested file name and can echo a picked URI straight back
 * into the ViewModel), and fake backup seams decide the dialog outcome —
 * so the success/failure dialogs render without a real system document UI.
 *
 * The runtime permission prompt and the one-time exact-alarm prompt are
 * suppressed up front via [uiSuppressionChain]; assertions target stable
 * testTags plus the string resources the dialog is contractually required
 * to show.
 */
@RunWith(AndroidJUnit4::class)
class SettingsBackupExportTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rule: TestRule = uiSuppressionChain.around(composeRule)

    private lateinit var db: AppDatabase
    private lateinit var viewModel: SnakeViewModel

    @Before
    fun createViewModel() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    /** Picker stand-in; [echoPickedFile] makes "launch" immediately act like a completed pick. */
    private class TestPicker(private val echoPickedFile: (String) -> Unit) : BackupDestinationPicker {
        var suggestedFileName: String? = null
        override fun launch(suggestedFileName: String) {
            this.suggestedFileName = suggestedFileName
            echoPickedFile(suggestedFileName)
        }
    }

    private fun showSettings(picker: BackupDestinationPicker) {
        composeRule.setContent {
            SnakeTrackerTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    backupDestinationPicker = picker
                )
            }
        }
    }

    @Test
    fun exportBackupRow_isPresentOnSettings() {
        viewModel = viewModelExportingTo { "unused" }
        showSettings(picker = BackupDestinationPicker { })

        composeRule.onNodeWithTag("export_backup_row").assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.settings_export_backup))
            .assertIsDisplayed()
    }

    /** WB1: tapping the row launches the picker with the date-stamped suggested name. */
    @Test
    fun tappingExportBackup_launchesPickerWithDateStampedFileName() {
        viewModel = viewModelExportingTo { "unused" }
        val picker = TestPicker { }
        showSettings(picker)

        composeRule.onNodeWithTag("export_backup_row").performClick()

        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        assertEquals("snake-tracker-backup-$today.json", picker.suggestedFileName)
    }

    /** WB3: after a write, the dialog names the file the gateway reported saved. */
    @Test
    fun successfulExport_resultDialogNamesTheSavedFile() {
        val savedName = "snake-tracker-backup-2026-04-01.json"
        viewModel = viewModelExportingTo { savedName }
        val picker = TestPicker { name -> viewModel.exportBackup(pickedUri(name)) }
        showSettings(picker)

        composeRule.onNodeWithTag("export_backup_row").performClick()

        val message = composeRule.activity.getString(R.string.backup_export_success_message, savedName)
        composeRule.onNodeWithTag("backup_export_message").assertIsDisplayed()
        composeRule.onNodeWithText(message).assertIsDisplayed()

        // Dismissing hides the dialog.
        composeRule.onNodeWithTag("backup_export_dismiss").performClick()
        composeRule.onNodeWithTag("backup_export_message").assertDoesNotExist()
    }

    /** WB3: a write error states its reason in the dialog. */
    @Test
    fun failedExport_resultDialogStatesTheReason() {
        val reason = "The chosen location could not be opened for writing."
        viewModel = viewModelExportingTo { throw IOException(reason) }
        val picker = TestPicker { name -> viewModel.exportBackup(pickedUri(name)) }
        showSettings(picker)

        composeRule.onNodeWithTag("export_backup_row").performClick()

        val message = composeRule.activity.getString(R.string.backup_export_failure_message, reason)
        composeRule.onNodeWithTag("backup_export_message").assertIsDisplayed()
        composeRule.onNodeWithText(message).assertIsDisplayed()
    }

    private fun viewModelExportingTo(saveWith: suspend (String) -> String) = SnakeViewModel(
        repository = Repository(db),
        backupJsonSource = object : BackupJsonSource {
            override suspend fun exportAll(): String = """{"schemaVersion":1,"data":{}}"""
        },
        backupExportGateway = object : BackupExportGateway {
            override suspend fun save(destination: Uri, json: String): String = saveWith(json)
        }
    )

    /** Stands in for a SAF result URI; the fake gateway never dereferences it. */
    private fun pickedUri(fileName: String): Uri =
        Uri.fromFile(File(ApplicationProvider.getApplicationContext<Context>().cacheDir, fileName))
}
