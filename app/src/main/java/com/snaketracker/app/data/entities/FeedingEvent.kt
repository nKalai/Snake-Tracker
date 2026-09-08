package com.snaketracker.app.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "feeding_events",
    foreignKeys = [ForeignKey(
        entity = Snake::class,
        parentColumns = ["id"],
        childColumns = ["snakeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("snakeId")]
)
data class FeedingEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val snakeId: Long,
    val date: Long,
    val foodType: String,       // e.g. Mouse, Rat, Chick, Quail
    val foodSize: String = "",  // e.g. Pinky, Fuzzy, Small Adult, 250g
    val accepted: Boolean = true, // false = refusal
    val assist: Boolean = false,  // assist-fed
    val notes: String = "",
    val foodStockItemId: Long? = null // which stock item this feeding was drawn from, if any
)
