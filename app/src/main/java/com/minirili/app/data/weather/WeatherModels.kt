package com.minirili.app.data.weather

/** 天气领域模型，UI 直接消费。WeatherRepository 负责从数据源 / 缓存构造这些对象。 */

data class WeatherData(
    val cityName: String,
    val latitude: Double,
    val longitude: Double,
    val fetchedAt: Long,
    val current: CurrentWeather,
    val hourly: List<HourlyWeather>,
    val daily: List<DailyWeather>
)

data class CurrentWeather(
    val timeMillis: Long,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val weatherCode: Int,
    val windSpeed: Double,
    val windDirection: Int,
    val pressure: Double,
    val isDay: Boolean
)

data class HourlyWeather(
    val timeMillis: Long,
    val temperature: Double,
    val precipitationProbability: Int,
    val weatherCode: Int,
    val windSpeed: Double
)

data class DailyWeather(
    val date: String,
    val weatherCode: Int,
    val tempMax: Double,
    val tempMin: Double,
    val precipitationProbabilityMax: Int,
    val sunriseMillis: Long?,
    val sunsetMillis: Long?
)

/** 城市（多城市管理） */
data class City(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?,
    val isCurrentLocation: Boolean = false
)

/** AQI 数据（WTH-07，Open-Meteo Air Quality API） */
data class AQIData(
    val pm25: Double?,      // PM2.5 (μg/m³)
    val pm10: Double?,      // PM10 (μg/m³)
    val ozone: Double?,     // O₃ (μg/m³)
    val nitrogenDioxide: Double?, // NO₂ (μg/m³)
    val carbonMonoxide: Double?,  // CO (μg/m³)
    val sulphurDioxide: Double?  // SO₂ (μg/m³)
) {
    /** 根据 PM2.5 计算 AQI 等级 */
    val level: String get() = when {
        pm25 == null -> "无数据"
        pm25 <= 35 -> "优"
        pm25 <= 75 -> "良"
        pm25 <= 115 -> "轻度污染"
        pm25 <= 150 -> "中度污染"
        pm25 <= 250 -> "重度污染"
        else -> "严重污染"
    }
}

/** WMO weather code → 中文描述 + 简化图标字符（文本图标，无图片资源） */
object WeatherCode {
    fun description(code: Int): String = when (code) {
        0 -> "晴"
        1 -> "大致晴朗"
        2 -> "局部多云"
        3 -> "阴天"
        45, 48 -> "雾"
        51, 53, 55 -> "毛毛雨"
        56, 57 -> "冻毛毛雨"
        61, 63, 65 -> "雨"
        66, 67 -> "冻雨"
        71, 73, 75 -> "雪"
        77 -> "雪粒"
        80, 81, 82 -> "阵雨"
        85, 86 -> "阵雪"
        95 -> "雷暴"
        96, 99 -> "雷暴伴冰雹"
        else -> "未知"
    }

    /** 简易文本图标，替代图片资源保持包体积极小 */
    fun icon(code: Int, isDay: Boolean = true): String = when (code) {
        0 -> if (isDay) "☀" else "☾"
        1 -> if (isDay) "🌤" else "☁"
        2 -> "⛅"
        3 -> "☁"
        45, 48 -> "🌫"
        in 51..57 -> "🌦"
        in 61..67 -> "🌧"
        in 71..77 -> "🌨"
        in 80..82 -> "🌦"
        in 85..86 -> "🌨"
        95, 96, 99 -> "⛈"
        else -> "？"
    }
}
