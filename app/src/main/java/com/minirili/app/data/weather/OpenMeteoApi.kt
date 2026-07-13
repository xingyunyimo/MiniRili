package com.minirili.app.data.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Open-Meteo 数据源实现。
 *
 * 使用 Android 内置 HttpURLConnection + org.json，不引入 Retrofit/OkHttp/Moshi，
 * 包体积极小（零新增依赖）。天气请求频率低（≥1h/次），无需连接池。
 *
 * 文档：https://open-meteo.com/en/docs
 */
class OpenMeteoApi(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 8_000
) : WeatherDataSource {

    companion object {
        private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        private const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
        private const val AQI_URL = "https://air-quality-api.open-meteo.com/v1/air-quality"
    }

    override suspend fun fetchWeather(latitude: Double, longitude: Double): RawWeatherResponse =
        withContext(Dispatchers.IO) {
            val url = buildForecastUrl(latitude, longitude)
            val body = httpGet(url)
            val json = org.json.JSONObject(body)
            RawWeatherResponse(
                latitude = json.optDouble("latitude", latitude),
                longitude = json.optDouble("longitude", longitude),
                timezone = json.optString("timezone", "Asia/Shanghai"),
                current = json.optJSONObject("current"),
                hourly = json.optJSONObject("hourly"),
                daily = json.optJSONObject("daily")
            )
        }

    override suspend fun searchCity(name: String, language: String, count: Int): List<City> =
        withContext(Dispatchers.IO) {
            // 1. Open-Meteo 搜索（对国外城市/大城市有较好覆盖，但国内县级数据缺失且省份标错）
            val openMeteoResults = try { searchOpenMeteo(name, language, count) } catch (_: Exception) { emptyList() }
            // 2. 本地数据库搜索（中国城市全覆盖，省份准确，支持区县级）
            val localResults = try { ChineseCityDb.search(name) } catch (_: Exception) { emptyList() }
            // 3. 合并去重：本地优先（数据准确），Open-Meteo 补充国外/未见过的城市
            val seen = mutableSetOf<String>()
            (localResults + openMeteoResults).filter { seen.add(it.id) }
        }

    private fun searchOpenMeteo(name: String, language: String, count: Int): List<City> {
        val q = URLEncoder.encode(name, "UTF-8")
        val url = "$GEOCODING_URL?name=$q&count=$count&language=$language"
        val body = httpGet(url)
        val json = org.json.JSONObject(body)
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val o = results.optJSONObject(i) ?: return@mapNotNull null
            val admin1 = o.optString("admin1").ifBlank { null }
            val country = o.optString("country").ifBlank { null }
            City(
                id = "${o.optDouble("latitude")},${o.optDouble("longitude")}",
                name = o.optString("name"),
                latitude = o.optDouble("latitude"),
                longitude = o.optDouble("longitude"),
                // 用 admin1（省份）替代"中国"，帮助区分同名结果
                country = admin1 ?: country
            )
        }
    }

    override suspend fun fetchAQI(latitude: Double, longitude: Double): AQIData? =
        withContext(Dispatchers.IO) {
            val url = "$AQI_URL?latitude=$latitude&longitude=$longitude" +
                "&current=pm2_5,pm10,ozone,nitrogen_dioxide,carbon_monoxide,sulphur_dioxide" +
                "&timezone=Asia%2FShanghai"
            try {
                val body = httpGet(url)
                val json = org.json.JSONObject(body)
                val cur = json.optJSONObject("current") ?: return@withContext null
                AQIData(
                    pm25 = cur.optDouble("pm2_5", Double.NaN).takeIf { !it.isNaN() },
                    pm10 = cur.optDouble("pm10", Double.NaN).takeIf { !it.isNaN() },
                    ozone = cur.optDouble("ozone", Double.NaN).takeIf { !it.isNaN() },
                    nitrogenDioxide = cur.optDouble("nitrogen_dioxide", Double.NaN).takeIf { !it.isNaN() },
                    carbonMonoxide = cur.optDouble("carbon_monoxide", Double.NaN).takeIf { !it.isNaN() },
                    sulphurDioxide = cur.optDouble("sulphur_dioxide", Double.NaN).takeIf { !it.isNaN() }
                )
            } catch (_: Exception) { null }
        }

    private fun buildForecastUrl(lat: Double, lon: Double): String {
        val current = listOf(
            "temperature_2m", "relative_humidity_2m", "apparent_temperature",
            "weather_code", "wind_speed_10m", "wind_direction_10m",
            "surface_pressure", "is_day"
        ).joinToString(",")
        val hourly = listOf(
            "temperature_2m", "precipitation_probability", "weather_code", "wind_speed_10m"
        ).joinToString(",")
        val daily = listOf(
            "weather_code", "temperature_2m_max", "temperature_2m_min",
            "precipitation_probability_max", "sunrise", "sunset"
        ).joinToString(",")
        return "$FORECAST_URL?" +
            "latitude=$lat&longitude=$lon" +
            "&current=$current" +
            "&hourly=$hourly" +
            "&daily=$daily" +
            "&timezone=Asia%2FShanghai" +
            "&forecast_days=16"
    }

    private fun httpGet(urlStr: String, userAgent: String? = null): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
            userAgent?.let { setRequestProperty("User-Agent", it) }
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw WeatherNetworkException("HTTP $code from $urlStr: $text")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }
}

class WeatherNetworkException(message: String) : Exception(message)
