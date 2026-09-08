package com.snaketracker.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.snaketracker.app.data.dao.*
import com.snaketracker.app.data.entities.*

@Database(
    entities = [
        Snake::class,
        FeedingEvent::class,
        ShedEvent::class,
        WeightEntry::class,
        FoodStockItem::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun snakeDao(): SnakeDao
    abstract fun feedingDao(): FeedingDao
    abstract fun shedDao(): ShedDao
    abstract fun weightDao(): WeightDao
    abstract fun foodStockDao(): FoodStockDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Purely local SQLite file stored in the app's private data directory.
                // No network calls, no cloud sync, no export happens automatically.
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "snake_tracker.db"
                )
                    // Development convenience: if the schema changes between versions,
                    // wipe and recreate rather than crash. Fine while you're the only
                    // user testing the app locally; if you ship this and need to
                    // preserve user data across upgrades, replace with real Migration
                    // objects instead.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
