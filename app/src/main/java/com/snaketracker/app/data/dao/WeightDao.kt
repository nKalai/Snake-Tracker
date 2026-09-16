package com.snaketracker.app.data.dao

import androidx.room.*
import com.snaketracker.app.data.entities.WeightEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_entries WHERE snakeId = :snakeId ORDER BY date DESC")
    fun getForSnake(snakeId: Long): Flow<List<WeightEntry>>

    // One-shot dump of the whole table, ordered by id for deterministic output.
    @Query("SELECT * FROM weight_entries ORDER BY id ASC")
    suspend fun getAllOnce(): List<WeightEntry>

    @Insert
    suspend fun insert(entry: WeightEntry): Long

    // Whole-table wipe; used only inside the import replace-everything transaction.
    @Query("DELETE FROM weight_entries")
    suspend fun deleteAll()

    @Update
    suspend fun update(entry: WeightEntry)

    @Delete
    suspend fun delete(entry: WeightEntry)
}
