package com.snaketracker.app.data.dao

import androidx.room.*
import com.snaketracker.app.data.entities.Snake
import kotlinx.coroutines.flow.Flow

@Dao
interface SnakeDao {
    @Query("SELECT * FROM snakes ORDER BY name ASC")
    fun getAll(): Flow<List<Snake>>

    @Query("SELECT * FROM snakes WHERE id = :id")
    fun getById(id: Long): Flow<Snake?>

    @Query("SELECT * FROM snakes WHERE remindersEnabled = 1")
    suspend fun getAllWithRemindersEnabled(): List<Snake>

    @Insert
    suspend fun insert(snake: Snake): Long

    @Update
    suspend fun update(snake: Snake)

    @Delete
    suspend fun delete(snake: Snake)
}

