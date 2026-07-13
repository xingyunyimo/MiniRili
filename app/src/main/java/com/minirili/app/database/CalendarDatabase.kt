package com.minirili.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.minirili.app.database.dao.CityDao
import com.minirili.app.database.dao.EventDao
import com.minirili.app.database.dao.WeatherCacheDao
import com.minirili.app.database.entity.CityEntity
import com.minirili.app.database.entity.EventEntity
import com.minirili.app.database.entity.WeatherCacheEntity

@Database(
    entities = [EventEntity::class, WeatherCacheEntity::class, CityEntity::class],
    version = 6,
    exportSchema = false
)
abstract class CalendarDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao
    abstract fun weatherCacheDao(): WeatherCacheDao
    abstract fun cityDao(): CityDao

    companion object {
        @Volatile
        private var INSTANCE: CalendarDatabase? = null

        fun getDatabase(context: Context): CalendarDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CalendarDatabase::class.java,
                    "calendar_database"
                )
                    .fallbackToDestructiveMigration() // MVP 阶段允许破坏性迁移
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
