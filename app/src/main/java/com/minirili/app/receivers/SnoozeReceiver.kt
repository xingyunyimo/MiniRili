package com.minirili.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.minirili.app.scheduler.ReminderScheduler
import com.minirili.app.utils.NotificationHelper
import java.util.Calendar

/**
 * 提醒延后处理（REM-05）。
 * 通知栏 action 按钮触发，延后指定时间后重新发出提醒。
 */
class SnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val eventDateStr = intent.getStringExtra(EXTRA_EVENT_DATE) ?: return
        val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "日程提醒"
        val content = intent.getStringExtra(EXTRA_CONTENT) ?: ""

        if (eventId <= 0) return

        // 计算延后时间 = 当前时间 + snoozeMinutes
        val newTime = System.currentTimeMillis() + snoozeMinutes * 60_000L

        // 用 ReminderScheduler 调度新闹钟
        val scheduler = ReminderScheduler(context)
        scheduler.scheduleOccurrence(eventId, 0xFE, eventDateStr, newTime) // 0xFE = snooze 专用 occurrence index

        // 发一条通知告知已延后
        val snoozeLabel = when (snoozeMinutes) {
            5 -> "5分钟"
            60 -> "1小时"
            1440 -> "1天"
            else -> "${snoozeMinutes}分钟"
        }
        NotificationHelper.createNotificationChannel(context)
        val notification = NotificationHelper.buildReminderNotification(
            context = context,
            title = "$title（已延后 $snoozeLabel）",
            content = content,
            eventId = eventId
        )
        NotificationHelper.sendNotification(context, notification)
    }

    companion object {
        const val EXTRA_EVENT_ID = "snooze_event_id"
        const val EXTRA_EVENT_DATE = "snooze_event_date"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
        const val EXTRA_TITLE = "snooze_title"
        const val EXTRA_CONTENT = "snooze_content"

        const val ACTION_SNOOZE = "com.minirili.app.action.SNOOZE"

        /** 创建延后操作的 PendingIntent */
        fun createPendingIntent(
            context: Context,
            eventId: Long,
            eventDate: String,
            snoozeMinutes: Int,
            title: String,
            content: String,
            requestCodeOffset: Int  // 0=5min, 1=1h, 2=1d
        ): android.app.PendingIntent {
            val intent = Intent(context, SnoozeReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(EXTRA_EVENT_ID, eventId)
                putExtra(EXTRA_EVENT_DATE, eventDate)
                putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_CONTENT, content)
            }
            val code = (eventId.toInt() shl 8) or 0xFD + requestCodeOffset
            return android.app.PendingIntent.getBroadcast(
                context,
                code,
                intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}