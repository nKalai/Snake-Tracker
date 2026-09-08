package com.snaketracker.app.data.dao

import androidx.room.*
import com.snaketracker.app.data.entities.WeightEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_entries WHERE snakeId = :snakeId ORDER BY date DESC")
    fun getForSnake(snakeId: Long): Flow<List<WeightEntry>>

    @Insert
    suspend fun insert(entry: WeightEntry): Long

    @Update
    suspend fun update(entry: WeightEntry)

    @Delete
    suspend fun delete(entry: WeightEntry)
}
