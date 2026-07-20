# MiniRili v1.0.5 — 农历日期转换修复 & 天气定位刷新 & 桌面插件重复事件

## 修复

### 🔴 日历视图未显示农历事件（P0）
记事时间为农历的事件在日历月/日/周视图的日期格子中不显示，仅在下方事件列表栏可见。
- **根本原因**：`DateTimePickerDialog` 选择农历日期时，不做农历→公历转换，直接把用户输入保存为 `gregorianDate`。`RecurrenceEngine` 用 `toLunarParts(gregorianDate)` 反推农历月/日时拿到错误值，导致展开到错误的公历日期上。
- **修复**：`DateTimePickerDialog` 确认时，当 `useLunar = true`，调用 `LunarCalendar.lunarToGregorian(year, month, day)` 将用户输入的农历年月日转为公历，再存入 `gregorianDate`。转换失败时回退到原始输入。

### 🔴 天气重新定位未真正获取当前位置（P0）
从成都市到宜宾市后，点击"重新定位"和"刷新"按钮，天气仍然显示成都市天气。
- **根本原因**：`refreshLocation()` → `loadCityAndRefresh()` 在 Room 中有城市记录时，直接取第一个已存城市返回，**不调用 `LocationHelper.getCurrentCity()`** 重新定位。
- **修复**：`refreshLocation()` 改为直接调用 `loadDefaultCity(fallback = _cities.firstOrNull())`，真正获取设备当前位置。定位失败时回退到第一个已存城市而非硬编码北京，避免突然跳转到北京。

### 🟡 桌面插件今日事项遗漏重复事件
桌面插件"今日 X 项事件"只统计 `gregorianDate == 今天` 的事件，不显示从其他日期重复到今天的事件。
- **根本原因**：`CombinedWidgetProvider.setEventsSection()` 直接调用 `getEventsByDate(today)`，SQL 精确匹配 `gregorianDate`，未使用 `RecurrenceEngine` 展开重复事件。
- **修复**：改用 `getAllEventsOnce()` 获取全量事件，通过 `RecurrenceEngine.expandForDate(allEvents, today)` 展开，得到所有在今天出现的事件（含重复事件展开）。

## 技术细节

- **农历→公历转换**：`LunarCalendar.lunarToGregorian(year, month, day)` 已存在（ICU `ChineseCalendar` + 本地锚点表回退），只在 `DateTimePickerDialog` 确认时补上调用。
- **天气定位**：`loadDefaultCity()` 新增 `fallback: City?` 参数，定位失败时回退到 `_cities.firstOrNull()` 或 `DEFAULT_BEIJING`。
- **桌面插件**：`RecurrenceEngine.expandForDate` 是纯计算，无状态依赖，可直接在 Widget 的 `runBlocking` 中调用。

## 下载

APK：`app/build/outputs/apk/debug/MiniRili.apk`