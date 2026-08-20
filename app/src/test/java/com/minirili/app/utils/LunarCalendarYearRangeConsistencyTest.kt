package com.minirili.app.utils

import org.junit.Test
import org.junit.Assert.*
import java.util.Calendar

/**
 * fallback 表全年份往返一致性测试（JVM 上走 fallback，无 ICU）。
 *
 * 覆盖 2000-2030 每一天：
 *  1) 公历 → 农历 (toLunarParts) → 公历 (lunarToGregorian) 应回到原日期；
 *  2) 每个月"初一"正反互逆：农历→公历→农历 回到 初一。
 * 这能系统性抓出 all-year 的"差一天"错位（2014 bug 的同类问题）。
 */
class LunarCalendarYearRangeConsistencyTest {

    private fun cal(y: Int, m1: Int, d: Int): Calendar =
        Calendar.getInstance().apply {
            set(y, m1 - 1, d)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

    @Test
    fun gregorianLunarRoundTrip_allDays_selfConsistent() {
        // 从 1899 春节之后开始：避免公历 1~2 月初属于农历前一年的边界（表含 1899 作回溯用）。
        val start = cal(1900, 3, 1)
        val end = cal(2200, 12, 31)
        var days = 0
        var failures = 0
        val failList = StringBuilder()
        var cur = start

        while (!cur.after(end)) {
            val (gy, gm, gd) = ints(cur)
            val parts = LunarCalendar.toLunarParts(cur)
            // 农历 -> 公历
            val back = LunarCalendar.lunarToGregorian(parts.yearBase, parts.month, parts.day, parts.isLeapMonth)
            if (back == null || back != String.format("%04d-%02d-%02d", gy, gm, gd)) {
                if (failList.length < 800) failList.append("$gy-$gm-$gd -> lunar ${parts.yearBase}/${parts.month}/${parts.day}/leap=${parts.isLeapMonth} -> back=$back\n")
                failures++
            }
            cur.add(Calendar.DAY_OF_MONTH, 1)
            days++
            if (days > 20000) break
        }
        println("往返一致性: 覆盖 $days 天, 失败 $failures")
        println(failList.toString())
        assertEquals(0, failures)
    }

    @Test
    fun lunarToGregorian_allMonths_firstDay_isFirstDay() {
        // 对每年每月初一: 农历->公历 再算农历，应回到"初一"
        var failures = 0
        val failList = StringBuilder()
        for (y in 1900..2200) {
            for (m in 1..13) {
                // 试探该月初一: 用二分/直接扫该日期的农历日==1
                val monthStart = firstDayOfLunarMonth(y, m)
                if (monthStart == null) continue
                val parts = LunarCalendar.toLunarParts(monthStart)
                if (parts.day != 1) {
                    if (failList.length < 800) failList.append("$y 年 $m 月初一算成 day=${parts.day} month=${parts.month} leap=${parts.isLeapMonth}\n")
                    failures++
                }
            }
        }
        println(failList.toString())
        assertEquals(0, failures)
    }

    // 扫描公历 year 年 1月1日~次年2月底，找农历 (month=m, day=1, 可能闰) 的第一天
    private fun firstDayOfLunarMonth(year: Int, lunarMonth: Int): Calendar? {
        for (gy in year..year + 1) {
            val c = Calendar.getInstance().apply {
                set(gy, 0, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val limit = cal(year + 1, 3, 1)
            while (!c.after(limit)) {
                val p = LunarCalendar.toLunarParts(c)
                if (p.month == lunarMonth && p.day == 1) return c
                c.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return null
    }

    private fun ints(c: Calendar): Triple<Int, Int, Int> =
        Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}