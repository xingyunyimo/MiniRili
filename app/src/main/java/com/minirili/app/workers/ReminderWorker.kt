package com.minirili.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 提醒 Worker
 * P1-REM-06 提醒防漏机制：处理错过和待触发的提醒
 */
class ReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // 1. 处理所有已过期的提醒
        handleMissedReminders()

        // 2. 调度即将到来的提醒
        scheduleUpcomingReminders()

        return Result.success()
    }

    private suspend fun handleMissedReminders() {
        // TODO: 从数据库查询已过期但未发送的提醒并发送通知
    }

    private suspend fun scheduleUpcomingReminders() {
        // TODO: 从数据库查询 1 天内的提醒并调度到 AlarmManager
    }
}