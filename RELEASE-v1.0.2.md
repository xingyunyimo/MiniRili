# MiniRili v1.0.2 — 提醒逻辑重构 & 农历重复实现

## 修复

### 🔴 提醒时间被篡改（P0）
设置事件 22:00 + 提醒 1 小时前，系统将事件时间自动改为 21:00。
- **根本原因**：`reminderTime` 存的是闹钟触发时间（事件时间减偏移），编辑时从 `reminderTime` 提取小时/分钟，导致事件时间被偏移值篡改。
- **修复**：`reminderTime` 永远存事件时间（22:00），偏移只在调度闹钟时计算。通知栏内容也同步改为显示事件时间。

### 🟡 `reminderOffset` 单位混乱
`EventEntity.reminderOffset` 字段注释为秒但实际存分钟，加载/保存不做单位转换。
- **修复**：统一为分钟，ICS VALARM 导出时正确转换分钟→秒。

### 🟢 农历每月/每年重复等同阳历
选择"每月(农历)"或"每年(农历)"后，实际按阳历月/年重复。
- **根本原因**：农历与阳历共用字符串值 `"monthly"`/`"yearly"`，调度器走的是相同的 `Calendar.add(MONTH/YEAR)` 公历路径。
- **修复**：新增 `LunarCalendar.lunarToGregorian()` 农历→公历转换函数（ICU `ChineseCalendar` + 本地锚点表回退），调度器按 `useLunar` 字段分支调度农历月/年重复。

## 调整

- **隐藏周视图**：从视图切换菜单移除"周视图"，减少冗余选项。
- **启动弹窗顺序**：调整为 隐私政策 → 通知权限（Android 13+） → 自启动引导，避免弹窗重叠。

## 技术细节

- `reminderTime`：Unix 毫秒时间戳，存事件时间（不含偏移）
- `reminderOffset`：整数，**分钟**（此前注释为秒，实际存分钟，已统一）
- 提醒触发时间 = `reminderTime - reminderOffset * 60 * 1000`（在调度时计算）
- 农历重复：`event.useLunar == true` + `repeatType == "monthly"/"yearly"` 时走农历排期路径

## 下载

APK：`app/build/outputs/apk/debug/app-debug.apk`