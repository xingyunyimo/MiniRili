package com.minirili.app.utils

import android.content.Context
import android.content.Intent
import android.os.Build

/** 厂商常驻后台设置检测与跳转引导 */
object AutoStartHelper {

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
        } catch (_: Exception) {
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
                guideHint = "请在「手机管家 → 应用管理 → 权限 → 自启动管理」中找到 迷历 并打开"
            )
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> ManufacturerGuide(
                manufacturerLabel = "华为",
                guideHint = "请在「手机管家 → 应用启动管理」中找到 迷历，关闭「自动管理」并开启「允许自启动」"
            )
            manufacturer.contains("oppo") -> ManufacturerGuide(
                manufacturerLabel = "OPPO",
                guideHint = "请在「手机管家 → 权限隐私 → 自启动管理」中找到 迷历 并打开"
            )
            manufacturer.contains("vivo") -> ManufacturerGuide(
                manufacturerLabel = "vivo",
                guideHint = "请在「i管家 → 应用管理 → 权限管理 → 自启动」中找到 迷历 并打开"
            )
            manufacturer.contains("samsung") -> ManufacturerGuide(
                manufacturerLabel = "三星",
                guideHint = "请在「智能管理器 → 电池 → 后台使用限制」中将 迷历 设为「不限制」"
            )
            else -> null
        }
    }
}

object AppLaunchPrefs {
    private const val PREFS = "calendar_app_launch"
    private const val KEY_AUTOSTART_ASKED = "autostart_asked_at"
    private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000

    fun shouldAskAutoStart(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val askedAt = prefs.getLong(KEY_AUTOSTART_ASKED, 0L)
        return askedAt <= 0L || (System.currentTimeMillis() - askedAt > THIRTY_DAYS_MS)
    }

    fun markAutoStartAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_AUTOSTART_ASKED, System.currentTimeMillis()).apply()
    }

    /** 用于"稍后设置"不记录时间戳——下次启动继续检测 */
    fun shouldKeepAskingAutoStart(context: Context): Boolean = shouldAskAutoStart(context)

    /** 一键标记为"已提示"，用于"30天内不再提示" */
    fun markAllAsked(context: Context) {
        markAutoStartAsked(context)
    }
}