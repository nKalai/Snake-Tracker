package com.snaketracker.app.ui.viewmodel

import android.net.Uri
import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.snaketracker.app.data.AppDatabase
import com.snaketracker.app.data.Repository
import com.snaketracker.app.data.dao.FeedingDao
import com.snaketracker.app.data.dao.FoodStockDao
import com.snaketracker.app.data.dao.LastFeedingInfo
import com.snaketracker.app.data.dao.ShedDao
import com.snaketracker.app.data.dao.SnakeDao
import com.snaketracker.app.data.dao.WeightDao
import com.snaketracker.app.data.entities.FeedingEvent
import com.snaketracker.app.data.entities.FoodStockItem
import com.snaketracker.app.data.entities.ShedEvent
import com.snaketracker.app.data.entities.Snake
import com.snaketracker.app.data.entities.WeightEntry
import com.snaketracker.app.ui.model.BackupImportState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// JVM stand-ins so the import-behavior tests can construct SnakeViewModel
// without a Room database or an Android runtime: the database-backed flows
// are never collected in these tests, so empty flows / unused DAO methods
// are all the ViewModel ever observes.

internal fun backupImportTestRepository(): Repository = Repository(FakeAppDatabase)

private object FakeAppDatabase : AppDatabase() {
    override fun snakeDao(): SnakeDao = FakeSnakeDao
    override fun feedingDao(): FeedingDao = FakeFeedingDao
    override fun shedDao(): ShedDao = FakeShedDao
    override fun weightDao(): WeightDao = FakeWeightDao
    override fun foodStockDao(): FoodStockDao = FakeFoodStockDao

    // Never reached: these tests do not open the database. The open-helper
    // override satisfies Room's deprecated abstract member for the stub
    // subclass, so its deprecation diagnostic is suppressed.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper =
        unsupported()
    override fun createInvalidationTracker(): InvalidationTracker = unsupported()
    override fun clearAllTables() = unsupported()
}

private fun unsupported(): Nothing = throw UnsupportedOperationException("unused in JVM tests")

private object FakeSnakeDao : SnakeDao {
    override fun getAll(): Flow<List<Snake>> = flowOf(emptyList())
    override fun getById(id: Long): Flow<Snake?> = flowOf(null)
    override suspend fun getAllWithRemindersEnabled(): List<Snake> = emptyList()
    override suspend fun getAllOnce(): List<Snake> = emptyList()
    override suspend fun insert(snake: Snake): Long = unsupported()
    override suspend fun deleteAll() = unsupported()
    override suspend fun update(snake: Snake) = unsupported()
    override suspend fun delete(snake: Snake) = unsupported()
}

private object FakeFeedingDao : FeedingDao {
    override fun getForSnake(snakeId: Long): Flow<List<FeedingEvent>> = flowOf(emptyList())
    override suspend fun getLastForSnake(snakeId: Long): FeedingEvent? = null
    override fun getLastFeedingPerSnake(): Flow<List<LastFeedingInfo>> = flowOf(emptyList())
    override suspend fun getLastFeedingPerSnakeOnce(): List<LastFeedingInfo> = emptyList()
    override suspend fun getAllOnce(): List<FeedingEvent> = emptyList()
    override suspend fun insert(event: FeedingEvent): Long = unsupported()
    override suspend fun deleteAll() = unsupported()
    override suspend fun update(event: FeedingEvent) = unsupported()
    override suspend fun delete(event: FeedingEvent) = unsupported()
}

private object FakeShedDao : ShedDao {
    override fun getForSnake(snakeId: Long): Flow<List<ShedEvent>> = flowOf(emptyList())
    override suspend fun getAllOnce(): List<ShedEvent> = emptyList()
    override suspend fun insert(event: ShedEvent): Long = unsupported()
    override suspend fun deleteAll() = unsupported()
    override suspend fun update(event: ShedEvent) = unsupported()
    override suspend fun delete(event: ShedEvent) = unsupported()
}

private object FakeWeightDao : WeightDao {
    override fun getForSnake(snakeId: Long): Flow<List<WeightEntry>> = flowOf(emptyList())
    override suspend fun getAllOnce(): List<WeightEntry> = emptyList()
    override suspend fun insert(entry: WeightEntry): Long = unsupported()
    override suspend fun deleteAll() = unsupported()
    override suspend fun update(entry: WeightEntry) = unsupported()
    override suspend fun delete(entry: WeightEntry) = unsupported()
}

private object FakeFoodStockDao : FoodStockDao {
    override fun getAll(): Flow<List<FoodStockItem>> = flowOf(emptyList())
    override suspend fun getById(id: Long): FoodStockItem? = null
    override suspend fun getAllOnce(): List<FoodStockItem> = emptyList()
    override suspend fun insert(item: FoodStockItem): Long = unsupported()
    override suspend fun deleteAll() = unsupported()
    override suspend fun update(item: FoodStockItem) = unsupported()
    override suspend fun delete(item: FoodStockItem) = unsupported()
}

internal fun backupImportStateOf(viewModel: SnakeViewModel): BackupImportState? =
    viewModel.backupImportState.value
