package com.snaketracker.app.data

import androidx.room.withTransaction
import com.snaketracker.app.data.dao.LastFeedingInfo
import com.snaketracker.app.data.entities.*
import kotlinx.coroutines.flow.Flow

class Repository(private val db: AppDatabase) {

    // Snakes
    fun getAllSnakes(): Flow<List<Snake>> = db.snakeDao().getAll()
    fun getSnake(id: Long): Flow<Snake?> = db.snakeDao().getById(id)
    suspend fun addSnake(snake: Snake): Long = db.snakeDao().insert(snake)
    suspend fun updateSnake(snake: Snake) = db.snakeDao().update(snake)
    suspend fun deleteSnake(snake: Snake) = db.snakeDao().delete(snake)
    suspend fun getSnakesWithRemindersEnabled(): List<Snake> = db.snakeDao().getAllWithRemindersEnabled()

    // Feeding
    fun getFeedingEvents(snakeId: Long): Flow<List<FeedingEvent>> = db.feedingDao().getForSnake(snakeId)
    suspend fun getLastFeeding(snakeId: Long): FeedingEvent? = db.feedingDao().getLastForSnake(snakeId)
    fun getLastFeedingPerSnake(): Flow<List<LastFeedingInfo>> = db.feedingDao().getLastFeedingPerSnake()
    suspend fun addFeeding(event: FeedingEvent): Long = db.feedingDao().insert(event)
    suspend fun updateFeeding(event: FeedingEvent) = db.feedingDao().update(event)
    suspend fun deleteFeeding(event: FeedingEvent) = db.feedingDao().delete(event)

    // Logs a feeding and, if it was drawn from a food-stock item, decrements that
    // item's quantity by one - both in a single local database transaction.
    suspend fun logFeedingConsumingStock(event: FeedingEvent): Long {
        var newId = 0L
        db.withTransaction {
            newId = db.feedingDao().insert(event)
            event.foodStockItemId?.let { stockId ->
                db.foodStockDao().getById(stockId)?.let { item ->
                    if (item.quantity > 0) {
                        db.foodStockDao().update(item.copy(quantity = item.quantity - 1))
                    }
                }
            }
        }
        return newId
    }

    // Shed
    fun getShedEvents(snakeId: Long): Flow<List<ShedEvent>> = db.shedDao().getForSnake(snakeId)
    suspend fun addShed(event: ShedEvent): Long = db.shedDao().insert(event)
    suspend fun updateShed(event: ShedEvent) = db.shedDao().update(event)
    suspend fun deleteShed(event: ShedEvent) = db.shedDao().delete(event)

    // Weight
    fun getWeightEntries(snakeId: Long): Flow<List<WeightEntry>> = db.weightDao().getForSnake(snakeId)
    suspend fun addWeight(entry: WeightEntry): Long = db.weightDao().insert(entry)
    suspend fun updateWeight(entry: WeightEntry) = db.weightDao().update(entry)
    suspend fun deleteWeight(entry: WeightEntry) = db.weightDao().delete(entry)

    // Food stock
    fun getFoodStock(): Flow<List<FoodStockItem>> = db.foodStockDao().getAll()
    suspend fun getFoodStockItem(id: Long): FoodStockItem? = db.foodStockDao().getById(id)
    suspend fun addFoodStock(item: FoodStockItem): Long = db.foodStockDao().insert(item)
    suspend fun updateFoodStock(item: FoodStockItem) = db.foodStockDao().update(item)
    suspend fun deleteFoodStock(item: FoodStockItem) = db.foodStockDao().delete(item)

    companion object {
        @Volatile private var INSTANCE: Repository? = null
        fun getInstance(db: AppDatabase): Repository =
            INSTANCE ?: synchronized(this) { INSTANCE ?: Repository(db).also { INSTANCE = it } }
    }
}
