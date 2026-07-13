package com.minirili.app.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DateUtils {

    private const val GREGORIAN_FORMAT = "yyyy-MM-dd"
    private val gregorianFormatter = SimpleDateFormat(GREGORIAN_FORMAT, Locale.CHINA)

    fun formatGregorian(date: Calendar): String = gregorianFormatter.format(date.time)

    fun parseGregorian(dateStr: String): Calendar {
        val cal = Calendar.getInstance()
        val parsed = gregorianFormatter.parse(dateStr)
        if (parsed != null) cal.time = parsed
        return cal
    }

    fun today(): String = formatGregorian(Calendar.getInstance())

    fun getDaysInMonth(year: Int, month: Int): Int {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun getDayOfWeek(date: String): String {
        val cal = parseGregorian(date)
        val days = arrayOf("日", "一", "二", "三", "四", "五", "六")
        return days[cal.get(Calendar.DAY_OF_WEEK) - 1]
    }

    fun isToday(date: String): Boolean = date == today()

    /**
     * 获取月份名称（中文）
     */
    fun getMonthName(month: Int): String {
        val months = arrayOf("", "1月", "2月", "3月", "4月", "5月", "6月",
            "7月", "8月", "9月", "10月", "11月", "12月")
        return months[month]
    }

    /**
     * 获取星期名称（中文，短格式）
     */
    fun getWeekdayShort(dayOfWeek: Int): String {
        val days = arrayOf("日", "一", "二", "三", "四", "五", "六")
        return days[dayOfWeek - 1]
    }

    /**
     * 获取星期名称（中文，完整格式）"星期日"/"星期一"...
     */
    fun getWeekdayFull(dayOfWeek: Int): String {
        val days = arrayOf("日", "一", "二", "三", "四", "五", "六")
        return "星期${days[dayOfWeek - 1]}"
    }
}
