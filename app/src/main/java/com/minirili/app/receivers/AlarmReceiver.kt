package com.minirili.app.receivers

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.minirili.app.database.CalendarDatabase
import com.minirili.app.scheduler.ReminderScheduler
import com.minirili.app.scheduler.RecurringReminderScheduler
import com.minirili.app.utils.NotificationHelper
import com.minirili.app.receivers.SnoozeReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private var activeMediaPlayer: MediaPlayer? = null
        private var activeRingtone: android.media.Ringtone? = null
        private var activeWakeLock: PowerManager.WakeLock? = null
        private const val STOP_ALARM_AFTER_MS = 30_000L
        private var screenOffReceiver: BroadcastReceiver? = null
        private var screenOffReceiverRegistered = false
        @Volatile private var screenOffAppContext: Context? = null

        // 互斥锁解决竞争：stopAlarm 在 playAlarmSound 之前调用时，标记为已请求停。
        // playAlarmSound 启动前/启动后检查此标志，防止"把 stop 错过但铃声已启动"。
        @Volatile
        private var stopRequested = false

        const val ACTION_DISMISS_ALARM = "com.minirili.app.action.DISMISS_ALARM"

        /** 让外部（用户确认事件后）能主动停止正在播放的闹钟 */
        fun stopAlarm() {
            stopRequested = true
            activeMediaPlayer?.let {
                try { if (it.isPlaying) it.stop(); it.release() } catch (_: Exception) {}
            }
            activeMediaPlayer = null
            activeRingtone?.let {
                try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
            }
            activeRingtone = null
            activeWakeLock?.let { try { if (it.isHeld) it.release() } catch (_: Exception) {} }
            activeWakeLock = null
            screenOffAppContext?.let { ctx -> unregisterScreenOffStopper(ctx) }
        }

        private fun unregisterScreenOffStopper(context: Context) {
            if (!screenOffReceiverRegistered) return
            runCatching { context.applicationContext.unregisterReceiver(screenOffReceiver!!) }
            screenOffReceiver = null
            screenOffReceiverRegistered = false
        }

        /** EVT-10: 闹钟触发时调用，判断本次触发是否被用户跳到（触发日期在 event.skipDates 中） */
        fun isOccurrenceSkipped(event: com.minirili.app.database.entity.EventEntity?, eventDateStr: String): Boolean {
            if (event == null || event.skipDates.isBlank()) return false
            val normalized = eventDateStr.trim()
            if (normalized.isEmpty()) return false
            val skipped = event.skipDates.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            return skipped.contains(normalized)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // 通知栏划掉 → 同步停闹钟
        if (intent.action == ACTION_DISMISS_ALARM) {
            stopAlarm()
            return
        }

        // 持有唤醒锁：即使 CPU 短暂休眠，铃声和振动也会仍在执行
        acquireWakeLock(context)

        val eventId = intent.getLongExtra("event_id", -1L)
        val eventDateStr = intent.getStringExtra("event_date") ?: ""
                val reminderTime = intent.getLongExtra("reminder_time", 0)

                if (eventId > 0) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = CalendarDatabase.getDatabase(context)
                            val event = db.eventDao().getEventById(eventId)
                            val title = event?.title?.takeIf { it.isNotBlank() } ?: context.appName()
                            val wantNotification = event?.notifyNotification ?: true
                            val wantAlarm = event?.notifyAlarm ?: true
                            val timeText = if (event?.reminderTime != null && event.reminderTime > 0) {
                                val cal = java.util.Calendar.getInstance().apply { timeInMillis = event.reminderTime }
                                String.format(
                                    "%02d:%02d",
                                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                                    cal.get(java.util.Calendar.MINUTE)
                                )
                            } else "全天"
                            val content = "$timeText · $eventDateStr"

                            // EVT-10: 周期事件例外 —— 触发日期在 skipDates 中则静默跳过
                            if (AlarmReceiver.isOccurrenceSkipped(event, eventDateStr)) {
                                releaseWakeLock()
                                return@launch
                            }

                            if (wantNotification) {
                                NotificationHelper.createNotificationChannel(context)
                                // 通知栏划掉这条通知 → 同步停闹钟
                                val dismissPendingIntent = PendingIntent.getBroadcast(
                                    context,
                                    (eventId.toInt() and 0xFFFFFF) or 0x40000000,
                                    Intent(context, AlarmReceiver::class.java).apply {
                                        action = ACTION_DISMISS_ALARM
                                    },
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                )
                                val notification = NotificationHelper.buildReminderNotification(
                                    context,
                                    title,
                                    content,
                                    eventId
                                ).apply {
                                    // REM-05: 提醒后延后操作按钮
                                    addAction(0, "延后 5分钟",
                                        SnoozeReceiver.createPendingIntent(context, eventId, eventDateStr, 5, title, content, 0))
                                    addAction(0, "延后 1小时",
                                        SnoozeReceiver.createPendingIntent(context, eventId, eventDateStr, 60, title, content, 1))
                                    addAction(0, "延后 1天",
                                        SnoozeReceiver.createPendingIntent(context, eventId, eventDateStr, 1440, title, content, 2))
                                    setDeleteIntent(dismissPendingIntent)
                                }
                                NotificationHelper.sendNotification(context, notification)
                            }
                            if (wantAlarm && event != null) {
                                playAlarmSound(context)
                                vibrate(context)
                            }

                            // 周期事件：每次触发后顺延预约，保证"永远每天/每周…"
                            if (event != null && event.repeatType != "none") {
                                rescheduleNextOccurrence(context, event, eventDateStr)
                            }
                        } catch (_: Exception) {
                        } finally {
                            releaseWakeLock()
                        }
                    }
                } else if (eventDateStr.isNotEmpty() && reminderTime > 0) {
                    sendReminderNotification(context, eventDateStr, reminderTime)
                    releaseWakeLock()
                } else {
                    releaseWakeLock()
                }
    }

    private fun Context.appName(): String = try {
        getString(applicationInfo.labelRes)
    } catch (_: Exception) { "日程提醒" }

    /** 周期事件本次触发后，以本次为基续预约下一轮。这样用户不修改事件，提醒就一直延续。 */
    private fun rescheduleNextOccurrence(
        context: Context,
        event: com.minirili.app.database.entity.EventEntity,
        currentDateStr: String
    ) {
        runCatching {
            val reminderScheduler = ReminderScheduler(context)
            val recurringScheduler = RecurringReminderScheduler(
                context, CalendarDatabase.getDatabase(context).eventDao(), reminderScheduler
            )
            recurringScheduler.scheduleNextOccurrence(event, currentDateStr)
        }
    }

    /** 闹钟响起时，在 applicationContext 上注册一个内部 SCREEN_OFF 监听。
     *  独立于 MainActivity：即使 activity 销毁（app 在后台），screen off 也能被截获并停闹钟。 */
    private fun registerScreenOffStopper(context: Context) {
        if (screenOffReceiverRegistered) return
        val appContext = context.applicationContext
        screenOffAppContext = appContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) stopAlarm()
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
        screenOffReceiver = receiver
        screenOffReceiverRegistered = true
    }

    private fun acquireWakeLock(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            releaseWakeLock()
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MiniRili::AlarmReceiver")
            wl.setReferenceCounted(false)
            wl.acquire(STOP_ALARM_AFTER_MS + 2000L)
            activeWakeLock = wl
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            activeWakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {}
        activeWakeLock = null
    }

    private fun playAlarmSound(context: Context) {
        // 每次新闹钟触发时重置停止标志，避免从 stopAlarm()（MainActivity.onCreate/onResume 调用）
        // 遗留下来的 true 状态静音本次闹钟。
        stopRequested = false
        registerScreenOffStopper(context)
        if (stopRequested) return

        // 优先尝试 Ringtone API — Android 10+ 在 Doze 下仍可靠
        if (playWithRingtone(context)) {
            return
        }
        // 兜底：MediaPlayer + USAGE_ALARM
        playWithMediaPlayer(context)
    }

    private fun playWithRingtone(context: Context): Boolean {
        return try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return false
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            // 多数 Android 铃声只能播放一次，靠循环线程持续启动直到 stopRequested
            activeRingtone = ringtone
            Thread {
                try {
                    while (!stopRequested && activeRingtone != null) {
                        if (!ringtone.isPlaying) ringtone.play()
                        Thread.sleep(500)
                    }
                } catch (_: InterruptedException) {
                    // 被打断即退出循环，正常
                }
            }.also { it.isDaemon = true; it.name = "MiniRiliRingtoneLoop" }.start()
            android.os.Handler(context.mainLooper).postDelayed({
                stopRingtone()
                unregisterScreenOffStopper(context)
            }, STOP_ALARM_AFTER_MS)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun stopRingtone() {
        try {
            activeRingtone?.let {
                if (it.isPlaying) it.stop()
                // Ringtone 实例无 release API；置空即可
            }
        } catch (_: Exception) {}
        activeRingtone = null
    }

    private fun playWithMediaPlayer(context: Context) {
        try {
            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, alarmUri)
                isLooping = true
                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }
            activeMediaPlayer = mp
            if (stopRequested) {
                releaseMediaPlayer()
                return
            }
            android.os.Handler(context.mainLooper).postDelayed({
                releaseMediaPlayer()
                unregisterScreenOffStopper(context)
            }, STOP_ALARM_AFTER_MS)
        } catch (_: Exception) {
        }
    }

    private fun releaseMediaPlayer() {
        try {
            activeMediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {}
        activeMediaPlayer = null
    }

    @Suppress("DEPRECATION")
    private fun vibrate(context: Context) {
        try {
            val pattern = longArrayOf(0, 400, 200, 400, 200, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        } catch (_: Exception) {
        }
    }

    private fun sendReminderNotification(context: Context, eventDateStr: String, reminderTime: Long) {
        NotificationHelper.createNotificationChannel(context)
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = reminderTime }
        val title = "日程提醒"
        val content = "事件在 $eventDateStr ${calendar.get(java.util.Calendar.HOUR_OF_DAY)}:${String.format("%02d", calendar.get(java.util.Calendar.MINUTE))}"
        val notification = NotificationHelper.buildReminderNotification(context, title, content, -1L)
        NotificationHelper.sendNotification(context, notification)
    }
}
