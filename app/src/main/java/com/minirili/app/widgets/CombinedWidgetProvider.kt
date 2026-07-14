package com.minirili.app.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.minirili.app.MainActivity
import com.minirili.app.R
import com.minirili.app.database.CalendarDatabase
import com.minirili.app.data.HolidayService
import com.minirili.app.data.weather.City
import com.minirili.app.data.weather.OpenMeteoApi
import com.minirili.app.data.weather.WeatherCode
import com.minirili.app.data.weather.WeatherRepository
import com.minirili.app.data.weather.WeatherResult
import com.minirili.app.utils.DateUtils
import com.minirili.app.utils.LunarCalendar
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CombinedWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_TIME_FORMAT -> {
                toggleTimeFormat(context)
                return
            }
            ACTION_TOGGLE_BG_MODE -> {
                val p = prefs(context)
                p.edit().putBoolean(PREF_TRANSPARENT, !p.getBoolean(PREF_TRANSPARENT, false)).apply()
                refreshAll(context)
                return
            }
            ACTION_CYCLE_EVENT -> {
                cycleEvents(context)
                return
            }
            ACTION_REFRESH_WEATHER -> {
                // 后台线程刷新天气，避免阻塞 BroadcastReceiver 主线程
                Thread {
                    runCatching { refreshWeather(context) }
                }.start()
                // 续约下一次 AlarmManager
                scheduleWeatherAlarm(context)
                return
            }
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val appContext = context.applicationContext
        for (appWidgetId in appWidgetIds) {
            try {
                appWidgetManager.updateAppWidget(appWidgetId, buildStaticViews(appContext, appWidgetId))
            } catch (e: Throwable) {
                Log.e(TAG, "static render failed id=$appWidgetId", e)
            }
        }
        // 启动独立 1 秒时间刷新
        startTimeTick(appContext)
        // 后台线程做动态渲染（天气网络请求 + Room 查询）
        Thread {
            for (appWidgetId in appWidgetIds) {
                try {
                    appWidgetManager.updateAppWidget(appWidgetId, buildDynamicViews(appContext, appWidgetId))
                } catch (e: Throwable) {
                    Log.e(TAG, "dynamic render failed id=$appWidgetId", e)
                }
            }
            // 动态渲染完成后启动天气 Handler 兜底 + AlarmManager
            startWeatherHandler(appContext)
            scheduleWeatherAlarm(appContext)
        }.start()
    }

    override fun onEnabled(context: Context) {
        val am = AppWidgetManager.getInstance(context)
        val ids = am.getAppWidgetIds(ComponentName(context, CombinedWidgetProvider::class.java))
        if (ids.isNotEmpty()) onUpdate(context, am, ids)
    }

    override fun onDisabled(context: Context) {
        stopTimeTick()
        stopWeatherHandler()
        cancelWeatherAlarm(context)
        cancelEventCycle()
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val am = AppWidgetManager.getInstance(context)
        onUpdate(context, am, newWidgetIds)
    }

    // ===== 颜色方案 =====
    private data class ColorScheme(
        val bgColor: Int,
        val primary: String,
        val secondary: String,
        val divider: String,
        val toggleIcon: String
    )

    private fun getColorScheme(context: Context, isDark: Boolean): ColorScheme {
        val isTransparent = prefs(context).getBoolean(PREF_TRANSPARENT, false)
        return if (isTransparent) {
            ColorScheme(
                bgColor = Color.TRANSPARENT,
                primary = "#FFFFFF",
                secondary = "#B3FFFFFF",
                divider = "#33FFFFFF",
                toggleIcon = "◆"
            )
        } else if (isDark) {
            ColorScheme(
                bgColor = Color.parseColor("#F21E1E1E"),
                primary = "#E0E0E0",
                secondary = "#AAAAAA",
                divider = "#33FFFFFF",
                toggleIcon = "◇"
            )
        } else {
            ColorScheme(
                bgColor = Color.parseColor("#F2F5F5F5"),
                primary = "#1A1A1A",
                secondary = "#666666",
                divider = "#33000000",
                toggleIcon = "◇"
            )
        }
    }

    // ===== 静态渲染 =====
    private fun buildStaticViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.combined_widget_4x3)
        applyTheme(context, views)
        views.setTextViewText(R.id.widget_date, "")
        views.setTextViewText(R.id.widget_lunar_date, "加载中…")
        views.setTextViewText(R.id.widget_event_count, "今日 — 项事件")
        views.setTextViewText(R.id.widget_weather_desc, "天气加载中…")
        views.setTextViewText(R.id.widget_feels_like, "")
        views.setViewVisibility(R.id.widget_event_empty, View.GONE)
        views.setViewVisibility(R.id.widget_aqi_label, View.GONE)
        views.setViewVisibility(R.id.widget_aqi_value, View.GONE)
        views.setViewVisibility(R.id.widget_weather_empty, View.GONE)
        views.setViewVisibility(R.id.widget_event_color, View.GONE)
        views.setViewVisibility(R.id.widget_event_time, View.GONE)
        views.setViewVisibility(R.id.widget_event_time_space, View.GONE)
        views.setViewVisibility(R.id.widget_event_title, View.GONE)
        views.setViewVisibility(R.id.widget_holiday_tag, View.GONE)
        views.setTextViewText(R.id.widget_weekday, "")
        views.setTextViewText(R.id.widget_city, "")
        views.setTextViewText(R.id.widget_weather_icon, "")
        views.setTextViewText(R.id.widget_temp, "")
        setTimeText(views, context)
        setClickIntents(context, views, appWidgetId)
        return views
    }

    // ===== 动态渲染 =====
    private fun buildDynamicViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.combined_widget_4x3)
        applyTheme(context, views)

        runCatching { setTimeSection(views, context) }
            .onFailure { Log.w(TAG, "time section failed", it) }
        runCatching { setWeatherSection(context, views) }
            .onFailure { Log.w(TAG, "weather section failed", it) }
        runCatching { setEventsSection(context, views) }
            .onFailure { Log.w(TAG, "events section failed", it) }

        runCatching { setClickIntents(context, views, appWidgetId) }

        return views
    }

    // ===== 主题应用 =====
    private fun applyTheme(context: Context, views: RemoteViews) {
        val isDark = runCatching { isDarkMode(context) }.getOrDefault(false)
        val scheme = getColorScheme(context, isDark)

        runCatching { views.setInt(R.id.combined_widget_root, "setBackgroundColor", scheme.bgColor) }
        runCatching { views.setTextColor(R.id.widget_date, Color.parseColor(scheme.primary)) }
        runCatching { views.setTextColor(R.id.widget_weekday, Color.parseColor(scheme.primary)) }
        runCatching { views.setTextColor(R.id.widget_lunar_date, Color.parseColor(scheme.secondary)) }
        runCatching { views.setTextColor(R.id.widget_time, Color.parseColor(scheme.primary)) }
        runCatching { views.setTextColor(R.id.widget_city, Color.parseColor(scheme.primary)) }
        runCatching { views.setTextColor(R.id.widget_weather_desc, Color.parseColor(scheme.secondary)) }
        runCatching { views.setTextColor(R.id.widget_weather_empty, Color.parseColor(scheme.secondary)) }
        runCatching { views.setTextColor(R.id.widget_event_count, Color.parseColor(scheme.primary)) }
        runCatching { views.setTextColor(R.id.widget_event_title, Color.parseColor(scheme.primary)) }
        runCatching { views.setTextColor(R.id.widget_event_time, Color.parseColor(scheme.secondary)) }
        runCatching { views.setTextColor(R.id.widget_event_empty, Color.parseColor(scheme.secondary)) }
        runCatching { views.setTextColor(R.id.widget_feels_like, Color.parseColor(scheme.secondary)) }
        runCatching { views.setTextViewText(R.id.widget_bg_toggle, scheme.toggleIcon) }
        runCatching { views.setTextColor(R.id.widget_bg_toggle, Color.parseColor(scheme.secondary)) }
        runCatching { views.setTextColor(R.id.widget_new_event, Color.parseColor(scheme.secondary)) }
        runCatching { views.setInt(R.id.widget_divider_top, "setBackgroundColor", Color.parseColor(scheme.divider)) }
        runCatching { views.setInt(R.id.widget_divider_bottom, "setBackgroundColor", Color.parseColor(scheme.divider)) }
    }

    // ===== 时间模块（全量更新） =====
    private fun setTimeSection(views: RemoteViews, context: Context) {
        val now = Calendar.getInstance()
        val month = now.get(Calendar.MONTH) + 1
        val day = now.get(Calendar.DAY_OF_MONTH)
        views.setTextViewText(R.id.widget_date, "${month}月${day}日")

        val dow = now.get(Calendar.DAY_OF_WEEK)
        views.setTextViewText(R.id.widget_weekday, DateUtils.getWeekdayFull(dow))

        setTimeText(views, context)

        val lunarMonthDay = runCatching { LunarCalendar.getLunarMonthDayName(now) }.getOrDefault("")
        views.setTextViewText(R.id.widget_lunar_date, lunarMonthDay.ifEmpty { "" })

        val todayStr = DateUtils.today()
        val holiday = HolidayService.getHolidayName(todayStr)
        if (holiday != null) {
            views.setTextViewText(R.id.widget_holiday_tag, holiday)
            views.setViewVisibility(R.id.widget_holiday_tag, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_holiday_tag, View.GONE)
        }
    }

    // ===== 时间文本（仅更新时间 HH:mm，轻量） =====
    private fun setTimeText(views: RemoteViews, context: Context) {
        val is24h = prefs(context).getBoolean(PREF_24H, true)
        val now = Calendar.getInstance()
        val fmt = if (is24h) SimpleDateFormat("HH:mm", Locale.getDefault())
                  else SimpleDateFormat("h:mm a", Locale.getDefault())
        views.setTextViewText(R.id.widget_time, fmt.format(now.time))
    }

    // ===== 天气模块 =====
    private fun setWeatherSection(context: Context, views: RemoteViews) {
        val db = CalendarDatabase.getDatabase(context)

        val weatherResult = try {
            runBlocking {
                val repo = WeatherRepository(
                    dataSource = OpenMeteoApi(),
                    weatherCacheDao = db.weatherCacheDao(),
                    cityDao = db.cityDao()
                )
                val cities = repo.getCities()
                val city = cities.firstOrNull() ?: DEFAULT_CITY
                views.setTextViewText(R.id.widget_city, city.name)
                repo.getCurrentWeather(city)
            }
        } catch (_: Throwable) { null }

        if (weatherResult !is WeatherResult.ForDate) {
            showWeatherEmpty(views)
            return
        }

        val cur = weatherResult.current
        views.setTextViewText(R.id.widget_weather_icon, WeatherCode.icon(cur.weatherCode, cur.isDay))
        views.setTextViewText(R.id.widget_temp, "${cur.temperature.toInt()}°")
        views.setTextViewText(R.id.widget_weather_desc, WeatherCode.description(cur.weatherCode))
        views.setTextViewText(R.id.widget_feels_like, "体感 ${cur.apparentTemperature.toInt()}°")

        val tempColor = when {
            cur.temperature >= 28 -> Color.parseColor("#E53935")
            cur.temperature >= 20 -> Color.parseColor("#FF6F00")
            cur.temperature >= 10 -> Color.parseColor("#1A1A1A")
            else -> Color.parseColor("#1976D2")
        }
        views.setTextColor(R.id.widget_temp, tempColor)

        val aqi = try {
            runBlocking {
                val repo = WeatherRepository(
                    dataSource = OpenMeteoApi(),
                    weatherCacheDao = db.weatherCacheDao(),
                    cityDao = db.cityDao()
                )
                val cities = repo.getCities()
                val city = cities.firstOrNull() ?: DEFAULT_CITY
                repo.getAQI(city)
            }
        } catch (_: Throwable) { null }

        if (aqi != null) {
            views.setTextViewText(R.id.widget_aqi_label, aqi.level)
            views.setTextViewText(R.id.widget_aqi_value, "AQI ${aqi.pm25?.toInt() ?: aqi.pm10?.toInt() ?: 0}")
            val aqiColor = when (aqi.level) {
                "优" -> Color.parseColor("#4CAF50")
                "良" -> Color.parseColor("#8BC34A")
                "轻度污染" -> Color.parseColor("#FFC107")
                "中度污染" -> Color.parseColor("#FF9800")
                "重度污染" -> Color.parseColor("#F44336")
                "严重污染" -> Color.parseColor("#880E4F")
                else -> Color.parseColor("#666666")
            }
            views.setTextColor(R.id.widget_aqi_label, aqiColor)
            views.setTextColor(R.id.widget_aqi_value, aqiColor)
            views.setViewVisibility(R.id.widget_aqi_label, View.VISIBLE)
            views.setViewVisibility(R.id.widget_aqi_value, View.VISIBLE)
            views.setViewVisibility(R.id.widget_weather_empty, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_aqi_label, View.GONE)
            views.setViewVisibility(R.id.widget_aqi_value, View.GONE)
            views.setViewVisibility(R.id.widget_weather_empty, View.VISIBLE)
            views.setTextViewText(R.id.widget_weather_empty, "AQI 暂不可用")
        }
    }

    private fun showWeatherEmpty(views: RemoteViews) {
        views.setTextViewText(R.id.widget_temp, "--°")
        views.setTextViewText(R.id.widget_weather_icon, "☁")
        views.setTextViewText(R.id.widget_weather_desc, "天气暂不可用")
        views.setTextViewText(R.id.widget_feels_like, "")
        views.setViewVisibility(R.id.widget_weather_empty, View.VISIBLE)
        views.setTextViewText(R.id.widget_weather_empty, "点击刷新")
        views.setViewVisibility(R.id.widget_aqi_label, View.GONE)
        views.setViewVisibility(R.id.widget_aqi_value, View.GONE)
    }

    // ===== 事件模块（含循环滚动） =====
    private fun setEventsSection(context: Context, views: RemoteViews) {
        val db = CalendarDatabase.getDatabase(context)

        val todayEvents = try {
            runBlocking { db.eventDao().getEventsByDate(DateUtils.today()).firstOrNull() ?: emptyList() }
        } catch (_: Throwable) { null }

        if (todayEvents == null) {
            views.setTextViewText(R.id.widget_event_count, "今日 -- 项事件")
            views.setViewVisibility(R.id.widget_event_color, View.GONE)
            views.setViewVisibility(R.id.widget_event_time, View.GONE)
            views.setViewVisibility(R.id.widget_event_time_space, View.GONE)
            views.setViewVisibility(R.id.widget_event_title, View.GONE)
            views.setViewVisibility(R.id.widget_event_empty, View.VISIBLE)
            views.setTextViewText(R.id.widget_event_empty, "加载中…")
            cancelEventCycle()
            return
        }

        if (todayEvents.isEmpty()) {
            views.setTextViewText(R.id.widget_event_count, "今日 0 项事件")
            views.setViewVisibility(R.id.widget_event_color, View.GONE)
            views.setViewVisibility(R.id.widget_event_time, View.GONE)
            views.setViewVisibility(R.id.widget_event_time_space, View.GONE)
            views.setViewVisibility(R.id.widget_event_title, View.GONE)
            views.setViewVisibility(R.id.widget_event_empty, View.VISIBLE)
            views.setTextViewText(R.id.widget_event_empty, "暂无事件，点击新建")
            cancelEventCycle()
        } else {
            val eventIndex = prefs(context).getInt(PREF_EVENT_INDEX, 0) % todayEvents.size
            views.setTextViewText(R.id.widget_event_count, "今日 ${todayEvents.size} 项事件")

            val event = todayEvents[eventIndex]
            views.setViewVisibility(R.id.widget_event_empty, View.GONE)

            if (event.color != 0) {
                val opaqueColor = event.color or (0xFF shl 24).toInt()
                runCatching { views.setInt(R.id.widget_event_color, "setBackgroundColor", opaqueColor) }
                views.setViewVisibility(R.id.widget_event_color, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_event_color, View.GONE)
            }

            val timeText = if (event.reminderTime > 0) {
                runCatching {
                    SimpleDateFormat("HH:mm", Locale.getDefault())
                        .format(java.util.Date(event.reminderTime))
                }.getOrDefault("")
            } else "全天"

            val titleText = if (event.completed) "✓ ${event.title}" else event.title

            if (timeText.isNotEmpty()) {
                views.setTextViewText(R.id.widget_event_time, timeText)
                views.setViewVisibility(R.id.widget_event_time, View.VISIBLE)
                views.setViewVisibility(R.id.widget_event_time_space, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_event_time, View.GONE)
                views.setViewVisibility(R.id.widget_event_time_space, View.GONE)
            }

            views.setTextViewText(R.id.widget_event_title, titleText)
            views.setViewVisibility(R.id.widget_event_title, View.VISIBLE)
            if (event.completed) {
                views.setTextColor(R.id.widget_event_title, Color.parseColor("#999999"))
            }

            // 多事件时启动循环滚动
            if (todayEvents.size > 1) {
                scheduleEventCycle(context)
            } else {
                cancelEventCycle()
            }
        }
    }

    // ===== 事件循环滚动（Handler 实现，5 秒间隔） =====
    private fun scheduleEventCycle(context: Context) {
        cancelEventCycle()
        val handler = Handler(context.mainLooper)
        val runnable = Runnable {
            runCatching { cycleEvents(context) }
        }
        sEventCycleHandler = handler
        sEventCycleRunnable = runnable
        handler.postDelayed(runnable, 5000)
    }

    private fun cancelEventCycle() {
        sEventCycleRunnable?.let { sEventCycleHandler?.removeCallbacks(it) }
        sEventCycleRunnable = null
        sEventCycleHandler = null
    }

    /** 事件循环 —— 只更新事件模块，不碰时间/天气（使用 partialUpdate 避免覆盖） */
    private fun cycleEvents(context: Context) {
        val p = prefs(context)
        p.edit().putInt(PREF_EVENT_INDEX, p.getInt(PREF_EVENT_INDEX, 0) + 1).apply()

        val views = RemoteViews(context.packageName, R.layout.combined_widget_4x3)
        setEventsSection(context, views)
        setClickIntents(context, views, 0)

        val am = AppWidgetManager.getInstance(context)
        for (id in am.getAppWidgetIds(ComponentName(context, CombinedWidgetProvider::class.java))) {
            am.partiallyUpdateAppWidget(id, views)
        }
    }

    /** 时间格式切换 —— 只更新时间文本 */
    private fun toggleTimeFormat(context: Context) {
        val p = prefs(context)
        p.edit().putBoolean(PREF_24H, !p.getBoolean(PREF_24H, true)).apply()

        val views = RemoteViews(context.packageName, R.layout.combined_widget_4x3)
        setTimeText(views, context)

        val am = AppWidgetManager.getInstance(context)
        for (id in am.getAppWidgetIds(ComponentName(context, CombinedWidgetProvider::class.java))) {
            am.partiallyUpdateAppWidget(id, views)
        }
    }

    // ===== 独立时间刷新（1 秒间隔） =====
    private fun startTimeTick(context: Context) {
        stopTimeTick()
        val handler = Handler(context.mainLooper)
        val runnable = Runnable {
            runCatching {
                val views = RemoteViews(context.packageName, R.layout.combined_widget_4x3)
                setTimeText(views, context)
                val am = AppWidgetManager.getInstance(context)
                for (id in am.getAppWidgetIds(ComponentName(context, CombinedWidgetProvider::class.java))) {
                    am.partiallyUpdateAppWidget(id, views)
                }
            }
            // 续约下一次
            val currentRunnable = sTimeTickRunnable
            if (currentRunnable != null) {
                sTimeTickHandler?.postDelayed(currentRunnable, 1000)
            }
        }
        val tickRunnable = runnable
        sTimeTickHandler = handler
        sTimeTickRunnable = tickRunnable
        handler.post(tickRunnable)
    }

    private fun stopTimeTick() {
        sTimeTickRunnable?.let { sTimeTickHandler?.removeCallbacks(it) }
        sTimeTickRunnable = null
        sTimeTickHandler = null
    }

    // ===== 天气刷新（Handler 兜底，30 分钟间隔） =====
    private fun startWeatherHandler(context: Context) {
        stopWeatherHandler()
        val handler = Handler(context.mainLooper)
        val runnable = Runnable {
            runCatching { refreshWeather(context) }
            // 续约下一次
            val currentRunnable = sWeatherRunnable
            if (currentRunnable != null) {
                sWeatherHandler?.postDelayed(currentRunnable, WEATHER_REFRESH_INTERVAL)
            }
        }
        val weatherRunnable = runnable
        sWeatherHandler = handler
        sWeatherRunnable = weatherRunnable
        handler.postDelayed(weatherRunnable, WEATHER_REFRESH_INTERVAL)
    }

    private fun stopWeatherHandler() {
        sWeatherRunnable?.let { sWeatherHandler?.removeCallbacks(it) }
        sWeatherRunnable = null
        sWeatherHandler = null
    }

    /** 全量刷新天气及时间/事件（通过 buildDynamicViews） */
    private fun refreshWeather(context: Context) {
        val views = buildDynamicViews(context, 0)
        val am = AppWidgetManager.getInstance(context)
        for (id in am.getAppWidgetIds(ComponentName(context, CombinedWidgetProvider::class.java))) {
            am.updateAppWidget(id, views)
        }
    }

    // ===== AlarmManager 天气刷新（进程被杀后仍能触发） =====
    private fun scheduleWeatherAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, CombinedWidgetProvider::class.java).apply {
            action = ACTION_REFRESH_WEATHER
        }
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE_WEATHER, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        runCatching { am.cancel(pi) }

        val triggerAt = System.currentTimeMillis() + WEATHER_REFRESH_INTERVAL
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            runCatching { am.setInexactRepeating(AlarmManager.RTC_WAKEUP, triggerAt, 3600000, pi) }
        }
    }

    private fun cancelWeatherAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, CombinedWidgetProvider::class.java).apply {
            action = ACTION_REFRESH_WEATHER
        }
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE_WEATHER, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        runCatching { am.cancel(pi) }
    }

    // ===== 点击跳转 =====
    private fun setClickIntents(context: Context, views: RemoteViews, appWidgetId: Int) {
        val piFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val cls = CombinedWidgetProvider::class.java

        // 点击日期 → 日历月视图
        val calendarIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        views.setOnClickPendingIntent(R.id.widget_date,
            PendingIntent.getActivity(context, appWidgetId * 3, calendarIntent, piFlags))

        // 点击时间 → 切换 12h / 24h
        val toggleTime = Intent(context, cls).apply { action = ACTION_TOGGLE_TIME_FORMAT }
        views.setOnClickPendingIntent(R.id.widget_time,
            PendingIntent.getBroadcast(context, appWidgetId * 3 + 4, toggleTime, piFlags))

        // 点击背景切换按钮 → 切换白底/透明模式
        val toggleBg = Intent(context, cls).apply { action = ACTION_TOGGLE_BG_MODE }
        views.setOnClickPendingIntent(R.id.widget_bg_toggle,
            PendingIntent.getBroadcast(context, appWidgetId * 3 + 5, toggleBg, piFlags))

        // 点击 + 按钮 → 新建事件
        val newEventIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("navigate_to", "new_event")
        }
        views.setOnClickPendingIntent(R.id.widget_new_event,
            PendingIntent.getActivity(context, appWidgetId * 3 + 9, newEventIntent, piFlags))

        // 点击天气图标 / 温度 → 天气详情页
        val weatherIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("navigate_to", "weather")
        }
        views.setOnClickPendingIntent(R.id.widget_weather_icon,
            PendingIntent.getActivity(context, appWidgetId * 3 + 1, weatherIntent, piFlags))
        views.setOnClickPendingIntent(R.id.widget_temp,
            PendingIntent.getActivity(context, appWidgetId * 3 + 2, weatherIntent, piFlags))

        // 点击事件区 → 跳转日历日视图
        val eventsIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("navigate_to", "day")
        }
        views.setOnClickPendingIntent(R.id.widget_event_count,
            PendingIntent.getActivity(context, appWidgetId * 3 + 3, eventsIntent, piFlags))
        views.setOnClickPendingIntent(R.id.widget_event_row,
            PendingIntent.getActivity(context, appWidgetId * 3 + 6, eventsIntent, piFlags))
        views.setOnClickPendingIntent(R.id.widget_event_title,
            PendingIntent.getActivity(context, appWidgetId * 3 + 7, eventsIntent, piFlags))
        views.setOnClickPendingIntent(R.id.widget_event_empty,
            PendingIntent.getActivity(context, appWidgetId * 3 + 8, eventsIntent, piFlags))
    }

    private fun isDarkMode(context: Context): Boolean {
        return (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun refreshAll(context: Context) {
        val am = AppWidgetManager.getInstance(context)
        val ids = am.getAppWidgetIds(ComponentName(context, CombinedWidgetProvider::class.java))
        onUpdate(context, am, ids)
    }

    companion object {
        private const val TAG = "CombinedWidget"
        private const val ACTION_TOGGLE_TIME_FORMAT = "com.minirili.app.TOGGLE_TIME_FORMAT"
        private const val ACTION_TOGGLE_BG_MODE = "com.minirili.app.TOGGLE_BG_MODE"
        private const val ACTION_CYCLE_EVENT = "com.minirili.app.CYCLE_EVENT"
        private const val ACTION_REFRESH_WEATHER = "com.minirili.app.REFRESH_WEATHER"

        private const val REQUEST_CODE_CYCLE = 0xC1C1
        private const val REQUEST_CODE_WEATHER = 0xC1C2
        private const val WEATHER_REFRESH_INTERVAL = 30 * 60 * 1000L // 30 分钟

        private const val PREF_NAME = "widget_prefs"
        private const val PREF_24H = "time_24h"
        private const val PREF_TRANSPARENT = "bg_transparent"
        private const val PREF_EVENT_INDEX = "event_index"

        /* 时间 1 秒刷新（static 避免实例重建后丢失） */
        private var sTimeTickHandler: Handler? = null
        private var sTimeTickRunnable: Runnable? = null

        /* 天气 30 分钟刷新（Handler 兜底） */
        private var sWeatherHandler: Handler? = null
        private var sWeatherRunnable: Runnable? = null

        /* 事件循环 5 秒 */
        private var sEventCycleHandler: Handler? = null
        private var sEventCycleRunnable: Runnable? = null

        private val DEFAULT_CITY = City(
            id = "39.9042,116.4074", name = "北京",
            latitude = 39.9042, longitude = 116.4074, country = "中国"
        )

        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        fun refreshWidget(context: Context) {
            try {
                val intent = Intent(context, CombinedWidgetProvider::class.java)
                intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val ids = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, CombinedWidgetProvider::class.java))
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                context.sendBroadcast(intent)
            } catch (_: Throwable) { }
        }
    }
}