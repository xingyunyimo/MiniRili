package com.minirili.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.minirili.app.MainActivity
import com.minirili.app.R
import com.minirili.app.data.weather.City
import com.minirili.app.data.weather.OpenMeteoApi
import com.minirili.app.data.weather.WeatherCode
import com.minirili.app.data.weather.WeatherRepository
import com.minirili.app.data.weather.WeatherResult
import com.minirili.app.database.CalendarDatabase
import kotlinx.coroutines.runBlocking

/**
 * 4×1 天气桌面小部件（WTH-09）。
 *
 * 显示当前城市名 + 天气图标 + 温度 + 天气描述。
 * 点击打开天气详情页。每 30 分钟自动刷新。
 */
class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        private val DEFAULT_BEIJING = City(
            id = "39.9042,116.4074", name = "北京",
            latitude = 39.9042, longitude = 116.4074, country = "中国"
        )

        private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.weather_widget)

            try {
                val result = runBlocking {
                    val db = CalendarDatabase.getDatabase(context)
                    val repository = WeatherRepository(
                        dataSource = OpenMeteoApi(),
                        weatherCacheDao = db.weatherCacheDao(),
                        cityDao = db.cityDao()
                    )
                    val cities = repository.getCities()
                    val city = cities.firstOrNull() ?: DEFAULT_BEIJING
                    views.setTextViewText(R.id.widget_weather_city, city.name)
                    repository.getCurrentWeather(city)
                }

                if (result is WeatherResult.ForDate) {
                    val cur = result.current
                    views.setTextViewText(R.id.widget_weather_icon, WeatherCode.icon(cur.weatherCode, cur.isDay))
                    views.setTextViewText(R.id.widget_weather_temp, "${cur.temperature.toInt()}°")
                    views.setTextViewText(R.id.widget_weather_desc, WeatherCode.description(cur.weatherCode))
                } else {
                    views.setTextViewText(R.id.widget_weather_temp, "--°")
                    views.setTextViewText(R.id.widget_weather_desc, "天气暂不可用")
                }
            } catch (_: Exception) {
                views.setTextViewText(R.id.widget_weather_temp, "--°")
                views.setTextViewText(R.id.widget_weather_desc, "加载失败")
            }

            // 点击打开天气页
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("navigate_to", "weather")
            }
            val pendingIntent = PendingIntent.getActivity(
                context, appWidgetId, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_weather_city, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}