package com.minirili.app.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 天气数据缓存。每城市每日一行，key = "cityId|today"，30 分钟内命中即直接用。
 * 完整 JSON 落库，UI 启动时秒开不依赖网络。JP/EN 城市名变化时也日频刷新。
 */
@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val cacheKey: String,
    val cityId: String,
    val fetchedAt: Long,
    val rawJson: String
)

/**
 * 多城市管理。MVP 单城市阶段仅写一条"当前位置"即可。
 */
@Entity(tableName = "cities")
data class CityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?,
    val isCurrentLocation: Boolean = false,
    val sortOrder: Long = 0L
)

fun CityEntity.toDomain(): com.minirili.app.data.weather.City =
    com.minirili.app.data.weather.City(
        id = id, name = name, latitude = latitude, longitude = longitude,
        country = country, isCurrentLocation = isCurrentLocation
    )
