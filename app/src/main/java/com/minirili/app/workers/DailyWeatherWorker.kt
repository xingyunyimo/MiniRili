package com.minirili.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.minirili.app.data.weather.City
import com.minirili.app.data.weather.OpenMeteoApi
import com.minirili.app.data.weather.WeatherCode
import com.minirili.app.data.weather.WeatherRepository
import com.minirili.app.data.weather.WeatherResult
import com.minirili.app.database.CalendarDatabase
import com.minirili.app.utils.NotificationHelper
import com.minirili.app.utils.TravelAdviceEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 每日天气通知 Worker（v1.3 WTH-05）。
 *
 * 定时获取今日天气预报 + 出行建议，推送通知到状态栏。
 * 默认 7:00 执行（由 [com.minirili.app.CalendarApplication] 注册 PeriodicWorkRequest）。
 */
class DailyWeatherWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = CalendarDatabase.getDatabase(applicationContext)
            val repository = WeatherRepository(
                dataSource = OpenMeteoApi(),
                weatherCacheDao = db.weatherCacheDao(),
                cityDao = db.cityDao()
            )

            val cities = repository.getCities()
            val city = cities.firstOrNull() ?: DEFAULT_BEIJING

            val result = repository.getCurrentWeather(city)
            if (result !is WeatherResult.ForDate) return Result.retry()

            val advice = TravelAdviceEngine.getAdvice(result.current, result.daily.firstOrNull())
            val weatherDesc = WeatherCode.description(result.current.weatherCode)
            val temp = "${result.current.temperature.toInt()}°"

            // 构建通知内容
            val title = "今日天气 · ${city.name}"
            val lines = mutableListOf("${weatherDesc}  ${temp}（${result.daily.firstOrNull()?.let { "${it.tempMin.toInt()}°~${it.tempMax.toInt()}°"} ?: ""}）")
            if (advice.isNotEmpty()) {
                lines.addAll(advice.take(3)) // 最多3条建议
            }

            val notification = NotificationHelper.buildWeatherNotification(
                context = applicationContext,
                title = title,
                content = lines.joinToString("\n")
            )
            NotificationHelper.sendNotification(applicationContext, notification)

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        val DEFAULT_BEIJING = City(
            id = "39.9042,116.4074",
            name = "北京",
            latitude = 39.9042,
            longitude = 116.4074,
            country = "中国"
        )
    }
}