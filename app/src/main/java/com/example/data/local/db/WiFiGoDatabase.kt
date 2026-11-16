package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SpeedTestRecordEntity::class,
        DownloadTaskEntity::class,
        SavedWebpageEntity::class,
        FavoriteNetworkEntity::class,
        LocalFileItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WiFiGoDatabase : RoomDatabase() {
    abstract fun speedTestDao(): SpeedTestDao
    abstract fun downloadDao(): DownloadDao
    abstract fun savedWebpageDao(): SavedWebpageDao
    abstract fun favoriteNetworkDao(): FavoriteNetworkDao
    abstract fun localFileDao(): LocalFileDao

    companion object {
        @Volatile
        private var INSTANCE: WiFiGoDatabase? = null

        fun getInstance(context: Context): WiFiGoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WiFiGoDatabase::class.java,
                    "wifigo_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
