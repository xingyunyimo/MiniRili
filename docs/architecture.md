# 架构与子系统

## 数据层架构

```
UI (Composable) ── observes ──► EventViewModel (StateFlow)
                                    │ calls suspend fun
                                    ▼
                              EventRepository
                                    │
                 ┌──────────────────┼──────────────────┐
                 ▼                  ▼                  ▼
            EventDao           ReminderScheduler    RecurringReminderScheduler
            (Room)             (AlarmManager)       (委托 RecurrenceEngine)
                 │
                 ▼
          EventEntity / CalendarDatabase
```

重复事件日期计算统一由 `RecurrenceEngine` 负责，`RecurringReminderScheduler` 和日历视图均委托它计算日期：

```
RecurrenceEngine ─── 唯一日期计算来源
    ↑ 依赖            ↑ 依赖
RecurringReminderScheduler    CalendarScreen / ViewModel
```

## 天气子系统

```
WeatherCard / WeatherScreen
    │ observes StateFlow
    ▼
WeatherViewModel (@HiltViewModel, 注入 WeatherRepository + LocationHelper)
    │
    ├─ start() → loadCityAndRefresh() + tryRefreshLocation()
    │      └─ 定位模式下每 30 分钟轻量刷新位置
    │      └─ 用户 onPermissionGranted() 或 refreshLocation() 重置间隔
    ├─ observeCities() Flow → 城市表变化时：
    │      └─ 当前城市还在列表里 → 不动
    │      └─ 当前城市被删 → 找 isSelected 城市，没有则 fallback firstOrNull
    │      └─ 空列表 → loadDefaultCity()
    ├─ selectCity(city) → Room setSelectedCity(id) → 触发 observeCities 同步所有 VM 实例
    ├─ addCity(city) → ensureCity + setSelectedCity
    ├─ loadDefaultCity() → GPS → ensureCity + setSelectedCity + clearWeatherCache
    │
    ▼
WeatherRepository (@Singleton)
    │
    ├─► WeatherCacheDao (Room, 30 分钟缓存, key="${lat},${lon}|today")
    │      └─ 定位更新后 clearAll() 强制走网络
    ├─► CityDao (Room, 多城市管理)
    │      └─ isSelected 列标记当前选中城市，进程恢复时读取
    │      └─ ensureCity() 保留旧 isSelected 值，避免 REPLACE 丢失
    └─► WeatherDataSource (接口)
          └─► OpenMeteoApi (HttpURLConnection + org.json)
                    │
                    ├─ searchCity() → ChineseCityDb（本地 3300 条行政区数据）+ Open-Meteo Geocoding 并发合并
                    │
                    └─► LocationHelper (LocationManager, 无 play-services 依赖)
                            │
                            ├─ getCurrentCity() — 同步，getLastKnownLocation
                            ├─ getCurrentCityAsync() — 协程安全
                            └─ getFreshLocation() — API 30+ 一次被动定位，低版本回退
```

**多城市选择机制：**
- `CityEntity.isSelected` 列持久化用户选中状态，进程恢复 / Widget / Worker 均读取此字段
- `selectCity()` / `addCity()` / `loadDefaultCity()` 均写入 `isSelected`
- `observeCities` 监听器不再无脑覆盖 `_currentCity`，仅当前城市被删除时才切换
- Widget、Worker、出行建议接收器均读取 `isSelected` 城市，而非固定 `firstOrNull()`

## 出行建议子系统（WTH-06）

```
DailyWeatherWorker (WorkManager 每日)
DailyTravelAdviceReceiver (AlarmManager 定时)
    │
    ▼
TravelAdviceEngine (规则引擎：高温/雨雪/大风/AQI)
    │
    ▼
NotificationHelper → 通知栏
    │
TravelAdvicePrefs (DataStore：开关 / 推送时间)
```

## Widget 子系统

```
CombinedWidgetProvider (AppWidgetProvider, 4x2)
    │ onUpdate → runBlocking
    ▼
CalendarDatabase / HolidayService / LunarCalendar / OpenMeteoApi
    │
    ▼
RemoteViews（农历+公历+节气+天气+事件列表）
    │
AlarmManager → 每 30 分钟 tick 刷新
```

## 附加工具

- `utils/AutoStartHelper.kt` — 厂商自启动引导，按设备厂商（小米/华为/OPPO/vivo/三星）跳转对应后台管理设置页。`CalendarScreen` 中首次启动和 30 天后弹窗提示。
- `utils/AppLaunchPrefs.kt` — 自启动提示频率控制，30 天内不重复提示。