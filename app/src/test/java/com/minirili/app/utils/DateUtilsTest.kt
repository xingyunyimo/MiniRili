package com.minirili.app.utils

import org.junit.Test
import org.junit.Assert.*
import java.util.Calendar

/**
 * DateUtils 单元测试
 */
class DateUtilsTest {

    @Test
    fun formatGregorian_formatsDateCorrectly() {
        val calendar = Calendar.getInstance().apply {
            set(2024, 1, 15) // 2024-02-15
        }
        val result = DateUtils.formatGregorian(calendar)
        assertEquals("2024-02-15", result)
    }

    @Test
    fun parseGregorian_parsesDateCorrectly() {
        val calendar = DateUtils.parseGregorian("2024-02-15")
        assertEquals(2024, calendar.get(Calendar.YEAR))
        assertEquals(1, calendar.get(Calendar.MONTH))
        assertEquals(15, calendar.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun today_returnsCurrentDate() {
        val today = DateUtils.today()
        val parts = today.split("-")
        assertTrue(parts.size == 3)
        assertTrue(parts[0].toIntOrNull() != null)
        assertTrue(parts[1].toIntOrNull() != null)
        assertTrue(parts[2].toIntOrNull() != null)
    }

    @Test
    fun getDaysInMonth_returnsCorrectDays() {
        assertEquals(31, DateUtils.getDaysInMonth(2024, 1)) // 1月31天
        assertEquals(29, DateUtils.getDaysInMonth(2024, 2)) // 2月29天（闰年）
        assertEquals(30, DateUtils.getDaysInMonth(2024, 4)) // 4月30天
        assertEquals(30, DateUtils.getDaysInMonth(2024, 6)) // 6月30天
    }

    @Test
    fun getDayOfWeek_returnsCorrectDay() {
        assertEquals("日", DateUtils.getDayOfWeek("2024-01-07"))
        assertEquals("一", DateUtils.getDayOfWeek("2024-01-08"))
        assertEquals("六", DateUtils.getDayOfWeek("2024-01-13"))
    }

    @Test
    fun isToday_returnsTrueForToday() {
        val today = DateUtils.today()
        assertTrue(DateUtils.isToday(today))
    }

    @Test
    fun isToday_returnsFalseForDifferentDate() {
        assertFalse(DateUtils.isToday("2023-01-01"))
    }

    @Test
    fun getMonthName_returnsCorrectName() {
        assertEquals("1月", DateUtils.getMonthName(1))
        assertEquals("12月", DateUtils.getMonthName(12))
        assertEquals("", DateUtils.getMonthName(0))
    }

    @Test
    fun getWeekdayShort_returnsCorrectDay() {
        assertEquals("日", DateUtils.getWeekdayShort(1))
        assertEquals("一", DateUtils.getWeekdayShort(2))
        assertEquals("六", DateUtils.getWeekdayShort(7))
    }
}
