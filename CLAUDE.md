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

debug APK 输出到 `app/build/outputs/apk/debug/MiniRili.apk`（`outputFileName` 已在 build.gradle.kts 改名）。

## 技术栈

Kotlin + Jetpack Compose (Material3) | Room (KSP，schema version 8) | Hilt 2.55 | Navigation-Compose | AlarmManager | WorkManager | HttpURLConnection | DataStore Preferences | AppWidgetProvider

包名：`com.minirili.app`，minSdk 26，targetSdk 35。

## 关键约定

- **Repository 包住提醒调度**。所有对事件的增删改都走 `EventRepository`。
- **`EventEntity.gregorianDate`** 公历主键 `YYYY-MM-DD`。`reminderTime` 是 Unix 毫秒时间戳（事件时间，不含偏移），`reminderOffset` 是偏移量（分钟）。
- **`reminderTime` 基准日期固定为 2000-01-01**（仅取 hour/minute），避免农历早于 1970 的日期导致负时间戳。调度器只从中提取 `HOUR_OF_DAY` 和 `MINUTE`，套到 `gregorianDate` 上计算触发时刻。
- **提醒触发时间** = `gregorianDate` 为日期基准 + `reminderTime` 的 hour/minute + 减去 `reminderOffset * 60 * 1000`。由 `RecurringReminderScheduler.calculateReminderTime()` 统一计算。**全天事件**（`reminderTime == 0`）fallback 到当天 **09:00**，可以正常提醒；任何路径都不能拿 `reminderTime` 直接当触发时刻用（它是 2000 年 epoch，必落在过去）。
- **排程条件统一为 `notifyNotification || notifyAlarm`**，不能写成 `reminderTime > 0`（会漏排全天事件）。insert / update / 跳过类操作共用 `EventRepository.scheduleForEvent()`。
- **跳过与续约的顺序**：`AlarmReceiver` 必须先 `rescheduleNextOccurrence` 再判断 skip，否则被跳过的触发不滚动窗口，连续跳过会让周期提醒静默断流。
- **`EventEntity.useLunar`** 区分农历/阳历事件；重复 `"monthly"/"yearly"` 配合 `useLunar` 决定是否走农历排期。
- **`EventEntity.skipDates`** 逗号分隔 `YYYY-MM-DD`，标记周期事件中某次不触发（不展示+不提醒）。也用于"仅删除本次"。
- **`EventEntity.skipReminderDates`** 逗号分隔 `YYYY-MM-DD`，标记周期事件中仅跳过提醒（事件仍展示）。独立于 `skipDates`。
- **`EventEntity.lunarDate` 不可信**：保存时写入的是 `selectedDate`（公历值）而非农历，且**不参与任何运行时计算**（调度器与 `RecurrenceEngine` 一律从 `gregorianDate` 用 `toLunarParts` 反推农历）。它只被 ICS/JSON 导入导出透传。需要农历值时请现算，不要读这个字段。
- **导航**：新增页面 route 必须加进 `Screen` sealed class（`ui/navigation/Screen.kt`），事件详情用 `Screen.EventDetail.createRoute(id)`。
- **农历事件日期选择器**：`EventDetailScreen` 的 `DateTimePickerDialog` 在 `useLunar` 时必须用 `LunarCalendar.toLunarParts(selectedDate)` 预填**农历**值，确认时再 `lunarToGregorian(..., isLeapMonth)` 反推为公历存 `gregorianDate`。预填公历值会导致公历被当农历反推而日期漂移；闰月标记需一并传入。农历日 clamp 用 1..30，不能用公历月天数。
- **新建事件默认**：`notifyNotification = true`、`notifyAlarm = false`（闹钟属强提醒，需用户主动开）。

## 日期工具

- `utils/DateUtils.kt` — 公历格式化与解析
- `utils/LunarCalendar.kt` — 完整农历（干支/生肖/节气/八字），有测试覆盖。公历→农历主路径走 `android.icu.util.ChineseCalendar`（API 24+ 内置，覆盖 1900-2200）；无 Android 运行时时回退至内置春节锚点表（1899-2201，由 ICU 权威生成，含闰月）。
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

`DateUtilsTest` / `LunarCalendarTest` / `LunarCalendarYearRangeConsistencyTest` / `IcsUtilsTest` / `HolidayServiceTest` / `ChineseCityDbTest` / `OccurrenceSkipTest` / `RecurrenceEngineTest`。覆盖：日期往返、农历闰月、1899-2201 全量公历↔农历自洽、1953 农历事件防漂移、ICS 往返解析、节假日判断、城市数据库查询、周期事件跳过、农历每月/每年重复展开、skipDates 过滤。