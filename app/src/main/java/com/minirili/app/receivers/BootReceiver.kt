package com.minirili.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.minirili.app.database.CalendarDatabase
import com.minirili.app.scheduler.ReminderScheduler
import com.minirili.app.scheduler.RecurringReminderScheduler

/**
 * 开机自启动接收器
 * P1-REM-06 提醒防漏机制：开机后恢复所有提醒
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val db = CalendarDatabase.getDatabase(context)
            val reminderScheduler = ReminderScheduler(context)
            val recurringScheduler = RecurringReminderScheduler(context, db.eventDao(), reminderScheduler)
            // TODO: 重新调度所有未触发的提醒
        }
    }
}
