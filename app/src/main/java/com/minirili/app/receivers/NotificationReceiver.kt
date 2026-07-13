package com.minirili.app.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.minirili.app.database.CalendarDatabase
import com.minirili.app.ui.screens.event.EventDetailScreen
import com.minirili.app.ui.viewmodel.EventViewModel
import javax.inject.Inject

/**
 * 通知广播接收器
 * P0-REM-01 一次性提醒
 */
class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra("event_id", -1)
        if (eventId < 0) return
    }
}
