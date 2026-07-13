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
}