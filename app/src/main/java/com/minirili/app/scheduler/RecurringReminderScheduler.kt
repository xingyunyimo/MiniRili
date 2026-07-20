package com.minirili.app.scheduler

import android.app.AlarmManager
import android.content.Context
import com.minirili.app.database.dao.EventDao
import com.minirili.app.database.entity.EventEntity
import com.minirili.app.utils.DateUtils
import com.minirili.app.utils.RecurrenceEngine
import kotlinx.coroutines.flow.first
import java.util.Calendar

class RecurringReminderScheduler(
    private val context: Context,
    private val eventDao: EventDao,
    private val reminderScheduler: ReminderScheduler
) {

    fun scheduleRecurringReminder(event: EventEntity, baseDate: String) {
        val calendar = DateUtils.parseGregorian(baseDate)
        when (event.repeatType) {
            "daily" -> scheduleDailyReminder(event, calendar)
            "weekly" -> scheduleWeeklyReminder(event, calendar)
            "monthly" -> if (event.useLunar) scheduleLunarMonthlyReminder(event, calendar)
                         else scheduleMonthlyReminder(event, calendar)
            "yearly" -> if (event.useLunar) scheduleLunarYearlyReminder(event, calendar)
                        else scheduleYearlyReminder(event, calendar)
            "workday" -> scheduleWorkdayReminder(event, calendar)
            "weekend" -> scheduleWeekendReminder(event, calendar)
            else -> {
                val reminderTime = calculateReminderTime(calendar, event.reminderTime, event.reminderOffset)
                if (reminderTime > System.currentTimeMillis()) {
                    reminderScheduler.scheduleReminder(event.id, event.gregorianDate, reminderTime)
                }
            }
        }
    }

    /** 通用：从引擎获取日期列表，调度闹钟 */
    private fun scheduleFromEngine(event: EventEntity, baseDate: Calendar, endDate: Calendar) {
        val baseStr = DateUtils.formatGregorian(baseDate)
        val endStr = DateUtils.formatGregorian(endDate)
        val occurrences = RecurrenceEngine.expandForRange(
            listOf(event), baseStr, endStr, excludeSkipDates = false
        )
        occurrences.forEachIndexed { index, occ ->
            val cal = DateUtils.parseGregorian(occ.occurrenceDate)
            val triggerTime = calculateReminderTime(cal, event.reminderTime, event.reminderOffset)
            if (triggerTime > System.currentTimeMillis()) {
                reminderScheduler.scheduleOccurrence(event.id, index, occ.occurrenceDate, triggerTime)
            }
        }
    }

    private fun scheduleDailyReminder(event: EventEntity, baseDate: Calendar) {
        val end = baseDate.clone() as Calendar
        end.add(Calendar.DAY_OF_MONTH, 39) // 40 days total
        scheduleFromEngine(event, baseDate, end)
    }

    private fun scheduleWeeklyReminder(event: EventEntity, baseDate: Calendar) {
        val end = baseDate.clone() as Calendar
        end.add(Calendar.WEEK_OF_YEAR, 11) // 12 weeks total
        scheduleFromEngine(event, baseDate, end)
    }

    private fun scheduleMonthlyReminder(event: EventEntity, baseDate: Calendar) {
        val end = baseDate.clone() as Calendar
        end.add(Calendar.MONTH, 11) // 12 months total
        scheduleFromEngine(event, baseDate, end)
    }

    private fun scheduleYearlyReminder(event: EventEntity, baseDate: Calendar) {
        val end = baseDate.clone() as Calendar
        end.add(Calendar.YEAR, 9) // 10 years total
        scheduleFromEngine(event, baseDate, end)
    }

    private fun scheduleLunarMonthlyReminder(event: EventEntity, baseDate: Calendar) {
        val end = baseDate.clone() as Calendar
        end.add(Calendar.YEAR, 1) // ~12 lunar months
        scheduleFromEngine(event, baseDate, end)
    }

    private fun scheduleLunarYearlyReminder(event: EventEntity, baseDate: Calendar) {
        val end = baseDate.clone() as Calendar
        end.add(Calendar.YEAR, 9) // 10 years total
        scheduleFromEngine(event, baseDate, end)
    }

    private fun scheduleWorkdayReminder(event: EventEntity, baseDate: Calendar) {
        // 90 days buffer should cover ~30 workdays
        val end = baseDate.clone() as Calendar
        end.add(Calendar.DAY_OF_MONTH, 90)
        scheduleFromEngine(event, baseDate, end)
    }

    private fun scheduleWeekendReminder(event: EventEntity, baseDate: Calendar) {
        // 90 days buffer should cover ~12 weekends
        val end = baseDate.clone() as Calendar
        end.add(Calendar.DAY_OF_MONTH, 90)
        scheduleFromEngine(event, baseDate, end)
    }

    /**
     * 计算给定日期的提醒触发时刻。
     *
     * hour/minute 取自 `baseReminderTimeMs`（即事件时间，不含偏移），
     * 应用到 occurrence 日期后，再减去 `offsetMinutes` 得到闹钟触发时刻。
     * 全天事件（reminderTime <= 0）fallback 到中午 12:00。
     */
    fun calculateReminderTime(calendar: Calendar, baseReminderTimeMs: Long, offsetMinutes: Int): Long {
        if (baseReminderTimeMs != 0L) {
            val baseCal = Calendar.getInstance().apply { timeInMillis = kotlin.math.abs(baseReminderTimeMs) }
            calendar.set(Calendar.HOUR_OF_DAY, baseCal.get(Calendar.HOUR_OF_DAY))
            calendar.set(Calendar.MINUTE, baseCal.get(Calendar.MINUTE))
        } else {
            calendar.set(Calendar.HOUR_OF_DAY, 12)
            calendar.set(Calendar.MINUTE, 0)
        }
        calendar.set(Calendar.SECOND, 0)
        return calendar.timeInMillis - (offsetMinutes * 60L * 1000L)
    }

    suspend fun rescheduleAllReminders() {
        val events = eventDao.getAllEvents()
        val allEvents: List<EventEntity> = events.first()
        allEvents.forEach { event ->
            reminderScheduler.cancelReminder(event.id)
            if (event.repeatType != "none") {
                val baseDate = getBaseDateForRecurring(event)
                if (baseDate != null) {
                    scheduleRecurringReminder(event, baseDate)
                }
            } else if (event.reminderTime > 0) {
                val triggerTime = event.reminderTime - event.reminderOffset * 60L * 1000L
                reminderScheduler.scheduleReminder(event.id, event.gregorianDate, triggerTime)
            }
        }
    }

    /**
     * 周期事件本次触发后，以本次为基续预约下一轮。保证"永远每天/每周…"。
     * 实现：取消该 event 未触发的预约，以 currentDateStr 为基重新预约未来 N 次。
     */
    fun scheduleNextOccurrence(event: EventEntity, currentDateStr: String) {
        val baseDate = DateUtils.parseGregorian(currentDateStr) ?: return
        reminderScheduler.cancelReminder(event.id)
        when (event.repeatType) {
            "daily" -> scheduleDailyReminder(event, baseDate)
            "weekly" -> scheduleWeeklyReminder(event, baseDate)
            "monthly" -> if (event.useLunar) scheduleLunarMonthlyReminder(event, baseDate)
                         else scheduleMonthlyReminder(event, baseDate)
            "yearly" -> if (event.useLunar) scheduleLunarYearlyReminder(event, baseDate)
                        else scheduleYearlyReminder(event, baseDate)
            "workday" -> scheduleWorkdayReminder(event, baseDate)
            "weekend" -> scheduleWeekendReminder(event, baseDate)
            else -> {}
        }
    }

    private fun getBaseDateForRecurring(event: EventEntity): String? {
        return when (event.repeatType) {
            "daily", "weekly", "monthly", "yearly", "workday", "weekend" -> event.gregorianDate
            else -> null
        }
    }
}
