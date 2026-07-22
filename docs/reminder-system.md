# 提醒系统

## 一次性提醒

事件保存 → `EventRepository.insert/update` → 以 `event.gregorianDate` 为日期基准、`event.reminderTime` 的 hour/minute 为时刻基准，减去 `reminderOffset` 得到触发时间 → `ReminderScheduler.scheduleReminder(eventId, gregorianDate, triggerTime)`（requestCode = `eventId.toInt() << 8`）→ AlarmManager `setAlarmClock(AlarmClockInfo, PendingIntent)` → `AlarmReceiver.onReceive` → `NotificationHelper` 发通知 + `playAlarmSound`。触发时间计算复用 `RecurringReminderScheduler.calculateReminderTime()`，与周期事件统一。

## 周期事件

`EventRepository.insert/update` → `RecurringReminderScheduler.scheduleRecurringReminder(event, baseDate)` → 委托 `RecurrenceEngine.expandForRange()` 计算未来 N 次日期 → 每次独立 requestCode = `(eventId.toInt() << 8) | occurrenceIndex`。每次触发后 `scheduleNextOccurrence` 续约下一轮。

`RecurrenceEngine` 覆盖 8 种 repeatType：
- 阳历：daily / weekly / monthly / yearly / workday / weekend
- 农历：monthly / yearly（配合 `useLunar` 字段，使用 `LunarCalendar.toLunarParts()` + `lunarToGregorian()`）

## 设备重启

`ReminderBootReceiver` → `RecurringReminderScheduler.rescheduleAllReminders()` 重调度所有事件。

## 锁屏停闹钟

`AlarmReceiver.playAlarmSound` 期间动态注册 `ACTION_SCREEN_OFF` 监听（不依赖 Activity），触发即 `stopAlarm()`。30s 超时或 stop 后注销。

## 提醒延后（Snooze）

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
```

延后选项：5/10/30 分钟，requestCode 高位 `0xFE` 标记 snooze 用途。

## RecurrenceEngine

`utils/RecurrenceEngine.kt` — 纯日期计算引擎，8 种 repeatType 的展开逻辑：
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

## 搜索

CalendarScreen 内嵌搜索栏，实时过滤事件标题/描述/标签，结果取前 10 条，in-memory 无持久化。

## 已知限制

- **Widget 不显示展开事件**：CombinedWidgetProvider 直接读 DB，不感知展开
- **moveEventUp/Down 影响所有日期**：同一个 EventEntity 的 sortOrder 全局共享
- **completed 标记所有日期**：无 per-occurrence 完成状态
- **AllEventsScreen 不展开**：避免无限展开