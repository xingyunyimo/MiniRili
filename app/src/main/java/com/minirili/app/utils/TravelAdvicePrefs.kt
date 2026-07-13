package com.minirili.app.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * 出行建议每日定时通知设置（Bug6）
 *
 * 默认：开启，每天 08:00 推送出行建议。
 * 用户可在天气页"出行建议"卡片右上角齿轮按钮中修改时间或关闭。
 */
object TravelAdvicePrefs {
    private const val PREFS = "travel_advice_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"

    const val DEFAULT_HOUR = 8
    const val DEFAULT_MINUTE = 0

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
    }

    fun getHour(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_HOUR, DEFAULT_HOUR)
    }

    fun getMinute(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_MINUTE, DEFAULT_MINUTE)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        reschedule(context)
    }

    fun setTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_MINUTE, minute.coerceIn(0, 59))
            .apply()
        reschedule(context)
    }

    private const val REQUEST_CODE = 0xCA11  // "CALL" 位段，独立编号避免与事件提醒冲突

    /** 重新注册下一次出行建议通知触发时间 */
    fun reschedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, com.minirili.app.receivers.DailyTravelAdviceReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // 先取消已注册的
        runCatching { am.cancel(pi) }

        if (!isEnabled(context)) return

        val triggerAt = nextTriggerTime(context)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            // 无精确闹钟权限时静默退化为普通 setInexactReboot
            runCatching { am.setInexactRepeating(AlarmManager.RTC_WAKEUP, triggerAt, AlarmManager.INTERVAL_DAY, pi) }
        }
    }

    private fun nextTriggerTime(context: Context): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, getHour(context))
            set(Calendar.MINUTE, getMinute(context))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // 若今天时间已过，定到明天
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return cal.timeInMillis
    }
}
