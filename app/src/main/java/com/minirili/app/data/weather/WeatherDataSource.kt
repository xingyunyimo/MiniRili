package com.minirili.app.data.weather

/**
 * 天气数据源接口。业务层只依赖此接口，底层实现可替换（MVP 用 Open-Meteo，V2/V3 可无缝接和风 / 自部署）。
 */
interface WeatherDataSource {
    suspend fun fetchWeather(latitude: Double, longitude: Double): RawWeatherResponse
    suspend fun searchCity(name: String, language: String = "zh", count: Int = 5): List<City>
    suspend fun fetchAQI(latitude: Double, longitude: Double): AQIData?
}

/** Open-Meteo 一次 forecast 调用的原始返回。后续由 WeatherRepository 解析为 WeatherData。 */
data class RawWeatherResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val current: JsonObject?,
    val hourly: JsonObject?,
    val daily: JsonObject?
)

/** org.json 的薄封装，避免在接口里暴露具体类型 */
typealias JsonObject = org.json.JSONObject
