package com.minirili.app.data.weather

import com.minirili.app.database.dao.CityDao
import com.minirili.app.database.dao.WeatherCacheDao
import com.minirili.app.database.entity.CityEntity
import com.minirili.app.database.entity.WeatherCacheEntity
import com.minirili.app.database.entity.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 天气仓库：缓存优先 + 30 分钟新鲜度 + 超出预报范围回退今日。
 *
 * 业务层只调用 [getWeatherForDate] / [getCurrentWeather] / [ensureCity]，
 * 网络与解析细节封装在此处。
 */
@Singleton
class WeatherRepository @Inject constructor(
    private val dataSource: WeatherDataSource,
    private val weatherCacheDao: WeatherCacheDao,
    private val cityDao: CityDao
) {
    companion object {
        private const val CACHE_FRESH_MS = 30L * 60 * 1000 // 30 分钟
        private const val FORECAST_DAYS = 16
    }

    /** 获取某日天气。超出预报范围（>15 天）→ 回退今日；无缓存/过期 → 触发拉取。 */
    suspend fun getWeatherForDate(targetDate: String, city: City): WeatherResult {
        val today = todayStr()
        val effectiveDate = if (isWithinForecast(targetDate, today)) targetDate else today
        val cacheKey = cacheKey(city, today)

        val cached = weatherCacheDao.get(cacheKey)
        val data = if (cached != null && System.currentTimeMillis() - cached.fetchedAt < CACHE_FRESH_MS) {
            parseCache(cached, city)
        } else {
            try {
                val fresh = fetchAndCache(city, cacheKey)
                fresh
            } catch (e: Exception) {
                if (cached != null) parseCache(cached, city) else return WeatherResult.Error(e)
            }
        }
        if (data.daily.isEmpty()) {
            return WeatherResult.Error(IllegalStateException("No daily data"))
        }
        return WeatherResult.ForDate(data.current, data.daily, data.hourly, data.fetchedAt, city, effectiveDate)
    }

    /** 仅当前天气，给天气栏目用。 */
    suspend fun getCurrentWeather(city: City): WeatherResult {
        return getWeatherForDate(todayStr(), city)
    }

    /** 拉取并写入缓存。 */
    private suspend fun fetchAndCache(city: City, cacheKey: String): WeatherData {
        val raw = dataSource.fetchWeather(city.latitude, city.longitude)
        val data = parse(raw, city)
        weatherCacheDao.upsert(
            WeatherCacheEntity(
                cacheKey = cacheKey,
                cityId = city.id,
                fetchedAt = data.fetchedAt,
                rawJson = rawToJson(raw)
            )
        )
        return data
    }

    /** 确保城市存在：传入 City，写入 DB；多城市管理用。 */
    suspend fun ensureCity(city: City) {
        cityDao.upsert(
            CityEntity(
                id = city.id,
                name = city.name,
                latitude = city.latitude,
                longitude = city.longitude,
                country = city.country,
                isCurrentLocation = city.isCurrentLocation,
                sortOrder = System.currentTimeMillis()
            )
        )
    }

    suspend fun getCities(): List<City> = cityDao.getAllOrdered().map { it.toDomain() }

    fun observeCities(): kotlinx.coroutines.flow.Flow<List<City>> =
        cityDao.observeAllOrdered().map { list -> list.map { it.toDomain() } }

    suspend fun removeCity(id: String) = cityDao.deleteById(id)

    suspend fun searchCity(name: String): List<City> = dataSource.searchCity(name)

    /** WTH-07: 获取 AQI 数据（不带缓存，按需调用） */
    suspend fun getAQI(city: City): AQIData? {
        return try {
            dataSource.fetchAQI(city.latitude, city.longitude)
        } catch (_: Exception) { null }
    }

    // ===== 解析 =====

    private fun parse(raw: RawWeatherResponse, city: City): WeatherData {
        val current = raw.current
        val hourly = raw.hourly
        val daily = raw.daily
        val fetchedAt = System.currentTimeMillis()

        val cur: CurrentWeather = if (current != null) {
            CurrentWeather(
                timeMillis = current.optString("time").let { parseIsoToMillis(it) },
                temperature = current.optDouble("temperature_2m", 0.0),
                apparentTemperature = current.optDouble("apparent_temperature", 0.0),
                humidity = current.optInt("relative_humidity_2m", 0),
                weatherCode = current.optInt("weather_code", 0),
                windSpeed = current.optDouble("wind_speed_10m", 0.0),
                windDirection = current.optInt("wind_direction_10m", 0),
                pressure = current.optDouble("surface_pressure", 1013.25),
                isDay = current.optInt("is_day", 1) == 1
            )
        } else {
            CurrentWeather(0, 0.0, 0.0, 0, 0, 0.0, 0, 1013.25, true)
        }

        val hourTimes = hourly?.optJSONArray("time") ?: emptyJsonArray()
        val hourTemps = hourly?.optJSONArray("temperature_2m")
        val hourPrec = hourly?.optJSONArray("precipitation_probability")
        val hourCode = hourly?.optJSONArray("weather_code")
        val hourWind = hourly?.optJSONArray("wind_speed_10m")
        val hourList = (0 until hourTimes.length()).map { i ->
            HourlyWeather(
                timeMillis = parseIsoToMillis(hourTimes.getString(i)),
                temperature = hourTemps?.optDouble(i, 0.0) ?: 0.0,
                precipitationProbability = hourPrec?.optInt(i, 0) ?: 0,
                weatherCode = hourCode?.optInt(i, 0) ?: 0,
                windSpeed = hourWind?.optDouble(i, 0.0) ?: 0.0
            )
        }

        val dayDates = daily?.optJSONArray("time") ?: emptyJsonArray()
        val dayCode = daily?.optJSONArray("weather_code")
        val dayMax = daily?.optJSONArray("temperature_2m_max")
        val dayMin = daily?.optJSONArray("temperature_2m_min")
        val dayPrec = daily?.optJSONArray("precipitation_probability_max")
        val dayRise = daily?.optJSONArray("sunrise")
        val daySet = daily?.optJSONArray("sunset")
        val dayList = (0 until dayDates.length()).map { i ->
            DailyWeather(
                date = dayDates.getString(i),
                weatherCode = dayCode?.optInt(i, 0) ?: 0,
                tempMax = dayMax?.optDouble(i, 0.0) ?: 0.0,
                tempMin = dayMin?.optDouble(i, 0.0) ?: 0.0,
                precipitationProbabilityMax = dayPrec?.optInt(i, 0) ?: 0,
                sunriseMillis = dayRise?.getString(i)?.let { parseIsoToMillis(it) },
                sunsetMillis = daySet?.getString(i)?.let { parseIsoToMillis(it) }
            )
        }

        return WeatherData(city.name, city.latitude, city.longitude, fetchedAt, cur, hourList, dayList)
    }

    private fun parseCache(entity: WeatherCacheEntity, city: City): WeatherData {
        val raw = runCatching {
            val obj = org.json.JSONObject(entity.rawJson)
            RawWeatherResponse(
                latitude = city.latitude,
                longitude = city.longitude,
                timezone = "Asia/Shanghai",
                current = obj.optJSONObject("current"),
                hourly = obj.optJSONObject("hourly"),
                daily = obj.optJSONObject("daily")
            )
        }.getOrNull() ?: throw IllegalStateException("Bad cache JSON")
        return parse(raw, city).copy(fetchedAt = entity.fetchedAt)
    }

    private fun rawToJson(raw: RawWeatherResponse): String {
        val obj = org.json.JSONObject()
        raw.current?.let { obj.put("current", it) }
        raw.hourly?.let { obj.put("hourly", it) }
        raw.daily?.let { obj.put("daily", it) }
        return obj.toString()
    }

    private fun cacheKey(city: City, today: String) = "${city.id}|$today"

    private fun emptyJsonArray() = org.json.JSONArray()

    private fun isWithinForecast(target: String, today: String): Boolean {
        val t = parseYmd(target) ?: return false
        val base = parseYmd(today) ?: return false
        val diff = (t.timeInMillis - base.timeInMillis) / (24 * 3600_000L)
        return diff in 0..FORECAST_DAYS - 1L
    }

    private fun todayStr(): String {
        val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        return "%04d-%02d-%02d".format(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun parseYmd(ymd: String): Calendar? {
        val p = ymd.split("-")
        if (p.size != 3) return null
        return Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply {
            clear()
            set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
        }
    }

    private fun parseIsoToMillis(iso: String): Long {
        if (iso.isBlank()) return 0L
        // Open-Meteo 返回 "2026-07-11T13:00" 这种格式（无秒、可能带 timezone 后缀）
        return runCatching {
            val parts = iso.split("T")
            val d = parts[0].split("-").map { it.toInt() }
            val timeParts = if (parts.size > 1) parts[1].split(":").map { it.toInt() } else listOf(0, 0)
            val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply {
                clear()
                set(d[0], d[1] - 1, d[2], timeParts[0], timeParts.getOrElse(1) { 0 })
            }
            cal.timeInMillis
        }.getOrDefault(0L)
    }
}

sealed class WeatherResult {
    data class ForDate(
        val current: CurrentWeather,
        val daily: List<DailyWeather>,
        val hourly: List<HourlyWeather>,
        val fetchedAt: Long,
        val city: City,
        val effectiveDate: String
    ) : WeatherResult()

    data class Error(val error: Throwable) : WeatherResult()
}
