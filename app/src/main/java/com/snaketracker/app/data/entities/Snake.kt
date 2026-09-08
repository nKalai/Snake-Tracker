package com.snaketracker.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "snakes")
data class Snake(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val species: String = "",
    val morph: String = "",
    val sex: String = "Unknown", // Male / Female / Unknown
    val birthDate: Long? = null,
    val acquisitionDate: Long? = null,
    val enclosure: String = "",
    val notes: String = "",
    // Reminder configuration
    val feedingIntervalDays: Int = 7,
    val remindersEnabled: Boolean = true
)
