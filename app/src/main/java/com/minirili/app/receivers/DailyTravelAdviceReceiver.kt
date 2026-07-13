package com.minirili.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.minirili.app.database.CalendarDatabase
import com.minirili.app.data.weather.OpenMeteoApi
import com.minirili.app.data.weather.WeatherCode
import com.minirili.app.data.weather.WeatherRepository
import com.minirili.app.data.weather.WeatherResult
import com.minirili.app.utils.NotificationHelper
import com.minirili.app.utils.TravelAdviceEngine
import com.minirili.app.utils.TravelAdvicePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 每日出行建议通知接收器（Bug6）。
 *
 * 由 TravelAdvicePrefs.reschedule 投放的 AlarmManager 触发；
 * 触发后查天气并发出通知，然后自动续约下一次。
 */
class DailyTravelAdviceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val db = CalendarDatabase.getDatabase(context)
                val repository = WeatherRepository(
                    dataSource = OpenMeteoApi(),
                    weatherCacheDao = db.weatherCacheDao(),
                    cityDao = db.cityDao()
                )
                val cities = repository.getCities()
                val city = cities.firstOrNull() ?: DEFAULT_BEIJING

                val result = repository.getCurrentWeather(city)
                if (result is WeatherResult.ForDate) {
                    val advice = TravelAdviceEngine.getAdvice(result.current, result.daily.firstOrNull())
                    val weatherDesc = WeatherCode.description(result.current.weatherCode)
                    val temp = "${result.current.temperature.toInt()}°"
                    val title = "今日出行 · ${city.name}"
                    val lines = mutableListOf("$weatherDesc $temp，建议：")
                    if (advice.isEmpty()) {
                        lines.add("天气尚可，按需出行")
                    } else {
                        lines.addAll(advice.take(3))
                    }
                    NotificationHelper.createNotificationChannel(context)
                    val notification = NotificationHelper.buildWeatherNotification(
                        context = context,
                        title = title,
                        content = lines.joinToString("\n")
                    )
                    NotificationHelper.sendNotification(context, notification)
                }
            }
            // 续约下一次
            TravelAdvicePrefs.reschedule(context.applicationContext)
        }
    }

    companion object {
        val DEFAULT_BEIJING = com.minirili.app.data.weather.City(
            id = "39.9042,116.4074",
            name = "北京",
            latitude = 39.9042,
            longitude = 116.4074,
            country = "中国"
        )
    }
}
