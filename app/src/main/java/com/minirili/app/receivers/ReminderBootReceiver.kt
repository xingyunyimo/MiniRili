package com.minirili.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.minirili.app.database.CalendarDatabase
import com.minirili.app.scheduler.ReminderScheduler
import com.minirili.app.scheduler.RecurringReminderScheduler

/**
 * 提醒防漏接收器
 * P1-REM-06 提醒防漏机制：开机自启、时间变更后恢复提醒
 *
 * 负责在系统启动或时间变更后重新调度所有提醒（含一次性 + 周期事件）。
 * 注意：周期事件的续约会随着每次触发自动前滚，这里只作为"兜底"补充防护。
 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val db = CalendarDatabase.getDatabase(appContext)
        val reminderScheduler = ReminderScheduler(appContext)
        val recurringScheduler = RecurringReminderScheduler(
            appContext, db.eventDao(), reminderScheduler
        )

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_DATE_CHANGED -> {
                kotlinx.coroutines.runBlocking {
                    recurringScheduler.rescheduleAllReminders()
                }
            }
        }
    }
}
