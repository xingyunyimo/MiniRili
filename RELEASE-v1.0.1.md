# v1.0.1 发布说明

发布日期：2026-07-14

## 修复

- **锁屏闹钟不响**：`ReminderScheduler` 改用 `AlarmManager.setAlarmClock()`（系统闹钟最高优先级调度），解决深度 Doze 下 `setExactAndAllowWhileIdle` 音频子系统不充分就绪的问题

- **通知关闭同步停闹钟**：通知栏划掉事件提醒后，闹钟铃声同步停止（`setDeleteIntent` → `AlarmReceiver.ACTION_DISMISS_ALARM`）

## 调整

- **桌面插件 4×2**：从 4×3 调整为 4×2，压缩行间距（根 padding 10→4dp，分割线 margin 2→1dp）

- **移除电池白名单引导**：删除 REM-08 全部代码（`BatteryOptimizationHelper`、`AppLaunchPrefs`、`BackgroundPermissionDialog`、`ViewModel` trigger 及相关权限声明）

---

APK: `app/build/outputs/apk/debug/app-debug.apk`