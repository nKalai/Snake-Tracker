package com.snaketracker.app

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.snaketracker.app.data.AppDatabase
import com.snaketracker.app.data.Repository
import com.snaketracker.app.data.backup.BackupImportGateway
import com.snaketracker.app.data.backup.BackupJsonSink
import com.snaketracker.app.data.backup.ImportFailure
import com.snaketracker.app.data.backup.ImportSummary
import com.snaketracker.app.ui.screens.BackupSourcePicker
import com.snaketracker.app.ui.screens.SettingsScreen
import com.snaketracker.app.ui.theme.SnakeTrackerTheme
import com.snaketracker.app.ui.viewmodel.SnakeViewModel
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * Settings import-backup UI (issue #28) driven through [SettingsScreen]'s
 * public seam: a fake [BackupSourcePicker] plays the SAF open-document picker
 * (it records the requested mime types and can echo a picked URI straight
 * into the ViewModel), and fake backup seams decide the outcome — so the
 * confirmation gate and both result dialogs render without a real system
 * document UI.
 *
 * The reminder re-arm is asserted at the seam the ViewModel calls — the same
 * suspend entry point production wires to `ReminderArming.refresh` — never by
 * inspecting AlarmManager.
 *
 * Runtime permission prompts and the one-time exact-alarm prompt are
 * suppressed up front via [uiSuppressionChain]; assertions target stable
 * testTags plus the string resources the dialogs are contractually required
 * to show.
 */
@RunWith(AndroidJUnit4::class)
class SettingsBackupImportTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rule: TestRule = uiSuppressionChain.around(composeRule)

    private val backupJson = """{"schemaVersion":1,"appVersion":"1.1","data":{}}"""

    private lateinit var db: AppDatabase
    private lateinit var viewModel: SnakeViewModel

    @Before
    fun createDatabase() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    /** Stand-in harness: counts every seam call the dialogs are forbidden to skip. */
    private inner class Harness(
        gateway: BackupImportGateway,
        sink: BackupJsonSink
    ) {
        val rearmCalls = AtomicInteger(0)
        val viewModel = SnakeViewModel(
            repository = Repository(db),
            backupJsonSink = sink,
            backupImportGateway = gateway,
            rearmReminders = { rearmCalls.incrementAndGet() }
        )
    }

    private fun gatewayReturning(json: String): BackupImportGateway =
        object : BackupImportGateway {
            override suspend fun read(source: Uri): String = json
        }

    private fun sinkReturning(result: ImportSummary): BackupJsonSink =
        object : BackupJsonSink {
            override suspend fun importJson(json: String): ImportSummary = result
        }

    private fun ioFailingGateway(): BackupImportGateway =
        object : BackupImportGateway {
            override suspend fun read(source: Uri): String =
                throw IOException("The chosen file could not be opened for reading.")
        }

    /** Picker stand-in; [echoPickedFile] makes "launch" immediately act like a completed pick. */
    private class TestPicker(private val echoPickedFile: () -> Unit) : BackupSourcePicker {
        var requestedMimeTypes: Array<String>? = null
        override fun launch(mimeTypes: Array<String>) {
            requestedMimeTypes = mimeTypes
            echoPickedFile()
        }
    }

    private fun showSettings(picker: BackupSourcePicker) {
        composeRule.setContent {
            SnakeTrackerTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    backupSourcePicker = picker
                )
            }
        }
    }

    /** Stands in for a SAF result URI; the fakes never dereference it. */
    private fun pickedUri(): Uri =
        Uri.fromFile(File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "backup.json"))

    @Test
    fun importBackupRow_isPresentOnSettings() {
        viewModel = Harness(gatewayReturning(backupJson), sinkReturning(EMPTY_SUCCESS)).viewModel
        showSettings(picker = BackupSourcePicker { })

        composeRule.onNodeWithTag("import_backup_row").assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.settings_import_backup))
            .assertIsDisplayed()
    }

    /** WB1: tapping the row launches the open-document picker restricted to JSON. */
    @Test
    fun tappingImportBackup_launchesOpenDocumentPickerForJson() {
        viewModel = Harness(gatewayReturning(backupJson), sinkReturning(EMPTY_SUCCESS)).viewModel
        val picker = TestPicker { }
        showSettings(picker)

        composeRule.onNodeWithTag("import_backup_row").performClick()

        assertArrayEquals(arrayOf("application/json"), picker.requestedMimeTypes)
    }

    /** WB2: a pick raises the destructive-worded confirmation; cancelling runs nothing. */
    @Test
    fun pickingAFile_showsReplaceEverythingConfirmation_andCancelImportsNothing() {
        var gatewayCalls = 0
        var sinkCalls = 0
        val gateway = object : BackupImportGateway {
            override suspend fun read(source: Uri): String { gatewayCalls += 1; return backupJson }
        }
        val sink = object : BackupJsonSink {
            override suspend fun importJson(json: String): ImportSummary { sinkCalls += 1; return EMPTY_SUCCESS }
        }
        val harness = Harness(gateway, sink)
        viewModel = harness.viewModel
        val picker = TestPicker { viewModel.requestBackupImport(pickedUri()) }
        showSettings(picker)

        composeRule.onNodeWithTag("import_backup_row").performClick()

        composeRule
            .onNodeWithTag("backup_import_confirm_title")
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.backup_import_confirm_message))
            .assertIsDisplayed()

        composeRule.onNodeWithTag("backup_import_confirm_cancel").performClick()

        composeRule.onNodeWithTag("backup_import_confirm_title").assertDoesNotExist()
        // Dismiss = the engine is never reached: no read, no import, no re-arm.
        assertEquals(0, gatewayCalls)
        assertEquals(0, sinkCalls)
        assertEquals(0, harness.rearmCalls.get())
        assertEquals(null, viewModel.backupImportState.value)
    }

    /** WB3 + WB4 + WB5: a confirmed import lists the per-table counts and re-arms once. */
    @Test
    fun confirmedImport_resultDialogListsCounts_andRearmsReminderOnce() {
        val counts = ImportSummary.Success(snakes = 2, feedings = 5, sheds = 1, weights = 3, foodStock = 4)
        val harness = Harness(gatewayReturning(backupJson), sinkReturning(counts))
        viewModel = harness.viewModel
        val picker = TestPicker { viewModel.requestBackupImport(pickedUri()) }
        showSettings(picker)

        composeRule.onNodeWithTag("import_backup_row").performClick()
        composeRule.onNodeWithTag("backup_import_confirm_yes").performClick()

        composeRule
            .onNodeWithTag("backup_import_dialog_title")
            .assertIsDisplayed()
        composeRule.onNodeWithText(expectedCountsMessage(counts)).assertIsDisplayed()
        // The re-arm seam the ViewModel calls fires exactly once on success.
        assertEquals(1, harness.rearmCalls.get())

        // Dismissing hides the dialog.
        composeRule.onNodeWithTag("backup_import_dismiss").performClick()
        composeRule.onNodeWithTag("backup_import_message").assertDoesNotExist()
    }

    /** WB4: an engine rejection names its reason — "not a Snake Tracker backup". */
    @Test
    fun rejectedImport_resultDialogNamesNotABackup() {
        resultDialogForRejection(ImportFailure.MalformedJson, R.string.backup_import_failure_not_backup)
    }

    /** WB4: a version the engine cannot restore names "newer version" instead. */
    @Test
    fun rejectedImport_resultDialogNamesNewerVersion() {
        resultDialogForRejection(
            ImportFailure.UnsupportedSchemaVersion(found = 2),
            R.string.backup_import_failure_newer_version
        )
    }

    /** WB4: an unreadable file names itself as the reason, with no engine involvement. */
    @Test
    fun unreadableFile_resultDialogNamesUnreadable_andRearmsNothing() {
        val harness = Harness(ioFailingGateway(), sinkReturning(EMPTY_SUCCESS))
        viewModel = harness.viewModel
        val picker = TestPicker { viewModel.requestBackupImport(pickedUri()) }
        showSettings(picker)

        composeRule.onNodeWithTag("import_backup_row").performClick()
        composeRule.onNodeWithTag("backup_import_confirm_yes").performClick()

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.backup_import_failure_unreadable))
            .assertIsDisplayed()
        assertEquals(0, harness.rearmCalls.get())
    }

    private fun resultDialogForRejection(reason: ImportFailure, messageRes: Int) {
        val harness = Harness(
            gatewayReturning(backupJson),
            sinkReturning(ImportSummary.Failure(reason))
        )
        viewModel = harness.viewModel
        val picker = TestPicker { viewModel.requestBackupImport(pickedUri()) }
        showSettings(picker)

        composeRule.onNodeWithTag("import_backup_row").performClick()
        composeRule.onNodeWithTag("backup_import_confirm_yes").performClick()

        composeRule
            .onNodeWithText(composeRule.activity.getString(messageRes))
            .assertIsDisplayed()
        // A failed import leaves the alarm alone: no re-arm through the seam.
        assertEquals(0, harness.rearmCalls.get())
    }

    /** The dialog's contract: five lines, in table order, from the plural resources. */
    private fun expectedCountsMessage(counts: ImportSummary.Success): String {
        val resources = composeRule.activity.resources
        return listOf(
            resources.getQuantityString(R.plurals.import_count_snakes, counts.snakes, counts.snakes),
            resources.getQuantityString(R.plurals.import_count_feedings, counts.feedings, counts.feedings),
            resources.getQuantityString(R.plurals.import_count_sheds, counts.sheds, counts.sheds),
            resources.getQuantityString(R.plurals.import_count_weights, counts.weights, counts.weights),
            resources.getQuantityString(R.plurals.import_count_food_stock, counts.foodStock, counts.foodStock)
        ).joinToString("\n")
    }

    private companion object {
        val EMPTY_SUCCESS = ImportSummary.Success(0, 0, 0, 0, 0)
    }
}
