# MiniRili

轻量离线 Android 农历日历 APP。仓库：`https://github.com/xingyunyimo/MiniRili.git`。

核心功能：万年历（公历+农历+节气，月/日/年视图，周视图已隐藏）、事件与一次性提醒（含延后）、导入栏+闹钟双通道通知、4x2 Widget、本地 ICS/JSON 导入导出、搜索、节假日（含调休）、出行建议、隐私政策页。农历基于 android.icu.util.ChineseCalendar，覆盖 1900-2200。

## Build & Run

标准 Android Gradle 工程，JDK 17+，Android SDK 35，min SDK 26。

```bash
./gradlew build          # 完整构建
./gradlew installDebug   # 安装 debug APK
./gradlew testDebugUnitTest  # 跑单元测试
./gradlew assembleDebug  # 仅构建 APK
```

debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

## 技术栈

| 栈 | 备注 |
|---|---|
| Kotlin + Jetpack Compose (Material3) | 全部 UI |
| Room (KSP) | `events` / `weather_cache` / `cities` 三表，version 7 |
| Hilt 2.55 | 单 `AppModule` |
| Navigation-Compose | `Screen` sealed class |
| AlarmManager + BroadcastReceiver | 提醒调度（一次性+周期+延后） |
| WorkManager | 每日天气通知（`DailyWeatherWorker.kt`） |
| HttpURLConnection + org.json | 天气数据源请求 |
| DataStore Preferences | 出行建议偏好设置 |
| AppWidgetProvider | 4x2 桌面小部件 |

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

天气子系统：

```
WeatherCard / WeatherScreen
    │ observes StateFlow
    ▼
WeatherViewModel (@HiltViewModel, 注入 WeatherRepository + LocationHelper)
    │
    ├─ start() → loadCityAndRefresh() + tryRefreshLocation()
    │      └─ 定位模式下每 30 分钟轻量刷新位置
    │      └─ 用户 onPermissionGranted() 或 refreshLocation() 重置间隔
    ├─ observeCities() Flow → 城市表变化自动切换
    │
    ▼
WeatherRepository (@Singleton)
    │
    ├─► WeatherCacheDao (Room, 30 分钟缓存, key="${lat},${lon}|today")
    │      └─ 定位更新后 clearAll() 强制走网络
    ├─► CityDao (Room, 多城市管理)
    └─► WeatherDataSource (接口)
          └─► OpenMeteoApi (HttpURLConnection + org.json)
                    │
                    └─► LocationHelper (LocationManager, 无 play-services 依赖)
                            │
                            ├─ getCurrentCity() — 同步，getLastKnownLocation
                            ├─ getCurrentCityAsync() — 协程安全
                            └─ getFreshLocation() — API 30+ 一次被动定位，低版本回退
```

出行建议子系统（WTH-06）：

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

Widget 子系统：

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

提醒延后（REM-05）：

```
通知 Action [延后5分/10分/30分]
    │
    ▼
SnoozeReceiver.onReceive
    │
    ▼
ReminderScheduler.scheduleOccurrence(eventId, 0xFE, triggerTime)
    │
    ▼
AlarmManager → AlarmReceiver → 重新发通知

## 关键约定
- **Repository 包住提醒调度**。所有对事件的增删改都走 `EventRepository`。
- **`EventEntity.gregorianDate`** 是公历主键格式 `YYYY-MM-DD`。`reminderTime` 是 Unix 毫秒时间戳（事件时间，不含偏移），`reminderOffset` 是偏移量（分钟）。
- **`reminderTime` 基准日期固定为 2000-01-01**（仅取 hour/minute），避免农历等早于 1970 的日期导致负时间戳。调度器只从中提取 `HOUR_OF_DAY` 和 `MINUTE`，套到事件真实日期（`gregorianDate`）上再计算触发时刻。
- **提醒触发时间** = 以 `gregorianDate` 为日期基准、`reminderTime` 的 hour/minute 为时刻基准，减去 `reminderOffset * 60 * 1000`。由 `RecurringReminderScheduler.calculateReminderTime(calendar, baseReminderTimeMs, offsetMinutes)` 统一计算（一次性与周期事件共用）。
- **`EventEntity.useLunar`** 区分农历/阳历事件；重复类型 `"monthly"/"yearly"` 配合 `useLunar` 字段决定是否走农历排期。
- **`EventEntity.skipDates`** 逗号分隔的 `YYYY-MM-DD` 字符串，标记周期事件中某次不触发（不展示+不提醒），`AlarmReceiver.isOccurrenceSkipped()` 判断。也用于"仅删除本次"功能。
- **`EventEntity.skipReminderDates`** 逗号分隔的 `YYYY-MM-DD` 字符串，标记周期事件中仅跳过提醒（事件仍展示，不响闹钟/通知）。与 `skipDates` 独立，两者均被 `AlarmReceiver.isOccurrenceSkipped()` 检查。
- **导航**：新增页面 route 必须加进 `Screen` sealed class（`ui/navigation/Screen.kt`），事件详情用 `Screen.EventDetail.createRoute(id)`。
- **通知方式约束**：全天事件（`forceAllDay == true`）不能同时启用通知栏或闹钟，保存时校验拦截。

## 重复事件展开显示

日历视图（月/周/日）使用 `RecurrenceEngine` 展开重复事件，在重复发生的所有日期上展示：

- `utils/RecurrenceEngine.kt` — 纯日期计算引擎，8 种 repeatType 的展开逻辑
  - `expandForRange(events, startDate, endDate, excludeSkipDates)` — 返回 `List<EventOccurrence>`
  - `expandForDate(events, date)` — 单日展开
  - 农历每月/每年：向后扫描到 `start` 下限，再向前扫描，传入 `isLeapMonth`
- `excludeSkipDates=false` 给调度器用（`skipDates`/`skipReminderDates` 由 `AlarmReceiver` 运行时处理）
- 月视图用 `remember(year, month, allEvents)` 缓存，避免点选日期触发重算
- AllEventsScreen **不展开**，只显示锚点日期 + 重复标记 `⟳`

### 删除与跳过重复事件
- 重复事件删除时弹出对话框，支持"仅删除本次"（→ `skipOccurrence`）和"删除全部"（→ `deleteEvent`）
- 重复事件详情页有"跳过当天"按钮（→ `skipReminderOnly`），仅跳过选中日期的提醒，事件仍展示，确认后返回上一页
- 通知方式卡片底部显示"当天提醒已跳过"提示 + "恢复提醒"按钮（→ `restoreReminderOnly`），立即从 `skipReminderDates` 移除该日期
- 非重复事件保持原样单确认
- 导航传入 `contextDate` 参数，与表单 `selectedDate` 分离，避免误删

## 已知限制
- **Widget 不显示展开事件**：CombinedWidgetProvider 直接读 DB，不感知展开
- **moveEventUp/Down 影响所有日期**：同一个 EventEntity 的 sortOrder 全局共享
- **completed 标记所有日期**：无 per-occurrence 完成状态
- **AllEventsScreen 不展开**：避免无限展开

## Android 适配陷阱

- **`Theme.kt`** 里取 `view.context` 必须 safe cast 为 `AppCompatActivity?`，硬转 `ComponentActivity` 在 API 31 安装时闪退。
- **自适应图标 foreground 必须是 drawable**，不能是 `@color`。
- **ICS 导出**：走 `FileProvider`（authority `${applicationId}.fileprovider`），不能写 `file://` URI。
- **`POST_NOTIFICATIONS`**（Android 13+）必须运行时请求。
- **`SCHEDULE_EXACT_ALARM`**：Android 13+ 有 `canScheduleExactAlarms()` 守卫，`setAlarmClock` 不受此限制（系统闹钟最高优先级），但 widget 天气刷新和出行建议仍用 `setExactAndAllowWhileIdle`，需降级 `setAndAllowWhileIdle`。
- **Material3 FilterChip 配色**：border 要单独写 `FilterChip(border = ...)`。

## 日期工具

- `utils/DateUtils.kt`：公历格式化与解析 (`YYYY-MM-DD`)。
- `utils/LunarCalendar.kt`：完整农历（干支/生肖/节气/八字），有测试覆盖。
- `utils/RecurrenceEngine.kt`：重复事件日期计算引擎（8 种 repeatType）。
- `utils/IcsUtils.kt`：RFC 5545 导入导出。
- `data/HolidayService.kt` + `HolidayDatabase.kt`：节假日 + 调休。

## 提醒链路

### 一次性提醒
事件保存 → `EventRepository.insert/update` → 以 `event.gregorianDate` 为日期基准、`event.reminderTime` 的 hour/minute 为时刻基准，减去 `reminderOffset` 得到触发时间 → `ReminderScheduler.scheduleReminder(eventId, gregorianDate, triggerTime)`（requestCode = `eventId.toInt() << 8`）→ AlarmManager `setAlarmClock(AlarmClockInfo, PendingIntent)` → `AlarmReceiver.onReceive` → `NotificationHelper` 发通知 + `playAlarmSound`。触发时间计算复用 `RecurringReminderScheduler.calculateReminderTime()`，与周期事件统一。

### 周期事件（repeatType != "none"）
`EventRepository.insert/update` → `RecurringReminderScheduler.scheduleRecurringReminder(event, baseDate)` → 委托 `RecurrenceEngine.expandForRange()` 计算未来 N 次日期 → 每次独立 requestCode = `(eventId.toInt() << 8) | occurrenceIndex`。每次触发后 `scheduleNextOccurrence` 续约下一轮。

`RecurrenceEngine` 覆盖 8 种 repeatType：
- 阳历：daily / weekly / monthly / yearly / workday / weekend
- 农历：monthly / yearly（配合 `useLunar` 字段，使用 `LunarCalendar.toLunarParts()` + `lunarToGregorian()`）

### 设备重启
`ReminderBootReceiver` → `RecurringReminderScheduler.rescheduleAllReminders()` 重调度所有事件。

### 锁屏停闹钟
`AlarmReceiver.playAlarmSound` 期间动态注册 `ACTION_SCREEN_OFF` 监听（不依赖 Activity），触发即 `stopAlarm()`。30s 超时或 stop 后注销。

### 提醒延后（Snooze）
通知栏 action 按钮 → `SnoozeReceiver` → `ReminderScheduler.scheduleOccurrence(eventId, 0xFE, newTime)` → AlarmManager 重新触发。延后选项：5/10/30 分钟，requestCode 高位 `0xFE` 标记 snooze 用途。

## 测试覆盖

- `DateUtilsTest`、`LunarCalendarTest`、`IcsUtilsTest`、`HolidayServiceTest`、`ChineseCityDbTest`、`OccurrenceSkipTest`、`RecurrenceEngineTest`。
- 覆盖：日期往返、农历闰月、ICS 往返解析（含中文转义）、节假日判断、城市数据库查询、周期事件跳过、农历每月/每年重复展开、skipDates 过滤、混合事件列表。