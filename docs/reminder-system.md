# 提醒系统

## 触发时刻计算

`RecurringReminderScheduler.calculateReminderTime(calendar, baseReminderTimeMs, offsetMinutes)` 是触发时刻的唯一计算入口，一次性与周期事件共用：

- `reminderTime` 存的是 **2000-01-01 基准**的时间戳，只作为"时:分编码器"使用（避免农历早于 1970 的日期产生负时间戳）。
- 计算方式：取 `reminderTime` 的 hour/minute 套到 `calendar`（事件日期）上，再减去 `reminderOffset` 分钟。
- **全天事件**（`reminderTime == 0`）fallback 到**当天 09:00**，因此全天事件也能正常提醒。
- 任何路径都不得把 `reminderTime` 直接当触发时刻用 —— 它是 2000 年的 epoch，必然落在过去而被跳过。

## 一次性提醒

事件保存 → `EventRepository.insert/update` → `scheduleForEvent()` → `calculateReminderTime()` 得到触发时间 → `ReminderScheduler.scheduleReminder(eventId, gregorianDate, triggerTime)`（requestCode = `eventId.toInt() << 8`）→ AlarmManager `setAlarmClock(AlarmClockInfo, PendingIntent)` → `AlarmReceiver.onReceive` → `NotificationHelper` 发通知 + `playAlarmSound`。

`scheduleForEvent()` 是 insert / update / 跳过类操作共用的排程入口，排程条件为 `notifyNotification || notifyAlarm`（不再依赖 `reminderTime > 0`，否则全天事件会被漏排）。

## 周期事件

`EventRepository` → `RecurringReminderScheduler.scheduleRecurringReminder(event, baseDate)` → 委托 `RecurrenceEngine.expandForRange()` 计算未来窗口内的日期 → 每次独立 requestCode = `(eventId.toInt() << 8) | occurrenceIndex`。

预约窗口：daily 40 天 / weekly 12 周 / monthly 12 月 / yearly 10 年 / 农历 monthly 1 年 / 农历 yearly 10 年 / workday、weekend 90 天。每次触发后 `scheduleNextOccurrence` 取消未触发预约并以本次为基续约下一轮。

`RecurrenceEngine` 覆盖 8 种 repeatType：
- 阳历：daily / weekly / monthly / yearly / workday / weekend
- 农历：monthly / yearly（配合 `useLunar` 字段，使用 `LunarCalendar.toLunarParts()` + `lunarToGregorian()`）

## 设备重启 / 时区与日期变更

`ReminderBootReceiver` 监听 `BOOT_COMPLETED` / `QUICKBOOT_POWERON` / `TIME_CHANGED` / `DATE_CHANGED` → `RecurringReminderScheduler.rescheduleAllReminders()` 全量重排。

- 周期事件：`getBaseDateForRecurring()` 取锚点日期后走 `scheduleRecurringReminder`。
- 一次性事件：必须走 `calculateReminderTime()` 把 2000 基准的 `reminderTime` 套回 `gregorianDate`，并以 `notifyNotification || notifyAlarm` 为排程条件。（历史 bug：曾用 `reminderTime - offset` 直接计算，结果永远在过去而被跳过守卫丢弃，导致重启后一次性提醒静默失效。）

## 锁屏停闹钟

`AlarmReceiver.playAlarmSound` 期间动态注册 `ACTION_SCREEN_OFF` 监听（不依赖 Activity），触发即 `stopAlarm()`。30s 超时或 stop 后注销。

## 提醒延后（Snooze）

```
通知 Action [延后5分钟 / 延后1小时 / 延后1天]
    │
    ▼
SnoozeReceiver.onReceive
    │
    ▼
ReminderScheduler.scheduleOccurrence(eventId, 0xFE, eventDateStr, now + 延后分钟)
    │
    ▼
AlarmManager → AlarmReceiver → 重新发通知（仍带 3 个延后按钮）
```

延后档位：5 分钟 / 1 小时 / 1 天，不可自定义，**无延后次数上限**（可无限链式延后）。snooze 重排的 requestCode 低位用 `0xFE` 标记。

## RecurrenceEngine

`utils/RecurrenceEngine.kt` — 纯日期计算引擎，8 种 repeatType 的展开逻辑：
- `expandForRange(events, startDate, endDate, excludeSkipDates)` — 返回 `List<EventOccurrence>`
- `expandForDate(events, date)` — 单日展开
- 农历每月/每年：向后扫描到 `start` 下限，再向前扫描，传入 `isLeapMonth`
- `excludeSkipDates=false` 给调度器用（`skipDates`/`skipReminderDates` 由 `AlarmReceiver` 运行时处理）
- 月视图用 `remember(year, month, allEvents)` 缓存，避免点选日期触发重算
- AllEventsScreen **不展开**，只显示锚点日期 + 重复标记 `⟳`

### 删除与跳过重复事件

- 重复事件删除时弹出对话框，支持"仅删除本次"（→ `skipOccurrence`，写入 `skipDates`，事件不展示也不提醒）和"删除全部"（→ `deleteEvent`）
- 重复事件详情页有"跳过当天"按钮（→ `skipReminderOnly`，写入 `skipReminderDates`），仅跳过选中日期的提醒，事件仍展示
- 通知方式卡片底部显示"当天提醒已跳过"提示 + "恢复提醒"按钮（→ `restoreReminderOnly`），从 `skipReminderDates` 移除该日期
- 非重复事件保持原样单确认
- 导航传入 `contextDate` 参数，与表单 `selectedDate` 分离，避免误删

**跳过与调度窗口的关系（两处配合，缺一会断流）**：
1. `AlarmReceiver` 把 `rescheduleNextOccurrence` 放在跳过判断**之前** —— 被跳过的那次触发不响，但照样滚动窗口。
2. `EventRepository` 的 `skipOccurrence` / `skipReminderOnly` / `restoreReminderOnly` 改库后调 `rescheduleAfterSkipChange()` → `scheduleForEvent(rescheduleRecurring = true)`，以"今天"为基重排（锚点很旧的历史事件也能排上未来窗口）。

## 搜索

CalendarScreen 顶栏搜索图标 → `SearchDialog`，按**标题 / 描述**模糊匹配（`EventDao.searchEvents`），300ms debounce，结果取前 10 条。点击结果跳转到该事件日期，不直接进详情。**不支持按标签 / 类型 / 日期范围搜索**。

## 已知限制

- **moveEventUp/Down 影响所有日期**：同一个 EventEntity 的 sortOrder 全局共享
- **completed 标记所有日期**：无 per-occurrence 完成状态
- **AllEventsScreen 不展开**：避免无限展开
- **类型/标签过滤无 UI 入口**：`getEventsByType` / `getEventsByTag` 已在 DAO 与 Repository 实现，界面未接入
- **完成状态不能在列表勾选**：`setCompleted` 需进事件详情页设置
- **无重复结束日期**：`EventEntity` 无 `repeatEndDate` / `repeatCount`，重复事件无限重复
- **提前提醒仅 6 档**：0 / 5 / 15 / 30 / 60 / 1440 分钟，最大"1 天前"，不可自定义
- **延后无次数上限**：可无限链式延后
- **weekend 重复不识别调休补班**：只判断周六/周日，未查 `HolidayService`（`workday` 已正确使用 HolidayService）
- **不监听时区变更**：无 `ACTION_TIMEZONE_CHANGED`，换时区后按原绝对时刻触发
- **导出仅支持 ICS**：导入支持 ICS + JSON，导出格式不对称；农历语义依赖 `X-MINIRILI-*` 扩展属性，标准 ICS 客户端读取时会退化为纯公历事件
- **农历覆盖范围**：真机走 ICU（1900-2200 全覆盖）；fallback 表覆盖 1899-2201，JVM 单测/无 ICU 设备用。超出此范围的年份 `lunarToGregorian` 返回 null（事件静默跳过）
- **开机重排在主线程 `runBlocking`**：事件量大时存在 ANR 风险