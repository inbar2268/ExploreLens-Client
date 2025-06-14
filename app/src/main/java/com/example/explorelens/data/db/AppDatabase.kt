package com.example.explorelens.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.explorelens.data.db.siteDetails.SiteDetailsDao
import com.example.explorelens.data.db.siteDetails.SiteDetailsEntity
import com.example.explorelens.data.db.siteHistory.SiteHistory
import com.example.explorelens.data.db.siteHistory.SiteHistoryDao
import com.example.explorelens.data.db.statistics.UserStatistics
import com.example.explorelens.data.db.statistics.UserStatisticsDao

/**
 * Main database for the application
 */
@Database(
    entities = [User::class, SiteHistory::class, SiteDetailsEntity::class, UserStatistics::class],
    version = 7,
    exportSchema = false
)

@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun siteHistoryDao(): SiteHistoryDao
    abstract fun siteDetailsDao(): SiteDetailsDao
    abstract fun userStatisticsDao(): UserStatisticsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "explorelens"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}