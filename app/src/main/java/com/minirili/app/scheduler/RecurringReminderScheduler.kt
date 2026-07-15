package com.minirili.app.scheduler

import android.app.AlarmManager
import android.content.Context
import com.minirili.app.database.dao.EventDao
import com.minirili.app.database.entity.EventEntity
import com.minirili.app.data.HolidayService
import com.minirili.app.utils.DateUtils
import com.minirili.app.utils.LunarCalendar
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

    private fun scheduleDailyReminder(event: EventEntity, baseDate: Calendar) {
        val calendar = baseDate.clone() as Calendar
        for (i in 0..39) {                       // 扩展为 40 天，确保 rolling-window 前有足够缓冲
            if (i > 0) calendar.add(Calendar.DAY_OF_MONTH, 1)
            val reminderTime = calculateReminderTime(calendar, event.reminderTime, event.reminderOffset)
            if (reminderTime <= System.currentTimeMillis()) continue  // 当天已过则跳过
            reminderScheduler.scheduleOccurrence(event.id, i, DateUtils.formatGregorian(calendar), reminderTime)
        }
    }

    private fun scheduleWeeklyReminder(event: EventEntity, baseDate: Calendar) {
        val calendar = baseDate.clone() as Calendar
        // 本周若尚未过提醒时间，先预约本周；之后每周一次 × 11 周
        var scheduled = 0
        val first = calculateReminderTime(calendar, event.reminderTime, event.reminderOffset)
        if (first > System.currentTimeMillis()) {
            reminderScheduler.scheduleOccurrence(event.id, scheduled, DateUtils.formatGregorian(calendar), first)
            scheduled++
        }
        for (w in 1..11) {
            calendar.add(Calendar.WEEK_OF_YEAR, 1)
            val reminderTime = calculateReminderTime(calendar, event.reminderTime, event.reminderOffset)
            reminderScheduler.scheduleOccurrence(event.id, scheduled, DateUtils.formatGregorian(calendar), reminderTime)
            scheduled++
        }
    }

    private fun scheduleMonthlyReminder(event: EventEntity, baseDate: Calendar) {
        val dayOfMonth = baseDate.get(Calendar.DAY_OF_MONTH)
        val calendar = baseDate.clone() as Calendar
        var scheduled = 0
        // 本月同日若尚未过提醒时间，先预约
        val first = calculateReminderTime(calendar, event.reminderTime, event.reminderOffset)
        if (first > System.currentTimeMillis()) {
            reminderScheduler.scheduleOccurrence(event.id, scheduled, DateUtils.formatGregorian(calendar), first)
            scheduled++
        }
        for (m in 1..11) {
            calendar.add(Calendar.MONTH, 1)
            if (calendar.get(Calendar.DAY_OF_MONTH) < dayOfMonth) {
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            }
            val reminderTime = calculateReminderTime(calendar, event.reminderTime, event.reminderOffset)
            reminderScheduler.scheduleOccurrence(event.id, scheduled, DateUtils.formatGregorian(calendar), reminderTime)
            scheduled++
        }
    }

    private fun scheduleYearlyReminder(event: EventEntity, baseDate: Calendar) {
        val month = baseDate.get(Calendar.MONTH)
        val dayOfMonth = baseDate.get(Calendar.DAY_OF_MONTH)
        val calendar = baseDate.clone() as Calendar
        var scheduled = 0
        val first = calculateReminderTime(calendar, event.reminderTime, event.reminderOffset)
        if (first > System.currentTimeMillis()) {
            reminderScheduler.scheduleOccurrence(event.id, scheduled, DateUtils.formatGregorian(calendar), first)
            scheduled++
        }
        for (y in 1..9) {
            calendar.add(Calendar.YEAR, 1)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            val reminderTime = calculateReminderTime(calendar, event.reminderTime, event.reminderOffset)
            reminderScheduler.scheduleOccurrence(event.id, scheduled, DateUtils.formatGregorian(calendar), reminderTime)
            scheduled++
        }
    }

    private fun scheduleLunarMonthlyReminder(event: EventEntity, baseDate: Calendar) {
        val parts = LunarCalendar.toLunarParts(baseDate)
        var year = parts.yearBase
        var month = parts.month
        val day = parts.day
        var scheduled = 0
        val todayStr = DateUtils.formatGregorian(baseDate)

        // 当月
        var gregDate = LunarCalendar.lunarToGregorian(year, month, day)
        if (gregDate != null && gregDate >= todayStr) {
            val cal = DateUtils.parseGregorian(gregDate)
            val triggerTime = calculateReminderTime(cal, event.reminderTime, event.reminderOffset)
            if (triggerTime > System.currentTimeMillis()) {
                reminderScheduler.scheduleOccurrence(event.id, scheduled, gregDate, triggerTime)
                scheduled++
            }
        }
        // 后续月份：逐月递进，跨年则年份+1
        var attempts = 0
        while (scheduled < 12 && attempts < 36) {
            month++
            if (month > 12) { month = 1; year++ }
            attempts++
            gregDate = LunarCalendar.lunarToGregorian(year, month, day)
            if (gregDate != null) {
                val cal = DateUtils.parseGregorian(gregDate)
                val triggerTime = calculateReminderTime(cal, event.reminderTime, event.reminderOffset)
                reminderScheduler.scheduleOccurrence(event.id, scheduled, gregDate, triggerTime)
                scheduled++
            }
        }
    }

    private fun scheduleLunarYearlyReminder(event: EventEntity, baseDate: Calendar) {
        val parts = LunarCalendar.toLunarParts(baseDate)
        val month = parts.month
        val day = parts.day
        var year = parts.yearBase
        var scheduled = 0
        val todayStr = DateUtils.formatGregorian(baseDate)

        // 当年
        var gregDate = LunarCalendar.lunarToGregorian(year, month, day)
        if (gregDate != null && gregDate >= todayStr) {
            val cal = DateUtils.parseGregorian(gregDate)
            val triggerTime = calculateReminderTime(cal, event.reminderTime, event.reminderOffset)
            if (triggerTime > System.currentTimeMillis()) {
                reminderScheduler.scheduleOccurrence(event.id, scheduled, gregDate, triggerTime)
                scheduled++
            }
        }
        // 后续年份
        for (y in 1..9) {
            year++
            gregDate = LunarCalendar.lunarToGregorian(year, month, day)
            if (gregDate != null) {
                val cal = DateUtils.parseGregorian(gregDate)
                val triggerTime = calculateReminderTime(cal, event.reminderTime, event.reminderOffset)
                reminderScheduler.scheduleOccurrence(event.id, scheduled, gregDate, triggerTime)
                scheduled++
            }
        }
    }

    private fun scheduleWorkdayReminder(event: EventEntity, baseDate: Calendar) {
        val calendar = baseDate.clone() as Calendar
        var scheduled = 0
        var index = 0
        // 今天如果是工作日（非节假日 + 非周末，或调休补班日）且未过，也预约
        val todayStr = DateUtils.formatGregorian(calendar)
        if (HolidayService.isWorkday(todayStr)) {
            val first = calculateReminderTime(calendar, event.reminderTime, event.reminderOffset)
            if (first > System.currentTimeMillis()) {
                reminderScheduler.scheduleOccurrence(event.id, scheduled, todayStr, first)
                scheduled++
            }
        }
        while (scheduled < 30 && index < 90) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            val dateStr = DateUtils.formatGregorian(calendar)
            if (HolidayService.isWorkday(dateStr)) {
                val reminderTime = calculateReminderTime(calendar, event.reminderTime, event.reminderOffset)
                reminderScheduler.scheduleOccurrence(event.id, scheduled, dateStr, reminderTime)
                scheduled++
            }
            index++
        }
    }

    private fun scheduleWeekendReminder(event: EventEntity, baseDate: Calendar) {
        val calendar = baseDate.clone() as Calendar
        var scheduled = 0
        var index = 0
        val dayOfWeek0 = calendar.get(Calendar.DAY_OF_WEEK)
        if (dayOfWeek0 == Calendar.SATURDAY || dayOfWeek0 == Calendar.SUNDAY) {
            val first = calculateReminderTime(calendar, event.reminderTime, event.reminderOffset)
            if (first > System.currentTimeMillis()) {
                reminderScheduler.scheduleOccurrence(event.id, scheduled, DateUtils.formatGregorian(calendar), first)
                scheduled++
            }
        }
        while (scheduled < 12 && index < 90) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                val reminderTime = calculateReminderTime(calendar, event.reminderTime, event.reminderOffset)
                reminderScheduler.scheduleOccurrence(event.id, scheduled, DateUtils.formatGregorian(calendar), reminderTime)
                scheduled++
            }
            index++
        }
    }

    /**
     * 计算给定日期的提醒触发时刻。
     *
     * hour/minute 取自 `baseReminderTimeMs`（即事件时间，不含偏移），
     * 应用到 occurrence 日期后，再减去 `offsetMinutes` 得到闹钟触发时刻。
     * 全天事件（reminderTime <= 0）fallback 到中午 12:00。
     */
    private fun calculateReminderTime(calendar: Calendar, baseReminderTimeMs: Long, offsetMinutes: Int): Long {
        if (baseReminderTimeMs > 0) {
            val baseCal = Calendar.getInstance().apply { timeInMillis = baseReminderTimeMs }
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
