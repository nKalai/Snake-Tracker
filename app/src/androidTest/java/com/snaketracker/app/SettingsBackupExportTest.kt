package com.snaketracker.app

import android.app.Instrumentation.ActivityMonitor
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.activity.ComponentActivity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.snaketracker.app.data.AppDatabase
import com.snaketracker.app.data.backup.BackupExportException
import com.snaketracker.app.data.backup.BackupExportFailureReason
import com.snaketracker.app.data.backup.ImportSummary
import com.snaketracker.app.testing.SettingsBackupHarness
import com.snaketracker.app.ui.screens.BackupDestinationPicker
import com.snaketracker.app.ui.screens.SettingsScreen
import com.snaketracker.app.ui.theme.SnakeTrackerTheme
import com.snaketracker.app.ui.viewmodel.SnakeViewModel
import android.content.Context
import android.net.Uri
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.concurrent.atomic.AtomicReference
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

    /**
     * WB1 against the production picker: the row must launch a real
     * ACTION_CREATE_DOCUMENT for application/json, prefilled with the
     * date-stamped suggested name. The system picker itself is aborted by
     * the ActivityMonitor; only the launched Intent is observed.
     */
    @Test
    fun exportBackup_launchesCreateDocumentIntentWithJsonTypeAndStampedTitle() {
        val launched = AtomicReference<Intent?>()
        // Catch-all monitor (no filter): Instrumentation then consults
        // onStartActivity for every launch. Matching the action ourselves,
        // we record the picker Intent and return a non-null result to abort
        // the real launch (androidx maps it to a cancelled pick); every
        // other launch passes through untouched (null).
        val monitor = object : ActivityMonitor() {
            override fun onStartActivity(intent: Intent): ActivityResult? {
                if (intent.action == Intent.ACTION_CREATE_DOCUMENT) {
                    launched.set(intent)
                    return ActivityResult(0, null)
                }
                return null
            }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.addMonitor(monitor)
        try {
            viewModel = viewModelExportingTo { "unused" }
            composeRule.setContent {
                SnakeTrackerTheme {
                    // No picker injected: the production SAF contract is under test.
                    SettingsScreen(viewModel = viewModel)
                }
            }

            composeRule.onNodeWithTag("export_backup_row").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) { launched.get() != null }

            val intent = launched.get()!!
            assertEquals("application/json", intent.type)
            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            assertEquals("snake-tracker-backup-$today.json", intent.getStringExtra(Intent.EXTRA_TITLE))
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    /** A cancelled pick (no URI delivered) must leave no dialog behind. */
    @Test
    fun pickerCancellation_showsNoDialog() {
        viewModel = viewModelExportingTo { "unused" }
        // TestPicker with no echo: launch() records the name and "the user" walks away.
        showSettings(TestPicker { })

        composeRule.onNodeWithTag("export_backup_row").performClick()

        composeRule.onNodeWithTag("backup_export_message").assertDoesNotExist()
        composeRule.onNodeWithTag("backup_export_dialog_title").assertDoesNotExist()
    }

    /** WB3: after a write, the dialog names the file the gateway reported saved. */
    @Test
    fun successfulExport_resultDialogNamesTheSavedFile() {
        val savedName = "snake-tracker-backup-2026-04-01.json"
        viewModel = viewModelExportingTo { savedName }
        val picker = TestPicker { name -> viewModel.exportBackup(pickedUri(name)) }
        showSettings(picker)

        composeRule.onNodeWithTag("export_backup_row").performClick()

        // WB3 names the outcome copy contractual: title AND message assert.
        composeRule
            .onNodeWithTag("backup_export_dialog_title")
            .assertTextEquals(composeRule.activity.getString(R.string.backup_export_success_title))
        val message = composeRule.activity.getString(R.string.backup_export_success_message, savedName)
        composeRule.onNodeWithTag("backup_export_message").assertIsDisplayed()
        composeRule.onNodeWithText(message).assertIsDisplayed()

        // Dismissing hides the dialog.
        composeRule.onNodeWithTag("backup_export_dismiss").performClick()
        composeRule.onNodeWithTag("backup_export_message").assertDoesNotExist()
    }

    /** WB3: a typed write failure states its reason, from string resources. */
    @Test
    fun failedExport_resultDialogStatesTheReason() {
        viewModel = viewModelExportingTo {
            throw BackupExportException(
                BackupExportFailureReason.DESTINATION_UNOPENABLE,
                "diagnostic detail that must stay out of the dialog"
            )
        }
        val picker = TestPicker { name -> viewModel.exportBackup(pickedUri(name)) }
        showSettings(picker)

        composeRule.onNodeWithTag("export_backup_row").performClick()

        composeRule
            .onNodeWithTag("backup_export_dialog_title")
            .assertTextEquals(composeRule.activity.getString(R.string.backup_export_failure_title))
        val reason = composeRule.activity.getString(R.string.backup_export_failure_reason_destination)
        val message = composeRule.activity.getString(R.string.backup_export_failure_message, reason)
        composeRule.onNodeWithTag("backup_export_message").assertIsDisplayed()
        composeRule.onNodeWithText(message).assertIsDisplayed()
    }

    /** WB3: an untyped failure shows the generic copy; raw exception text never reaches the user. */
    @Test
    fun untypedExportFailure_showsGenericReasonNotTheRawMessage() {
        val rawMessage = "ENOENT /storage/emulated/0/Download/snake-tracker-backup.json"
        viewModel = viewModelExportingTo { throw IOException(rawMessage) }
        val picker = TestPicker { name -> viewModel.exportBackup(pickedUri(name)) }
        showSettings(picker)

        composeRule.onNodeWithTag("export_backup_row").performClick()

        val reason = composeRule.activity.getString(R.string.backup_export_failure_reason_unknown)
        val message = composeRule.activity.getString(R.string.backup_export_failure_message, reason)
        composeRule.onNodeWithText(message).assertIsDisplayed()
        composeRule.onNodeWithText(rawMessage, substring = true).assertDoesNotExist()
    }

    /**
     * The write-phase reason must warn that the chosen file may now be
     * incomplete — the destination was truncated before the write failed
     * (PR #34 review 🔴), so generic "unexpected error" copy would lie.
     */
    @Test
    fun unwritableExport_resultDialogWarnsTheFileMayBeIncomplete() {
        viewModel = viewModelExportingTo {
            throw BackupExportException(
                BackupExportFailureReason.DESTINATION_UNWRITABLE,
                "diagnostic detail that must stay out of the dialog"
            )
        }
        val picker = TestPicker { name -> viewModel.exportBackup(pickedUri(name)) }
        showSettings(picker)

        composeRule.onNodeWithTag("export_backup_row").performClick()

        val reason = composeRule.activity.getString(R.string.backup_export_failure_reason_unwritable)
        assertTrue("reason copy must name the possibly-incomplete file", reason.contains("incomplete"))
        val message = composeRule.activity.getString(R.string.backup_export_failure_message, reason)
        composeRule.onNodeWithTag("backup_export_message").assertIsDisplayed()
        composeRule.onNodeWithText(message).assertIsDisplayed()
    }

    private fun viewModelExportingTo(saveWith: suspend (String) -> String) = SettingsBackupHarness.viewModel(
        db = db,
        backup = SettingsBackupHarness.engineReturning(
            // The import half is never reached: the import gateway stays unused.
            result = ImportSummary.Success(emptyList()),
            json = """{"schemaVersion":1,"data":{}}"""
        ),
        exportGateway = SettingsBackupHarness.exportGatewaySavingWith(saveWith)
    )

    /** Stands in for a SAF result URI; the fake gateway never dereferences it. */
    private fun pickedUri(fileName: String): Uri = SettingsBackupHarness.pickedUri(fileName)
}
