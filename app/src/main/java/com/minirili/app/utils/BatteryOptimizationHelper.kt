package com.minirili.app.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** 电池优化白名单 + 厂商常驻后台设置 检测与跳转引导 */
object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 构建跳转电池优化白名单的 Intent 列表，按优先级返回：
     * 1. 系统标准对话框（RequestIgnore，针对本包）— Android 6-9/原生系统有效
     * 2. 系统级电池优化设置主页（IgnoreBatteryOptimizationSettings）— Android 6+ 通用
     * 3. 应用详情页（用户手动在「电池」细项里改）— 兜底
     *
     * 调用方按列表依次尝试 startActivity，每个失败则换下一个。
     */
    fun buildRequestIntents(context: Context): List<Intent> {
        val list = mutableListOf<Intent>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 1. 单包询问对话框（多数机器直跳到「不优化」开关）
            runCatching {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }.getOrNull()?.let { list.add(it) }

            // 2. 全局电池优化设置主页（fallback：原生 Android 10+ 仍支持）
            runCatching {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }.getOrNull()?.let { list.add(it) }

            // 3. 应用详情页（兜底 — 用户自己进「电池」/「后台」子项）
            runCatching {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }.getOrNull()?.let { list.add(it) }
        }
        return list
    }

    /** 旧 API 兼容：返回第一个 Intent，保留旧调用方 */
    fun buildRequestIntent(context: Context): Intent? = buildRequestIntents(context).firstOrNull()

    /** 尝试依次跳转，返回是否成功跳到某个目标页 */
    fun startBatterySettings(context: Context): Boolean {
        for (intent in buildRequestIntents(context)) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // 该目标在当前设备不存在，试下一个
            } catch (_: SecurityException) {
                // 个别厂商对 REQUEST_IGNORE 限制，继续 fallback
            }
        }
        return false
    }

    /** 当前设备厂商是否有定制"启动管理 / 常驻后台"设置页面 */
    fun hasAutoStartSettings(context: Context): Boolean = buildAutoStartIntent(context) != null

    /** 厂商"启动管理 / 常驻后台"设置页。不同厂商不同 Intent，失败时返回 null */
    fun buildAutoStartIntent(context: Context): Intent? {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        return try {
            val intent = Intent().apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            when {
                manufacturer.contains("xiaomi") -> {
                    intent.setClassName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
                manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                    intent.setClassName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startup.ui.StartupNormalAppListActivity"
                    )
                }
                manufacturer.contains("oppo") -> {
                    intent.setClassName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startup.StartupAppListActivity"
                    )
                }
                manufacturer.contains("vivo") -> {
                    intent.setClassName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                    )
                }
                manufacturer.contains("samsung") -> {
                    intent.setClassName(
                        "com.samsung.android.sm_cn",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                }
                else -> return null
            }
            if (context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()) intent else null
        } catch (e: Exception) {
            null
        }
    }

    /** 厂商中文名 + 路径指引，用于 AutoStartDialog 文案 */
    data class ManufacturerGuide(
        val manufacturerLabel: String,   // 中文名：如"小米"、"华为"
        val guideHint: String,            // 引导路径文本
    )

    /** 按当前设备厂商返回中文指引，非支持厂商返回 null */
    fun getManufacturerGuide(): ManufacturerGuide? {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        return when {
            manufacturer.contains("xiaomi") -> ManufacturerGuide(
                manufacturerLabel = "小米",
                guideHint = "请在「手机管家 → 应用管理 → 权限 → 自启动管理」中找到 MiniRili 并打开"
            )
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> ManufacturerGuide(
                manufacturerLabel = "华为",
                guideHint = "请在「手机管家 → 应用启动管理」中找到 MiniRili，关闭「自动管理」并开启「允许自启动」"
            )
            manufacturer.contains("oppo") -> ManufacturerGuide(
                manufacturerLabel = "OPPO",
                guideHint = "请在「手机管家 → 权限隐私 → 自启动管理」中找到 MiniRili 并打开"
            )
            manufacturer.contains("vivo") -> ManufacturerGuide(
                manufacturerLabel = "vivo",
                guideHint = "请在「i管家 → 应用管理 → 权限管理 → 自启动」中找到 MiniRili 并打开"
            )
            manufacturer.contains("samsung") -> ManufacturerGuide(
                manufacturerLabel = "三星",
                guideHint = "请在「智能管理器 → 电池 → 后台使用限制」中将 MiniRili 设为「不限制」"
            )
            else -> null
        }
    }
}

object AppLaunchPrefs {
    private const val PREFS = "calendar_app_launch"
    private const val KEY_BATTERY_ASKED = "battery_asked_at"
    private const val KEY_AUTOSTART_ASKED = "autostart_asked_at"
    private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000

    fun shouldAskBattery(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val askedAt = prefs.getLong(KEY_BATTERY_ASKED, 0L)
        return askedAt <= 0L || (System.currentTimeMillis() - askedAt > THIRTY_DAYS_MS)
    }

    fun markBatteryAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_BATTERY_ASKED, System.currentTimeMillis()).apply()
    }

    fun shouldAskAutoStart(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val askedAt = prefs.getLong(KEY_AUTOSTART_ASKED, 0L)
        return askedAt <= 0L || (System.currentTimeMillis() - askedAt > THIRTY_DAYS_MS)
    }

    fun markAutoStartAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_AUTOSTART_ASKED, System.currentTimeMillis()).apply()
    }

    /** 一键标记两项均为"已询问"，用于合并对话框统一关闭 */
    fun markAllAsked(context: Context) {
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_BATTERY_ASKED, now)
            .putLong(KEY_AUTOSTART_ASKED, now)
            .apply()
    }
}
