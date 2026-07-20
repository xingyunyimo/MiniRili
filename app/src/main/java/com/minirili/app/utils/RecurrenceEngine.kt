package com.minirili.app.utils

import com.minirili.app.data.HolidayService
import com.minirili.app.database.entity.EventEntity
import java.util.Calendar

/**
 * 重复事件日期计算引擎。
 *
 * 唯一职责：给定 [EventEntity] 和日期范围，计算该范围内所有出现日期。
 * 不涉及 AlarmManager、闹钟调度或任何 UI 逻辑。
 *
 * [RecurringReminderScheduler] 也委托此引擎计算日期，使重复逻辑只有一份。
 */
data class EventOccurrence(
    val event: EventEntity,
    val occurrenceDate: String // YYYY-MM-DD
)

object RecurrenceEngine {

    /**
     * 将事件列表展开为 [startDate, endDate] 闭区间内的所有 occurrence。
     * - 非重复事件：仅当 gregorianDate 在范围内时出现
     * - 重复事件：按 repeatType 规则展开
     *
     * @param excludeSkipDates 为 true 时排除 skipDates 中的日期（用于 UI 展示）；
     *                         为 false 时返回所有日期（用于调度器，skipDates 由 AlarmReceiver 运行时处理）
     */
    fun expandForRange(
        events: List<EventEntity>,
        startDate: String,
        endDate: String,
        excludeSkipDates: Boolean = true
    ): List<EventOccurrence> {
        if (startDate > endDate) return emptyList()
        val result = mutableListOf<EventOccurrence>()
        for (event in events) {
            if (event.repeatType == "none") {
                if (event.gregorianDate in startDate..endDate) {
                    result.add(EventOccurrence(event, event.gregorianDate))
                }
            } else {
                val skipSet = if (excludeSkipDates) {
                    event.skipDates.split(",")
                        .map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                } else emptySet()
                val dates = getOccurrenceDates(event, startDate, endDate)
                for (d in dates) {
                    if (d >= event.gregorianDate && d !in skipSet) {
                        result.add(EventOccurrence(event, d))
                    }
                }
            }
        }
        return result
    }

    /**
     * 展开单日的事件。
     */
    fun expandForDate(events: List<EventEntity>, date: String): List<EventOccurrence> {
        return expandForRange(events, date, date)
    }

    // ===== 内部：按 repeatType 计算日期列表 =====

    private fun getOccurrenceDates(
        event: EventEntity,
        rangeStart: String,
        rangeEnd: String
    ): List<String> {
        val anchor = event.gregorianDate
        // 范围完全在锚点之前 → 无出现
        if (rangeEnd < anchor) return emptyList()

        val effectiveStart = maxOf(rangeStart, anchor)
        val startCal = DateUtils.parseGregorian(effectiveStart)
        val endCal = DateUtils.parseGregorian(rangeEnd)

        return when (event.repeatType) {
            "daily" -> dailyDates(startCal, endCal)
            "weekly" -> weeklyDates(startCal, endCal, DateUtils.parseGregorian(anchor))
            "monthly" -> {
                if (event.useLunar) lunarMonthlyDates(event, startCal, endCal, anchor)
                else monthlyDates(startCal, endCal, DateUtils.parseGregorian(anchor))
            }
            "yearly" -> {
                if (event.useLunar) lunarYearlyDates(event, startCal, endCal, anchor)
                else yearlyDates(startCal, endCal, DateUtils.parseGregorian(anchor))
            }
            "workday" -> workdayDates(startCal, endCal, anchor)
            "weekend" -> weekendDates(startCal, endCal, anchor)
            else -> emptyList()
        }
    }

    // ---- daily ----

    private fun dailyDates(start: Calendar, end: Calendar): List<String> {
        val dates = mutableListOf<String>()
        val cal = start.clone() as Calendar
        while (cal <= end) {
            dates.add(DateUtils.formatGregorian(cal))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return dates
    }

    // ---- weekly ----

    private fun weeklyDates(start: Calendar, end: Calendar, anchor: Calendar): List<String> {
        val targetDayOfWeek = anchor.get(Calendar.DAY_OF_WEEK)
        val dates = mutableListOf<String>()
        val cal = start.clone() as Calendar
        // 对齐到目标星期几
        while (cal.get(Calendar.DAY_OF_WEEK) != targetDayOfWeek) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
            if (cal > end) return dates
        }
        while (cal <= end) {
            dates.add(DateUtils.formatGregorian(cal))
            cal.add(Calendar.WEEK_OF_YEAR, 1)
        }
        return dates
    }

    // ---- monthly (solar) ----

    private fun monthlyDates(start: Calendar, end: Calendar, anchor: Calendar): List<String> {
        val targetDay = anchor.get(Calendar.DAY_OF_MONTH)
        val dates = mutableListOf<String>()
        val cal = start.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, minOf(targetDay, cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
        // 如果调整后比 start 早，进到下个月
        if (cal < start) {
            cal.add(Calendar.MONTH, 1)
            cal.set(Calendar.DAY_OF_MONTH, minOf(targetDay, cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        while (cal <= end) {
            dates.add(DateUtils.formatGregorian(cal))
            cal.add(Calendar.MONTH, 1)
            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            if (cal.get(Calendar.DAY_OF_MONTH) < targetDay && targetDay <= maxDay) {
                cal.set(Calendar.DAY_OF_MONTH, targetDay)
            }
        }
        return dates
    }

    // ---- yearly (solar) ----

    private fun yearlyDates(start: Calendar, end: Calendar, anchor: Calendar): List<String> {
        val targetMonth = anchor.get(Calendar.MONTH)
        val targetDay = anchor.get(Calendar.DAY_OF_MONTH)
        val dates = mutableListOf<String>()
        val cal = start.clone() as Calendar
        cal.set(Calendar.MONTH, targetMonth)
        cal.set(Calendar.DAY_OF_MONTH, minOf(targetDay, cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
        if (cal < start) {
            cal.add(Calendar.YEAR, 1)
            cal.set(Calendar.MONTH, targetMonth)
            cal.set(Calendar.DAY_OF_MONTH, minOf(targetDay, cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        while (cal <= end) {
            dates.add(DateUtils.formatGregorian(cal))
            cal.add(Calendar.YEAR, 1)
            cal.set(Calendar.MONTH, targetMonth)
            cal.set(Calendar.DAY_OF_MONTH, minOf(targetDay, cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        return dates
    }

    // ---- lunar monthly ----

    private fun lunarMonthlyDates(
        event: EventEntity,
        start: Calendar,
        end: Calendar,
        anchorDateStr: String
    ): List<String> {
        val parts = LunarCalendar.toLunarParts(DateUtils.parseGregorian(anchorDateStr))
        val anchorMonth = parts.month
        val anchorDay = parts.day
        val isLeap = parts.isLeapMonth
        val anchorYear = parts.yearBase

        val dates = mutableListOf<String>()
        val startStr = DateUtils.formatGregorian(start)
        val endStr = DateUtils.formatGregorian(end)

        // 1. 从锚点向后扫描，找到第一个 >= startStr 的月份
        var year = anchorYear
        var month = anchorMonth
        var attempts = 0
        val maxBackward = 60
        while (attempts < maxBackward) {
            val greg = LunarCalendar.lunarToGregorian(year, month, anchorDay, isLeap)
            if (greg != null) {
                if (greg >= startStr) break
            }
            // 向后退一个月
            month--
            if (month < 1) { month = 12; year-- }
            attempts++
        }
        if (attempts >= maxBackward) {
            // 没找到，重置到锚点
            year = anchorYear; month = anchorMonth
        }

        // 2. 从当前位置向前扫描到 end
        attempts = 0
        while (attempts < 120) {
            val greg = LunarCalendar.lunarToGregorian(year, month, anchorDay, isLeap)
            if (greg != null) {
                if (greg in startStr..endStr) {
                    dates.add(greg)
                }
                if (greg > endStr) break
            }
            month++
            if (month > 12) { month = 1; year++ }
            attempts++
        }
        return dates
    }

    // ---- lunar yearly ----

    private fun lunarYearlyDates(
        event: EventEntity,
        start: Calendar,
        end: Calendar,
        anchorDateStr: String
    ): List<String> {
        val parts = LunarCalendar.toLunarParts(DateUtils.parseGregorian(anchorDateStr))
        val anchorMonth = parts.month
        val anchorDay = parts.day
        val isLeap = parts.isLeapMonth
        val anchorYear = parts.yearBase

        val dates = mutableListOf<String>()
        val startStr = DateUtils.formatGregorian(start)
        val endStr = DateUtils.formatGregorian(end)

        // 1. 从锚点向后扫描到 start
        var year = anchorYear
        var attempts = 0
        while (attempts < 30) {
            val greg = LunarCalendar.lunarToGregorian(year, anchorMonth, anchorDay, isLeap)
            if (greg != null && greg >= startStr) break
            year--
            attempts++
        }
        if (attempts >= 30) year = anchorYear

        // 2. 向前扫描到 end
        attempts = 0
        while (attempts < 60) {
            val greg = LunarCalendar.lunarToGregorian(year, anchorMonth, anchorDay, isLeap)
            if (greg != null) {
                if (greg in startStr..endStr) {
                    dates.add(greg)
                }
                if (greg > endStr) break
            }
            year++
            attempts++
        }
        return dates
    }

    // ---- workday ----

    private fun workdayDates(start: Calendar, end: Calendar, anchorDateStr: String): List<String> {
        val dates = mutableListOf<String>()
        val cal = start.clone() as Calendar
        while (cal <= end) {
            val dateStr = DateUtils.formatGregorian(cal)
            if (dateStr >= anchorDateStr && HolidayService.isWorkday(dateStr)) {
                dates.add(dateStr)
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return dates
    }

    // ---- weekend ----

    private fun weekendDates(start: Calendar, end: Calendar, anchorDateStr: String): List<String> {
        val dates = mutableListOf<String>()
        val cal = start.clone() as Calendar
        while (cal <= end) {
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) {
                val dateStr = DateUtils.formatGregorian(cal)
                if (dateStr >= anchorDateStr) {
                    dates.add(dateStr)
                }
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return dates
    }
}