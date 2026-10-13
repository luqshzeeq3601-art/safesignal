package com.safesignal.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Main Room database instance for SafeSignal.
 */
@Database(entities = [AlertEntity::class], version = 1, exportSchema = false)
abstract class SafeSignalDatabase : RoomDatabase() {

    abstract fun alertDao(): AlertDao

    companion object {
        const val DATABASE_NAME = "safesignal.db"

        fun createInMemory(context: Context): SafeSignalDatabase {
            return Room.inMemoryDatabaseBuilder(context, SafeSignalDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }

        fun create(context: Context): SafeSignalDatabase {
            return Room.databaseBuilder(context, SafeSignalDatabase::class.java, DATABASE_NAME)
                .fallbackToDestructiveMigration(true)
                .build()
        }
    }
}
