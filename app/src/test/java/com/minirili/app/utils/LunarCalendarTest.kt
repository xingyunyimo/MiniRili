package com.minirili.app.utils

import org.junit.Test
import org.junit.Assert.*
import java.util.Calendar

class LunarCalendarTest {

    @Test
    fun getLunarDay_returnsCorrectDay() {
        // 2024-02-10 是 2024 年春节（正月初一）
        val calendar = Calendar.getInstance().apply {
            set(2024, 1, 10)
        }
        val lunarDay = LunarCalendar.getLunarDay(calendar)
        assertEquals("初一", lunarDay)
    }

    @Test
    fun getLunarDay_knownDate() {
        // 2024-02-15 是正月初六
        val calendar = Calendar.getInstance().apply {
            set(2024, 1, 15)
        }
        assertEquals("初六", LunarCalendar.getLunarDay(calendar))
    }

    @Test
    fun getLunarMonthDayName_knownDate() {
        // 2023-01-22 是 2023 年春节（正月初一）
        val calendar = Calendar.getInstance().apply {
            set(2023, 0, 22)
        }
        // 初一返回"正月"（纯月名，带 月）
        assertEquals("正月", LunarCalendar.getLunarMonthDayName(calendar))
    }

    @Test
    fun getLunarDayLabel_firstDayOfMonth() {
        val calendar = Calendar.getInstance().apply {
            set(2023, 0, 22) // 正月初一
        }
        assertEquals("正月", LunarCalendar.getLunarDayLabel(calendar))
    }

    @Test
    fun getGanZhiYear_returnsCorrectGanZhi() {
        val calendar = Calendar.getInstance().apply {
            set(2024, 1, 10)
        }
        val ganZhi = LunarCalendar.getGanZhiYear(calendar)
        assertTrue(ganZhi.contains("甲"))
        assertTrue(ganZhi.contains("辰"))
        assertTrue(ganZhi.endsWith("年"))
    }

    @Test
    fun getZodiacSign_returnsCorrectSign() {
        val dragonCalendar = Calendar.getInstance().apply { set(2024, 1, 8) }
        assertEquals("龙", LunarCalendar.getZodiacSign(dragonCalendar))

        val tigerCalendar = Calendar.getInstance().apply { set(2022, 1, 1) }
        assertEquals("虎", LunarCalendar.getZodiacSign(tigerCalendar))

        val rabbitCalendar = Calendar.getInstance().apply { set(2023, 1, 1) }
        assertEquals("兔", LunarCalendar.getZodiacSign(rabbitCalendar))
    }

    @Test
    fun getLunarMonthName_returnsCorrectName() {
        val calendar = Calendar.getInstance().apply {
            set(2024, 0, 1) // 2024年1月1日
        }
        val monthName = LunarCalendar.getLunarMonthName(calendar)
        assertNotNull(monthName)
        assertTrue(monthName.isNotEmpty())
    }

    @Test
    fun isSolarTerm_returnsCorrectResult() {
        val springCalendar = Calendar.getInstance().apply {
            set(2024, 2, 6) // 2024年3月6日 - 惊蛰
        }
        assertTrue(LunarCalendar.isSolarTerm(springCalendar))

        val summerCalendar = Calendar.getInstance().apply {
            set(2024, 5, 6) // 2024年6月6日 - 芒种
        }
        assertTrue(LunarCalendar.isSolarTerm(summerCalendar))
    }

    @Test
    fun getSolarTerm_returnsCorrectName() {
        val springCalendar = Calendar.getInstance().apply {
            set(2024, 2, 6) // 2024年3月6日 - 惊蛰
        }
        val solarTerm = LunarCalendar.getSolarTerm(springCalendar)
        assertEquals("惊蛰", solarTerm)

        val clearCalendar = Calendar.getInstance().apply {
            set(2024, 5, 6) // 2024年6月6日 - 芒种
        }
        val clearTerm = LunarCalendar.getSolarTerm(clearCalendar)
        assertEquals("芒种", clearTerm)
    }

    @Test
    fun getEightChar_returnsCorrectEightChar() {
        val calendar = Calendar.getInstance().apply {
            set(2024, 1, 10)
        }
        val eightChar = LunarCalendar.getEightChar(calendar)
        assertTrue(eightChar.yearGanZhi.contains("甲"))
        assertTrue(eightChar.yearGanZhi.contains("辰"))
    }

    @Test
    fun getWeekdayShort_returnsCorrectDay() {
        assertEquals("日", LunarCalendar.getWeekdayShort(1))
        assertEquals("一", LunarCalendar.getWeekdayShort(2))
        assertEquals("六", LunarCalendar.getWeekdayShort(7))
    }

    // ===== 回归：农历回算"差一天"（2014 七月天数曾误写 30→29）=====

    @Test
    fun lunarBacktrack_2014_August_isCorrect() {
        // 用户场景：设置农历 2014-08-20，回显应为 2014-09-13（八月二十）
        assertEquals("2014-09-13", LunarCalendar.lunarToGregorian(2014, 8, 20))
        assertEquals("初一", LunarCalendar.getLunarDay(cal(2014, 8, 25)))   // 八月初一
        assertEquals("二十", LunarCalendar.getLunarDay(cal(2014, 9, 13)))   // 八月二十
        assertEquals("廿一", LunarCalendar.getLunarDay(cal(2014, 9, 14)))   // 八月廿一
    }

    @Test
    fun lunarBacktrack_2014_aroundLeapMonth() {
        // 权威历法：10-23 九月三十，10-24 闰九月初一，11-22 十月初一
        assertEquals("闰九", LunarCalendar.getLunarMonthName(cal(2014, 10, 24)))
        assertEquals("三十", LunarCalendar.getLunarDay(cal(2014, 10, 23)))
    }

    @Test
    fun lunarBacktrack_2025_leapMonth() {
        // 权威历法：07-25 闰六月初一，08-23 七月初一
        assertEquals("初一", LunarCalendar.getLunarDay(cal(2025, 7, 25)))
        assertEquals("闰六", LunarCalendar.getLunarMonthName(cal(2025, 7, 25)))
        assertEquals("七", LunarCalendar.getLunarMonthName(cal(2025, 8, 23)))
    }

    @Test
    fun lunarBacktrack_2148_leapFirstMonth() {
        // 权威历法：2148 年闰正月，正月初一=01-21，闰正月初一=02-20
        assertEquals("正", LunarCalendar.getLunarMonthName(cal(2148, 1, 21)))
        assertEquals("闰正", LunarCalendar.getLunarMonthName(cal(2148, 2, 20)))
        assertEquals("正月", LunarCalendar.getLunarDayLabel(cal(2148, 1, 21)))   // 初一→月名+"月"
        assertEquals("闰正月", LunarCalendar.getLunarDayLabel(cal(2148, 2, 20))) // 闰月初一→"闰月名"+"月"
    }

    private fun cal(y: Int, m1: Int, d: Int) =
        Calendar.getInstance().apply { set(y, m1 - 1, d) }
}