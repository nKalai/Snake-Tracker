package com.snaketracker.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.snaketracker.app.R
import com.snaketracker.app.data.backup.BackupExportGateway
import com.snaketracker.app.data.backup.BackupImportContentGateway
import com.snaketracker.app.data.backup.BackupImportRejectException
import com.snaketracker.app.data.backup.BackupRepository
import com.snaketracker.app.data.backup.GatewayTestProvider
import com.snaketracker.app.data.entities.FeedingEvent
import com.snaketracker.app.data.entities.FoodStockItem
import com.snaketracker.app.data.entities.ShedEvent
import com.snaketracker.app.data.entities.Snake
import com.snaketracker.app.data.entities.WeightEntry
import com.snaketracker.app.ui.model.BackupImportState
import com.snaketracker.app.ui.viewmodel.DefaultBackupCoordinator
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The oversized-source rejection end to end (PR #34 review 🟡, suggested
 * edge-case test 3): a provider whose `OpenableColumns.SIZE` exceeds the
 * import bound must be refused as a typed failure before a single byte is
 * read, so every outcome - including this one - is a dialog and the five
 * tables stay byte-for-byte what they were.
 */
@RunWith(AndroidJUnit4::class)
class BackupImportOversizeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-04-01T10:15:30Z"), ZoneOffset.UTC)

    private lateinit var db: AppDatabase

    @Before
    fun createDatabase() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    @Test
    fun read_oversizedSource_throwsTypedTooLargeBeforeReading() {
        val gateway = BackupImportContentGateway(context.contentResolver)

        val failure = assertThrows(IOException::class.java) {
            runBlocking { gateway.read(GatewayTestProvider.uri("oversize", "huge.json")) }
        }

        val typed = failure as? BackupImportRejectException
        assertNotNull("expected a BackupImportRejectException, got: $failure", typed)
        assertTrue("any source above the bound must be rejected",
            GatewayTestProvider.PROVIDER_REPORTED_SIZE > BackupImportContentGateway.MAX_IMPORT_BYTES)
    }

    @Test
    fun oversizedSource_fullStack_dialogsAndLeavesEveryTableUntouched() = runBlocking {
        val repository = BackupRepository(db, appVersion = "1.1", clock = fixedClock)
        val coordinator = DefaultBackupCoordinator(
            backupEngine = repository,
            exportGateway = object : BackupExportGateway {
                override suspend fun save(destination: android.net.Uri, json: String): String =
                    throw UnsupportedOperationException("this test never exports")
            },
            importGateway = BackupImportContentGateway(context.contentResolver),
            rearmReminders = { throw UnsupportedOperationException("a rejected import never re-arms") }
        )

        // Device data with distinct ids: a missed "untouched" would show.
        db.snakeDao().insert(Snake(id = 7, name = "Device Snake"))
        db.feedingDao().insert(FeedingEvent(id = 70, snakeId = 7, date = 100, foodType = "Rat"))
        db.shedDao().insert(ShedEvent(id = 71, snakeId = 7, date = 200))
        db.weightDao().insert(WeightEntry(id = 72, snakeId = 7, date = 300, grams = 500f))
        db.foodStockDao().insert(FoodStockItem(id = 90, name = "Device rats", foodType = "Rat"))

        val state = coordinator.importBackup(GatewayTestProvider.uri("oversize", "huge.json"))

        assertEquals(
            BackupImportState.Failure(R.string.backup_import_failure_too_large),
            state
        )
        // All five tables byte-for-byte what they were: the read never ran,
        // so the engine never ran either.
        assertEquals(listOf(7L), db.snakeDao().getAllOnce().map { it.id })
        assertEquals(listOf(70L), db.feedingDao().getAllOnce().map { it.id })
        assertEquals(listOf(71L), db.shedDao().getAllOnce().map { it.id })
        assertEquals(listOf(72L), db.weightDao().getAllOnce().map { it.id })
        assertEquals(listOf(90L), db.foodStockDao().getAllOnce().map { it.id })
    }
}
