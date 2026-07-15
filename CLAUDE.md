# MiniRili

轻量离线 Android 农历日历 APP。仓库：`https://github.com/xingyunyimo/MiniRili.git`。

核心功能：万年历（公历+农历+节气，月/日/年视图，周视图已隐藏）、事件与一次性提醒、导入栏+闹钟双通道通知、本地 ICS/JSON 导入导出、搜索、节假日（含调休）、农历（基于 android.icu.util.ChineseCalendar，覆盖 1900-2200）。

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
| Room (KSP) | `events` / `weather_cache` / `cities` 三表 |
| Hilt 2.55 | 单 `AppModule` |
| Navigation-Compose | `Screen` sealed class |
| AlarmManager + BroadcastReceiver | 一次性提醒 |
| WorkManager | 预留（workers/ReminderWorker.kt） |
| HttpURLConnection + org.json | 天气数据源请求 |

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
            (Room)             (AlarmManager)       (预约未来多次)
                 │
                 ▼
          EventEntity / CalendarDatabase
```

天气子系统：

```
WeatherCard / WeatherScreen
    │ observes StateFlow
    ▼
WeatherViewModel (@HiltViewModel, 注入 WeatherRepository + LocationHelper)
    │
    ▼
WeatherRepository (@Singleton)
    │
    ├─► WeatherCacheDao (Room, 30 分钟缓存, key="cityId|today")
    ├─► CityDao (Room, 多城市管理)
    └─► WeatherDataSource (接口)
          └─► OpenMeteoApi (HttpURLConnection + org.json)
                    │
                    └─► LocationHelper (LocationManager, 无 play-services 依赖)
```

关键约定：
- **Repository 包住提醒调度**。所有对事件的增删改都走 `EventRepository`。
- **`EventEntity.gregorianDate`** 是公历主键格式 `YYYY-MM-DD`。`reminderTime` 是 Unix 毫秒时间戳（事件时间，不含偏移），`reminderOffset` 是偏移量（分钟）。
- **提醒触发时间** = `reminderTime - reminderOffset * 60 * 1000`（在调度时计算，不在保存时减）。
- **`EventEntity.useLunar`** 区分农历/阳历事件；重复类型 `"monthly"/"yearly"` 配合 `useLunar` 字段决定是否走农历排期。
- **导航**：新增页面 route 必须加进 `Screen` sealed class（`ui/navigation/Screen.kt`），事件详情用 `Screen.EventDetail.createRoute(id)`。

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
- `utils/IcsUtils.kt`：RFC 5545 导入导出。
- `data/HolidayService.kt` + `HolidayDatabase.kt`：节假日 + 调休。

## 提醒链路

### 一次性提醒
事件保存 → `EventRepository.insert/update` → 计算触发时间 `triggerTime = event.reminderTime - event.reminderOffset * 60L * 1000L` → `ReminderScheduler.scheduleReminder(eventId, gregorianDate, triggerTime)`（requestCode = `eventId.toInt() << 8`）→ AlarmManager `setAlarmClock(AlarmClockInfo, PendingIntent)` → `AlarmReceiver.onReceive` → `NotificationHelper` 发通知 + `playAlarmSound`（通知内容中时间从 `event.reminderTime` 取事件时间）。

### 周期事件（repeatType != "none"）
`EventRepository.insert/update` → `RecurringReminderScheduler.scheduleRecurringReminder(event, baseDate)` → 按 repeatType 分支调度（阳历：daily/weekly/monthly/yearly/workday/weekend；农历：monthly/yearly + useLunar 分支判断）→ 预约未来 N 次，每次独立 requestCode = `(eventId.toInt() << 8) | occurrenceIndex`。每次触发后 `scheduleNextOccurrence` 续约下一轮。

农历排期使用 `LunarCalendar.toLunarParts()` 提取农历月/日，`LunarCalendar.lunarToGregorian()` 将未来农历月/日转为公历日期。

### 设备重启
`ReminderBootReceiver` → `RecurringReminderScheduler.rescheduleAllReminders()` 重调度所有事件。

### 锁屏停闹钟
`AlarmReceiver.playAlarmSound` 期间动态注册 `ACTION_SCREEN_OFF` 监听（不依赖 Activity），触发即 `stopAlarm()`。30s 超时或 stop 后注销。

## 测试覆盖

- `DateUtilsTest`、`LunarCalendarTest`、`IcsUtilsTest`、`HolidayServiceTest` —— 核心工具。
- 覆盖：日期往返、农历闰月、ICS 往返解析（含中文转义）、节假日判断。