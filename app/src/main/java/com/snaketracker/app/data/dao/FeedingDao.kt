package com.snaketracker.app.data.dao

import androidx.room.*
import com.snaketracker.app.data.entities.FeedingEvent
import kotlinx.coroutines.flow.Flow

data class LastFeedingInfo(val snakeId: Long, val lastDate: Long)

@Dao
interface FeedingDao {
    @Query("SELECT * FROM feeding_events WHERE snakeId = :snakeId ORDER BY date DESC")
    fun getForSnake(snakeId: Long): Flow<List<FeedingEvent>>

    @Query("SELECT * FROM feeding_events WHERE snakeId = :snakeId ORDER BY date DESC LIMIT 1")
    suspend fun getLastForSnake(snakeId: Long): FeedingEvent?

    @Query("SELECT snakeId, MAX(date) as lastDate FROM feeding_events GROUP BY snakeId")
    fun getLastFeedingPerSnake(): Flow<List<LastFeedingInfo>>

    @Insert
    suspend fun insert(event: FeedingEvent): Long

    @Update
    suspend fun update(event: FeedingEvent)

    @Delete
    suspend fun delete(event: FeedingEvent)
}
