package com.minirili.app.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.minirili.app.receivers.AlarmReceiver

/**
 * 提醒调度器
 * P0-REM-01 一次性提醒
 *
 * 使用 AlarmManager 在指定时间触发提醒
 *
 * ⚠️ scheduledCodes 是 companion-level 共享 map。
 * 因为存在多处实例：
 *   - Hilt 注入的单例（EventRepository.update/delete 时走 cancelReminder）
 *   - AlarmReceiver 每次响铃 new 出来 rolling-window 顺延时走 scheduleOccurrence
 *   - ReminderBootReceiver 开机重调度时 new 出来
 *   - RepositoryBootReceiver.rescheduleAllReminders 里再次 new 出新实例
 * 如果 scheduledCodes 是实例字段，取消方永远找不到自己没登记过的代码。
 */
class ReminderScheduler(
    private val context: Context
) {

    companion object {
        private val scheduledCodes = mutableMapOf<Long, MutableSet<Int>>()
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** 调度一次性提醒（触达一次后结束）。requestCode 高 24 位 = eventId，低 8 位留给 recurring 用。 */
    fun scheduleReminder(eventId: Long, eventDateStr: String, reminderTime: Long) {
        val code = (eventId.toInt() shl 8)
        scheduleAt(eventId, code, eventDateStr, reminderTime)
    }

    /** 周期事件的第 occurrenceIndex 次预约（0..255）。eventCode = (eventId << 8) | index。 */
    fun scheduleOccurrence(eventId: Long, occurrenceIndex: Int, eventDateStr: String, reminderTime: Long) {
        if (occurrenceIndex !in 0..255) return
        val code = (eventId.toInt() shl 8) or occurrenceIndex
        scheduleAt(eventId, code, eventDateStr, reminderTime)
    }

    private fun scheduleAt(eventId: Long, requestCode: Int, eventDateStr: String, reminderTime: Long) {
        if (reminderTime <= 0) return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("event_date", eventDateStr)
            putExtra("reminder_time", reminderTime)
            putExtra("event_id", eventId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        synchronized(scheduledCodes) {
            scheduledCodes.getOrPut(eventId) { mutableSetOf() }.add(requestCode)
        }

        if (reminderTime <= System.currentTimeMillis()) return  // 已过去的时间，跳过

        try {
            // setAlarmClock 是系统闹钟使用的最高优先级调度方式，
            // 在深度 Doze（锁屏待机）下仍能准时触发并充分唤醒音频子系统播放铃声。
            // setExactAndAllowWhileIdle 在深度 Doze 下接收器可以启动（通知正常），但音频子系统未必就绪，
            // 导致铃声推迟到用户按下电源键才播放。
            val alarmInfo = AlarmManager.AlarmClockInfo(reminderTime, pendingIntent)
            alarmManager.setAlarmClock(alarmInfo, pendingIntent)
        } catch (_: SecurityException) {
        }
    }

    /** 取消指定事件所有已预约的提醒（含一次性 + 所有 occurrence）。跨实例有效。 */
    fun cancelReminder(eventId: Long) {
        val codes = synchronized(scheduledCodes) {
            scheduledCodes.remove(eventId)?.toSet() ?: emptySet()
        }
        codes.forEach { cancelRequest(it) }
    }

    private fun cancelRequest(requestCode: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        // FLAG_NO_CREATE：拿到已注册过的 PendingIntent；没注册过则跳过，避免误创建
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        ) ?: return
        try {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        } catch (_: Exception) {
        }
    }

    /** 取消所有该日期的提醒（一次性）。当前未使用。 */
    fun cancelAllForDate(eventDateStr: String) {
        // 非核心路径。需要可按 DB 查询日期所有 eventId 后调用 cancelReminder，当前留 TODO
    }
}
