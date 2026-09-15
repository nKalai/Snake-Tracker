package com.snaketracker.app.data.dao

import androidx.room.*
import com.snaketracker.app.data.entities.FoodStockItem
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodStockDao {
    @Query("SELECT * FROM food_stock ORDER BY name ASC")
    fun getAll(): Flow<List<FoodStockItem>>

    @Query("SELECT * FROM food_stock WHERE id = :id")
    suspend fun getById(id: Long): FoodStockItem?

    // One-shot dump of the whole table, ordered by id for deterministic output.
    @Query("SELECT * FROM food_stock ORDER BY id ASC")
    suspend fun getAllOnce(): List<FoodStockItem>

    @Insert
    suspend fun insert(item: FoodStockItem): Long

    @Update
    suspend fun update(item: FoodStockItem)

    @Delete
    suspend fun delete(item: FoodStockItem)
}
