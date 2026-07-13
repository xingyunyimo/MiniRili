# MiniRili

轻量化、离线优先的安卓日历 APP，支持公历/农历/节气/节假日、事件提醒、天气。

## 功能特性

| 功能模块 | 状态 |
|---|---|
| 万年历（公历 + 农历 + 节气，月/周/日/年视图） | ✅ 已完成 |
| 事件管理与一次性提醒 | ✅ 已完成 |
| 周期性重复提醒（每日/每周/每月/每年/工作日/周末） | ✅ 已完成 |
| 提醒防漏机制（开机自启、被杀恢复、锁屏停闹钟） | ✅ 已完成 |
| 电池白名单 / 厂商自启动引导 | ✅ 已完成 |
| 导入栏 + 通知双通道提醒 | ✅ 已完成 |
| 本地 ICS / JSON 导入导出 | ✅ 已完成 |
| 搜索（标题 + 描述） | ✅ 已完成 |
| 节假日标记（含调休） | ✅ 已完成 |
| 标签分类与颜色标记 | ✅ 已完成 |
| 周期事件例外（跳过某次触发） | ✅ 已完成 |
| 天气栏目与详情页（Open-Meteo + 定位 + Room 缓存） | ✅ 已完成 |
| 出行建议规则引擎 | 🔜 开发中 |
| 每日天气通知 | 🔜 开发中 |

## 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose + Material3
- **数据库**：Room（KSP）
- **依赖注入**：Hilt
- **后台任务**：AlarmManager + WorkManager
- **导航**：Navigation-Compose
- **天气数据**：Open-Meteo API（SDK 内置 HttpURLConnection，零额外依赖）
- **定位**：Android 内置 LocationManager（无 Google Play Services 依赖）

## 数据来源

- **农历**：基于 Android 内置 `android.icu.util.ChineseCalendar`（ICU4J，覆盖 1900-2200 年）
- **节假日**：国务院每年发布的法定节假日安排（本地 `assets/holidays.json`）
- **天气**：[Open-Meteo](https://open-meteo.com/) 免费天气 API（CC BY 4.0，免费层仅限非商用用途）
- **反地理编码**：[Nominatim](https://nominatim.org/) / OpenStreetMap 数据（Android Geocoder 的兜底 fallback）
- **城市数据库**：内置约 140 个中国城市经纬度数据

## 开发环境要求

- JDK 17+
- Android Studio Koala (2024.1.1) 或更高版本
- Android SDK 35

## 构建运行

```bash
# 完整构建
./gradlew build

# 安装 debug APK
./gradlew installDebug

# 跑单元测试
./gradlew testDebugUnitTest

# 单跑某一个测试类
./gradlew testDebugUnitTest --tests "com.minirili.app.utils.LunarCalendarTest"
```

## 目录结构

```
.
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/minirili/app/
│   │   │   │   ├── ui/screens/     # 日历/事件/天气/设置 界面
│   │   │   │   ├── ui/theme/       # 主题配色
│   │   │   │   ├── ui/viewmodel/   # ViewModel
│   │   │   │   ├── navigation/     # 导航配置
│   │   │   │   ├── data/           # 数据层（天气/节假日）
│   │   │   │   ├── database/       # Room 数据库
│   │   │   │   ├── repository/     # 仓库层
│   │   │   │   ├── scheduler/      # 提醒调度
│   │   │   │   ├── receivers/      # BroadcastReceiver
│   │   │   │   ├── workers/        # WorkManager
│   │   │   │   ├── utils/          # 工具类
│   │   │   │   ├── di/             # Hilt 模块
│   │   │   │   └── widgets/        # 桌面小部件
│   │   │   └── res/                # 资源文件
│   │   ├── test/                   # 单元测试
│   │   └── androidTest/            # 仪表化测试
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── 需求文档.md
```

## 开源许可证

[Apache License 2.0](LICENSE)

本应用使用 [Open-Meteo](https://open-meteo.com/) 作为天气数据源，其免费层仅限非商用用途。商用部署需自行订阅 Open-Meteo 或自托管开源版本。