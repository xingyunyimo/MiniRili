package com.minirili.app.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minirili.app.database.entity.CityEntity
import com.minirili.app.database.entity.WeatherCacheEntity

@Dao
interface WeatherCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WeatherCacheEntity)

    @Query("SELECT * FROM weather_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun get(key: String): WeatherCacheEntity?

    @Query("DELETE FROM weather_cache WHERE fetchedAt < :olderThan")
    suspend fun evict(olderThan: Long)

    @Query("DELETE FROM weather_cache")
    suspend fun clearAll()
}

@Dao
interface CityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CityEntity)

    @Query("SELECT * FROM cities ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllOrdered(): List<CityEntity>

    @Query("SELECT * FROM cities ORDER BY sortOrder ASC, name ASC")
    fun observeAllOrdered(): kotlinx.coroutines.flow.Flow<List<CityEntity>>

    @Query("SELECT * FROM cities WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CityEntity?

    @Query("DELETE FROM cities WHERE id = :id")
    suspend fun deleteById(id: String)
}
