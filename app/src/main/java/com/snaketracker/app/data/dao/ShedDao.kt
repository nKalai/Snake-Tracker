package com.snaketracker.app.data.dao

import androidx.room.*
import com.snaketracker.app.data.entities.ShedEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface ShedDao {
    @Query("SELECT * FROM shed_events WHERE snakeId = :snakeId ORDER BY date DESC")
    fun getForSnake(snakeId: Long): Flow<List<ShedEvent>>

    @Insert
    suspend fun insert(event: ShedEvent): Long
    // One-shot dump of the whole table, ordered by id for deterministic output.
    @Query("SELECT * FROM shed_events ORDER BY id ASC")
    suspend fun getAllOnce(): List<ShedEvent>


    @Update
    suspend fun update(event: ShedEvent)

    @Delete
    suspend fun delete(event: ShedEvent)
}
