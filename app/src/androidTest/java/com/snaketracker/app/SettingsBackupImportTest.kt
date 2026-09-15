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
import com.snaketracker.app.data.backup.BackupEngine
import com.snaketracker.app.data.backup.BackupImportGateway
import com.snaketracker.app.data.backup.BackupTable
import com.snaketracker.app.data.backup.ImportFailure
import com.snaketracker.app.data.backup.ImportSummary
import com.snaketracker.app.data.backup.TableCount
import com.snaketracker.app.testing.SettingsBackupHarness
import com.snaketracker.app.ui.screens.BackupSourcePicker
import com.snaketracker.app.ui.screens.SettingsScreen
import com.snaketracker.app.ui.theme.SnakeTrackerTheme
import com.snaketracker.app.ui.viewmodel.SnakeViewModel
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * Settings import-backup UI (issue #28) driven through [SettingsScreen]'s
 * public seam: a fake [BackupSourcePicker] plays the SAF open-document
 * picker (it can echo a picked URI straight into the ViewModel), and fake
 * backup seams decide the outcome — so the confirmation gate and both result
 * dialogs render without a real system document UI. The real picker contract
 * is pinned in [SettingsPickerContractTest].
 *
 * The reminder re-arm is asserted at the seam the ViewModel calls — the same
 * suspend entry point production wires to `ReminderArming.reschedule` — never
 * by inspecting AlarmManager.
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
        importGateway: BackupImportGateway,
        engine: BackupEngine
    ) {
        val rearmCalls = AtomicInteger(0)
        val viewModel = SettingsBackupHarness.viewModel(
            db = db,
            backup = engine,
            importGateway = importGateway,
            onRearm = { rearmCalls.incrementAndGet() }
        )
    }

    private fun engineReturning(result: ImportSummary): BackupEngine =
        SettingsBackupHarness.engineReturning(result)

    /** Picker stand-in; [echoPickedFile] makes "pick" immediately act like a completed pick. */
    private class TestPicker(private val echoPickedFile: () -> Unit) : BackupSourcePicker {
        override fun pick() = echoPickedFile()
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
    private fun pickedUri(): Uri = SettingsBackupHarness.pickedUri()

    @Test
    fun importBackupRow_isPresentOnSettings() {
        viewModel = Harness(SettingsBackupHarness.importGatewayReading(backupJson), engineReturning(EMPTY_SUCCESS)).viewModel
        showSettings(picker = BackupSourcePicker { })

        composeRule.onNodeWithTag("import_backup_row").assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.settings_import_backup))
            .assertIsDisplayed()
    }

    /** WB2: a pick raises the destructive-worded confirmation; cancelling runs nothing. */
    @Test
    fun pickingAFile_showsReplaceEverythingConfirmation_andCancelImportsNothing() {
        var gatewayCalls = 0
        var engineCalls = 0
        val gateway = object : BackupImportGateway {
            override suspend fun read(source: Uri): String { gatewayCalls += 1; return backupJson }
        }
        val engine = object : BackupEngine {
            override suspend fun exportAll(): String =
                throw UnsupportedOperationException("import tests never export")
            override suspend fun importJson(json: String): ImportSummary {
                engineCalls += 1
                return EMPTY_SUCCESS
            }
        }
        val harness = Harness(gateway, engine)
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
        assertEquals(0, engineCalls)
        assertEquals(0, harness.rearmCalls.get())
        assertEquals(null, viewModel.backupImportState.value)
    }

    /** WB3 + WB4 + WB5: a confirmed import lists the per-table counts and re-arms once. */
    @Test
    fun confirmedImport_resultDialogListsCounts_andRearmsReminderOnce() {
        val counts = ImportSummary.Success(
            listOf(
                TableCount(BackupTable.SNAKE, 2),
                TableCount(BackupTable.FEEDING, 5),
                TableCount(BackupTable.SHED, 1),
                TableCount(BackupTable.WEIGHT, 3),
                TableCount(BackupTable.FOOD_STOCK, 4)
            )
        )
        val harness = Harness(SettingsBackupHarness.importGatewayReading(backupJson), engineReturning(counts))
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
        val harness = Harness(SettingsBackupHarness.importGatewayFailingWith(IOException("The chosen file could not be opened for reading.")), engineReturning(EMPTY_SUCCESS))
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

    /** WB4: a validated file that fails mid-insert names the database as the reason. */
    @Test
    fun databaseFailure_resultDialogNamesDatabase_andRearmsNothing() {
        val throwingEngine = object : BackupEngine {
            override suspend fun exportAll(): String =
                throw UnsupportedOperationException("import tests never export")
            override suspend fun importJson(json: String): ImportSummary =
                throw IllegalStateException("SQLITE_BUSY")
        }
        val harness = Harness(SettingsBackupHarness.importGatewayReading(backupJson), throwingEngine)
        viewModel = harness.viewModel
        val picker = TestPicker { viewModel.requestBackupImport(pickedUri()) }
        showSettings(picker)

        composeRule.onNodeWithTag("import_backup_row").performClick()
        composeRule.onNodeWithTag("backup_import_confirm_yes").performClick()

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.backup_import_failure_database))
            .assertIsDisplayed()
        assertEquals(0, harness.rearmCalls.get())
    }

    /**
     * WB4 "distinct reason per failure kind", proven where strings resolve:
     * the seven failure messages plus the confirm/result copy are each
     * non-empty on the device and pairwise distinct — the check the JVM
     * resource-id test honestly cannot make.
     */
    @Test
    fun failureMessages_resolveToPairwiseDistinctNonEmptyCopy() {
        val ids = listOf(
            R.string.backup_import_failure_not_backup,
            R.string.backup_import_failure_newer_version,
            R.string.backup_import_failure_orphan_rows,
            R.string.backup_import_failure_invalid_rows,
            R.string.backup_import_failure_unreadable,
            R.string.backup_import_failure_too_large,
            R.string.backup_import_failure_database,
            // The surrounding dialog copy the failure messages share a screen with.
            R.string.backup_import_confirm_title,
            R.string.backup_import_confirm_message,
            R.string.backup_import_failure_title,
            R.string.backup_import_success_title
        )
        val copy = ids.map { composeRule.activity.getString(it) }

        for ((id, text) in ids.zip(copy)) {
            assertTrue("string $id resolved to blank copy", text.isNotBlank())
        }
        assertEquals(
            "every import dialog string must read differently",
            ids.size,
            copy.distinct().size
        )
    }

    private fun resultDialogForRejection(reason: ImportFailure, messageRes: Int) {
        val harness = Harness(
            SettingsBackupHarness.importGatewayReading(backupJson),
            engineReturning(ImportSummary.Failure(reason))
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
        return counts.counts.joinToString("\n") { table ->
            val plural = when (table.table) {
                BackupTable.SNAKE -> R.plurals.import_count_snakes
                BackupTable.FEEDING -> R.plurals.import_count_feedings
                BackupTable.SHED -> R.plurals.import_count_sheds
                BackupTable.WEIGHT -> R.plurals.import_count_weights
                BackupTable.FOOD_STOCK -> R.plurals.import_count_food_stock
            }
            resources.getQuantityString(plural, table.count, table.count)
        }
    }

    private companion object {
        // An empty file still reports five zero rows, in table order.
        val EMPTY_SUCCESS = ImportSummary.Success(
            listOf(
                TableCount(BackupTable.SNAKE, 0),
                TableCount(BackupTable.FEEDING, 0),
                TableCount(BackupTable.SHED, 0),
                TableCount(BackupTable.WEIGHT, 0),
                TableCount(BackupTable.FOOD_STOCK, 0)
            )
        )
    }
}
