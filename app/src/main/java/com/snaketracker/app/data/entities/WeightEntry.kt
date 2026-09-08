package com.snaketracker.app.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "weight_entries",
    foreignKeys = [ForeignKey(
        entity = Snake::class,
        parentColumns = ["id"],
        childColumns = ["snakeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("snakeId")]
)
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val snakeId: Long,
    val date: Long,
    val grams: Float,
    val notes: String = ""
)
