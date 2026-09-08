package com.snaketracker.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "food_stock")
data class FoodStockItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,      // e.g. "Frozen mice - small"
    val foodType: String,  // Mouse, Rat, Chick...
    val size: String = "",
    val quantity: Int = 0,
    val lowStockThreshold: Int = 5,
    val notes: String = ""
)
