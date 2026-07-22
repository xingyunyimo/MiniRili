# MiniRili

轻量离线 Android 农历日历 APP。仓库：`https://github.com/xingyunyimo/MiniRili.git`。

核心功能：万年历（公历+农历+节气，月/日/年视图，周视图代码完整但菜单隐藏入口）、事件与一次性提醒（含延后）、导入栏+闹钟双通道通知、4x2 Widget、本地 ICS/JSON 导入导出、搜索、节假日（含调休）、出行建议、隐私政策页。农历基于 android.icu.util.ChineseCalendar，覆盖 1900-2200。

## Build & Run

```bash
./gradlew build              # 完整构建
./gradlew installDebug       # 安装 debug APK
./gradlew testDebugUnitTest  # 跑单元测试
./gradlew assembleDebug      # 仅构建 APK
```

debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

## 技术栈

Kotlin + Jetpack Compose (Material3) | Room (KSP, ver 7) | Hilt 2.55 | Navigation-Compose | AlarmManager | WorkManager | HttpURLConnection | DataStore Preferences | AppWidgetProvider

包名：`com.minirili.app`，minSdk 26，targetSdk 35。

## 关键约定

- **Repository 包住提醒调度**。所有对事件的增删改都走 `EventRepository`。
- **`EventEntity.gregorianDate`** 公历主键 `YYYY-MM-DD`。`reminderTime` 是 Unix 毫秒时间戳（事件时间，不含偏移），`reminderOffset` 是偏移量（分钟）。
- **`reminderTime` 基准日期固定为 2000-01-01**（仅取 hour/minute），避免农历早于 1970 的日期导致负时间戳。调度器只从中提取 `HOUR_OF_DAY` 和 `MINUTE`，套到 `gregorianDate` 上计算触发时刻。
- **提醒触发时间** = `gregorianDate` 为日期基准 + `reminderTime` 的 hour/minute + 减去 `reminderOffset * 60 * 1000`。由 `RecurringReminderScheduler.calculateReminderTime()` 统一计算。
- **`EventEntity.useLunar`** 区分农历/阳历事件；重复 `"monthly"/"yearly"` 配合 `useLunar` 决定是否走农历排期。
- **`EventEntity.skipDates`** 逗号分隔 `YYYY-MM-DD`，标记周期事件中某次不触发（不展示+不提醒）。也用于"仅删除本次"。
- **`EventEntity.skipReminderDates`** 逗号分隔 `YYYY-MM-DD`，标记周期事件中仅跳过提醒（事件仍展示）。独立于 `skipDates`。
- **导航**：新增页面 route 必须加进 `Screen` sealed class（`ui/navigation/Screen.kt`），事件详情用 `Screen.EventDetail.createRoute(id)`。
- **通知方式约束**：`EventDetailScreen` 内 `forceAllDay` 为 true 时，`reminderTime` 置 0 且保存时校验 `notifyNotification` 和 `notifyAlarm` 不能同时为 true。

## 日期工具

- `utils/DateUtils.kt` — 公历格式化与解析
- `utils/LunarCalendar.kt` — 完整农历（干支/生肖/节气/八字），有测试覆盖
- `utils/RecurrenceEngine.kt` — 重复事件日期计算引擎（8 种 repeatType）
- `utils/IcsUtils.kt` — RFC 5545 导入导出
- `data/HolidayService.kt` + `HolidayDatabase.kt` — 节假日 + 调休

## Android 适配陷阱

- **`Theme.kt`** 里取 `view.context` 必须 safe cast 为 `AppCompatActivity?`，硬转 `ComponentActivity` 在 API 31 安装时闪退。
- **自适应图标 foreground 必须是 drawable**，不能是 `@color`。
- **ICS 导出**：走 `FileProvider`（authority `${applicationId}.fileprovider`），不能写 `file://` URI。
- **`POST_NOTIFICATIONS`**（Android 13+）必须运行时请求。
- **`SCHEDULE_EXACT_ALARM`**：Android 13+ 有 `canScheduleExactAlarms()` 守卫，`setAlarmClock` 不受此限制，但 widget 天气刷新和出行建议仍用 `setExactAndAllowWhileIdle`，需降级 `setAndAllowWhileIdle`。
- **Material3 FilterChip 配色**：border 要单独写 `FilterChip(border = ...)`。

## 参考文档

- [架构与子系统](docs/architecture.md) — 数据层架构、天气/出行建议/Widget 子系统、AutoStartHelper
- [提醒系统](docs/reminder-system.md) — 提醒链路、RecurrenceEngine、删除/跳过、搜索、已知限制

## 测试覆盖

`DateUtilsTest` / `LunarCalendarTest` / `IcsUtilsTest` / `HolidayServiceTest` / `ChineseCityDbTest` / `OccurrenceSkipTest` / `RecurrenceEngineTest`。覆盖：日期往返、农历闰月、ICS 往返解析、节假日判断、城市数据库查询、周期事件跳过、农历每月/每年重复展开、skipDates 过滤。