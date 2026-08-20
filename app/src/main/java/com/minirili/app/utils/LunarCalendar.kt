package com.minirili.app.utils

import java.util.Calendar
import java.util.GregorianCalendar

/**
 * 农历计算工具
 *
 * 主路径：使用 android.icu.util.ChineseCalendar（Android 7.0+ 内置，覆盖 1 BCE ~ 2100 CE 以上）
 * 纯 JVM 单元测试环境（无 Android 运行时）回退至内置春节锚点表（1899-2201）。
 *
 * 支持：公历→农历月/日、干支、生肖、节气、八字
 */
object LunarCalendar {

    data class LunarMonthInfo(
        val month: Int,
        val leap: Boolean,
        val days: Int
    )

    data class EightChar(
        val yearGanZhi: String,
        val monthGanZhi: String,
        val dayGanZhi: String,
        val hourGanZhi: String
    )

    data class LunarParts(
        val yearBase: Int,
        val month: Int,        // 1-based
        val day: Int,          // 1-based
        val isLeapMonth: Boolean
    )

    private val earthlyBranches = arrayOf(
        "子", "丑", "寅", "卯", "辰", "巳",
        "午", "未", "申", "酉", "戌", "亥"
    )

    private val heavenlyStems = arrayOf(
        "甲", "乙", "丙", "丁", "戊", "己",
        "庚", "辛", "壬", "癸"
    )

    private val zodiacSigns = arrayOf(
        "鼠", "牛", "虎", "兔", "龙", "蛇",
        "马", "羊", "猴", "鸡", "狗", "猪"
    )

    private val stemArr = heavenlyStems

    private val solarTermData = arrayOf(
        intArrayOf(1, 5), intArrayOf(1, 20),
        intArrayOf(2, 4), intArrayOf(2, 19),
        intArrayOf(3, 6), intArrayOf(3, 21),
        intArrayOf(4, 5), intArrayOf(4, 20),
        intArrayOf(5, 6), intArrayOf(5, 21),
        intArrayOf(6, 6), intArrayOf(6, 21),
        intArrayOf(7, 7), intArrayOf(7, 23),
        intArrayOf(8, 8), intArrayOf(8, 23),
        intArrayOf(9, 8), intArrayOf(9, 23),
        intArrayOf(10, 8), intArrayOf(10, 23),
        intArrayOf(11, 7), intArrayOf(11, 22),
        intArrayOf(12, 7), intArrayOf(12, 22)
    )

    private val solarTermNames = arrayOf(
        "小寒", "大寒", "立春", "雨水", "惊蛰", "春分",
        "清明", "谷雨", "立夏", "小满", "芒种", "夏至",
        "小暑", "大暑", "立秋", "处暑", "白露", "秋分",
        "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"
    )

    private val lunarMonthNames = arrayOf(
        "正", "二", "三", "四", "五", "六",
        "七", "八", "九", "十", "冬", "腊"
    )

    private val lunarDayNames = arrayOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    )

    /**
     * 公历 → 农历（月 / 日 / 是否闰月）
     * 主路径：android.icu.util.ChineseCalendar
     */
    fun toLunarParts(gregorian: Calendar): LunarParts {
        // 尝试 ICU，失败后 fall back 到本地锚点表
        val fromIcu = kotlin.runCatching { toLunarPartsIcu(gregorian) }.getOrNull()
        if (fromIcu != null) return fromIcu
        return toLunarPartsFallback(gregorian)
    }

    private fun toLunarPartsIcu(gregorian: Calendar): LunarParts {
        // Use Java's built-in Calendar library with Chinese calendar - ICU4J is available
        // via android.icu.util since Android API 24. We load via reflection so JVM unit
        // tests (no Android runtime) don't crash.
        val cls = Class.forName("android.icu.util.ChineseCalendar")
        // 注意：不能用 getConstructor(int,int,int) 构造——android.icu / ICU4J 的
        // ChineseCalendar 没有该公开构造器，会抛 NoSuchMethodException 被上层
        // runCatching 吞掉，导致真机公历→农历始终落进 fallback 数据表（进而触发
        // 差一天问题）。这里与 lunarToGregorianIcu 保持一致：无参构造 + setTimeInMillis。
        val cc = cls.getDeclaredConstructor().newInstance()
        cls.getMethod("setTimeInMillis", java.lang.Long.TYPE).invoke(cc, gregorian.timeInMillis)
        val monthField = cls.getField("MONTH").get(null) as Int
        val dayField = cls.getField("DAY_OF_MONTH").get(null) as Int
        val extendedYearField = cls.getField("EXTENDED_YEAR").get(null) as Int
        val isLeapField = cls.getField("IS_LEAP_MONTH").get(null) as Int

        val month0 = cls.getMethod("get", Integer.TYPE).invoke(cc, monthField) as Int
        val day = cls.getMethod("get", Integer.TYPE).invoke(cc, dayField) as Int
        val extYear = cls.getMethod("get", Integer.TYPE).invoke(cc, extendedYearField) as Int
        val isLeapInt = cls.getMethod("get", Integer.TYPE).invoke(cc, isLeapField) as Int

        return LunarParts(
            yearBase = extYear - 2637,
            month = month0 + 1,
            day = day,
            isLeapMonth = isLeapInt == 1
        )
    }

    /** 本地表兜底 — 覆盖 1899-2201（JVM 单元测试 vs 少数无 ICU 的老设备） */
    private fun toLunarPartsFallback(gregorian: Calendar): LunarParts {
        val year = gregorian.get(Calendar.YEAR)
        val month = gregorian.get(Calendar.MONTH) + 1
        val day = gregorian.get(Calendar.DAY_OF_MONTH)

        val yd = FALLBACK_YEAR_DATA.firstOrNull { it.year == year } ?: return LunarParts(year, month, day, false)
        val springDate = Calendar.getInstance().apply {
            clear(); set(year, yd.springMonth - 1, yd.springDay)
        }
        val today = Calendar.getInstance().apply {
            clear(); set(year, month - 1, day)
        }
        if (today.before(springDate)) {
            // 用上一年定义（农历年 = 公历年 - 1）
            val prev = FALLBACK_YEAR_DATA.firstOrNull { it.year == year - 1 }
                ?: return LunarParts(year - 1, month, day, false)
            val prevSpring = Calendar.getInstance().apply {
                clear(); set(prev.year, prev.springMonth - 1, prev.springDay)
            }
            val todayForPrev = Calendar.getInstance().apply {
                clear(); set(year, month - 1, day)
            }
            val offsetDays = ((todayForPrev.timeInMillis - prevSpring.timeInMillis + 43200_000L) / 86_400_000L).toInt()
            return walkMonthDays(prev, offsetDays, year - 1)
        }
        val offsetDays = ((today.timeInMillis - springDate.timeInMillis + 43200_000L) / 86_400_000L).toInt()
        return walkMonthDays(yd, offsetDays, year)
    }

    private fun walkMonthDays(yd: FallbackYearData, offset: Int, gregorianYear: Int): LunarParts {
        var rem = offset
        val months = expandMonthDays(yd)
        for ((idx, m) in months.withIndex()) {
            if (rem < m.days) {
                return LunarParts(gregorianYear, m.monthIndex, rem + 1, m.isLeap)
            }
            rem -= m.days
        }
        return LunarParts(gregorianYear, 12, rem.coerceIn(1, 30), false)
    }

    private data class ExpandedMonth(val monthIndex: Int, val days: Int, val isLeap: Boolean)

    private fun expandMonthDays(yd: FallbackYearData): List<ExpandedMonth> {
        // yd.monthDays: 12 或 13 个整数，严格按真实时序（正月、二月...腊月），
        // 闰月紧随被闰的普通月之后。13 项 == 存在闰月，且索引在 [leapMonth] 处是闰月。
        // 例：leapMonth=6 → [正..六月, 闰六月, 七月..十二月]。
        val result = mutableListOf<ExpandedMonth>()
        if (yd.leapMonth > 0 && yd.monthDays.size == 13) {
            for (i in 0..yd.monthDays.lastIndex) {
                val isLeap = i == yd.leapMonth
                val monthIndex = if (i >= yd.leapMonth) i else i + 1
                result.add(ExpandedMonth(monthIndex, yd.monthDays[i], isLeap))
            }
        } else {
            for (i in yd.monthDays.indices) {
                result.add(ExpandedMonth(i + 1, yd.monthDays[i], false))
            }
        }
        return result
    }

    private data class FallbackYearData(
        val year: Int,
        val leapMonth: Int,
        val springMonth: Int,
        val springDay: Int,
        val monthDays: List<Int>
    )

    private val FALLBACK_YEAR_DATA: List<FallbackYearData> = buildList {
        // 1899-2201 全部年份由 ICU（android.icu.util.ChineseCalendar）权威历法生成，
        // 含闰月（leapMonth>0 时13项）。额外包含 1899 / 2031-2201，使春节前的
        // 日期可回溯到上一农历年查表。
        // 历史 bug：2014 年七月天数曾误写 30→29、闰九月漏记，已由权威数据替换修正。
                add(1899, 0, 2, 10, listOf(30,29,30,29,30,29,30,30,29,30,29,30))
        add(1900, 8, 1, 31, listOf(29,30,29,29,30,29,30,30,29,30,30,29,30))
        add(1901, 0, 2, 19, listOf(29,30,29,29,30,29,30,29,30,30,30,29))
        add(1902, 0, 2, 8, listOf(30,29,30,29,29,30,29,30,29,30,30,30))
        add(1903, 5, 1, 29, listOf(29,30,29,30,29,29,30,29,29,30,30,29,30))
        add(1904, 0, 2, 16, listOf(30,30,29,30,29,29,30,29,29,30,30,29))
        add(1905, 0, 2, 4, listOf(30,30,29,30,30,29,29,30,29,30,29,30))
        add(1906, 4, 1, 25, listOf(29,30,30,29,30,29,30,29,30,29,30,29,30))
        add(1907, 0, 2, 13, listOf(29,30,29,30,29,30,30,29,30,29,30,29))
        add(1908, 0, 2, 2, listOf(30,29,29,30,30,29,30,29,30,30,29,30))
        add(1909, 2, 1, 22, listOf(29,30,29,29,30,29,30,29,30,30,30,29,30))
        add(1910, 0, 2, 10, listOf(29,30,29,29,30,29,30,29,30,30,30,29))
        add(1911, 6, 1, 30, listOf(30,29,30,29,29,30,29,29,30,30,29,30,30))
        add(1912, 0, 2, 18, listOf(30,29,30,29,29,30,29,29,30,30,29,30))
        add(1913, 0, 2, 6, listOf(30,30,29,30,29,29,30,29,29,30,29,30))
        add(1914, 5, 1, 26, listOf(30,30,29,30,29,30,29,30,29,29,30,29,30))
        add(1915, 0, 2, 14, listOf(30,29,30,30,29,30,29,30,29,30,29,29))
        add(1916, 0, 2, 3, listOf(30,30,29,30,29,30,30,29,30,29,30,29))
        add(1917, 3, 1, 23, listOf(30,29,29,30,29,30,30,29,30,30,29,30,29))
        add(1918, 0, 2, 11, listOf(30,29,29,30,29,30,29,30,30,29,30,30))
        add(1919, 7, 2, 1, listOf(29,30,29,29,30,29,29,30,30,29,30,30,30))
        add(1920, 0, 2, 20, listOf(29,30,29,29,30,29,29,30,29,30,30,30))
        add(1921, 0, 2, 8, listOf(30,29,30,29,29,30,29,29,30,29,30,30))
        add(1922, 6, 1, 28, listOf(30,29,30,30,29,29,30,29,29,30,29,30,30))
        add(1923, 0, 2, 16, listOf(29,30,30,29,30,29,30,29,29,30,29,30))
        add(1924, 0, 2, 5, listOf(29,30,30,29,30,30,29,30,29,30,29,29))
        add(1925, 4, 1, 24, listOf(30,29,30,29,30,30,29,30,30,29,30,29,30))
        add(1926, 0, 2, 13, listOf(29,29,30,29,30,29,30,30,29,30,30,29))
        add(1927, 0, 2, 2, listOf(30,29,29,30,29,30,29,30,29,30,30,30))
        add(1928, 2, 1, 23, listOf(29,30,29,29,30,29,29,30,29,30,30,30,30))
        add(1929, 0, 2, 10, listOf(29,30,29,29,30,29,29,30,29,30,30,30))
        add(1930, 6, 1, 30, listOf(29,30,30,29,29,30,29,29,30,29,30,30,29))
        add(1931, 0, 2, 17, listOf(30,30,29,30,29,30,29,29,30,29,30,29))
        add(1932, 0, 2, 6, listOf(30,30,30,29,30,29,30,29,29,30,29,30))
        add(1933, 5, 1, 26, listOf(29,30,30,29,30,30,29,30,29,30,29,29,30))
        add(1934, 0, 2, 14, listOf(29,30,29,30,30,29,30,29,30,30,29,30))
        add(1935, 0, 2, 4, listOf(29,29,30,29,30,29,30,30,29,30,30,29))
        add(1936, 3, 1, 24, listOf(30,29,29,30,29,29,30,30,29,30,30,30,29))
        add(1937, 0, 2, 11, listOf(30,29,29,30,29,29,30,29,30,30,30,29))
        add(1938, 7, 1, 31, listOf(30,30,29,29,30,29,29,30,29,30,30,29,30))
        add(1939, 0, 2, 19, listOf(30,30,29,29,30,29,29,30,29,30,29,30))
        add(1940, 0, 2, 8, listOf(30,30,29,30,29,30,29,29,30,29,30,29))
        add(1941, 6, 1, 27, listOf(30,30,29,30,30,29,30,29,29,30,29,30,29))
        add(1942, 0, 2, 15, listOf(30,29,30,30,29,30,29,30,29,30,29,30))
        add(1943, 0, 2, 5, listOf(29,30,29,30,29,30,30,29,30,29,30,29))
        add(1944, 4, 1, 25, listOf(30,29,30,29,30,29,30,29,30,30,29,30,30))
        add(1945, 0, 2, 13, listOf(29,29,30,29,29,30,29,30,30,30,29,30))
        add(1946, 0, 2, 2, listOf(30,29,29,30,29,29,30,29,30,30,29,30))
        add(1947, 2, 1, 22, listOf(30,30,29,29,30,29,29,30,29,30,29,30,30))
        add(1948, 0, 2, 10, listOf(30,29,30,29,30,29,29,30,29,30,29,30))
        add(1949, 7, 1, 29, listOf(30,29,30,30,29,30,29,29,30,29,30,29,30))
        add(1950, 0, 2, 17, listOf(29,30,30,29,30,30,29,29,30,29,30,29))
        add(1951, 0, 2, 6, listOf(30,29,30,30,29,30,29,30,29,30,29,30))
        add(1952, 5, 1, 27, listOf(29,30,29,30,29,30,29,30,30,29,30,29,30))
        add(1953, 0, 2, 14, listOf(29,30,29,29,30,30,29,30,30,29,30,30))
        add(1954, 0, 2, 4, listOf(29,29,30,29,29,30,29,30,30,29,30,30))
        add(1955, 3, 1, 24, listOf(30,29,29,30,29,29,30,29,30,29,30,30,30))
        add(1956, 0, 2, 12, listOf(29,30,29,30,29,29,30,29,30,29,30,30))
        add(1957, 8, 1, 31, listOf(30,29,30,29,30,29,29,30,29,30,29,30,29))
        add(1958, 0, 2, 18, listOf(30,30,30,29,30,29,29,30,29,30,29,30))
        add(1959, 0, 2, 8, listOf(29,30,30,29,30,29,30,29,30,29,30,29))
        add(1960, 6, 1, 28, listOf(30,29,30,29,30,30,29,30,29,30,29,30,29))
        add(1961, 0, 2, 15, listOf(30,29,30,29,30,29,30,30,29,30,29,30))
        add(1962, 0, 2, 5, listOf(29,30,29,29,30,29,30,30,29,30,30,29))
        add(1963, 4, 1, 25, listOf(30,29,30,29,29,30,29,30,29,30,30,30,29))
        add(1964, 0, 2, 13, listOf(30,29,30,29,29,30,29,30,29,30,30,30))
        add(1965, 0, 2, 2, listOf(29,30,29,30,29,29,30,29,29,30,30,29))
        add(1966, 3, 1, 21, listOf(30,30,30,29,30,29,29,30,29,29,30,30,29))
        add(1967, 0, 2, 9, listOf(30,30,29,30,30,29,29,30,29,30,29,30))
        add(1968, 7, 1, 30, listOf(29,30,29,30,30,29,30,29,30,29,30,29,30))
        add(1969, 0, 2, 17, listOf(29,30,29,30,29,30,30,29,30,29,30,29))
        add(1970, 0, 2, 6, listOf(30,29,29,30,29,30,30,29,30,30,29,30))
        add(1971, 5, 1, 27, listOf(29,30,29,29,30,29,30,29,30,30,30,29,30))
        add(1972, 0, 2, 15, listOf(29,30,29,29,30,29,30,29,30,30,29,30))
        add(1973, 0, 2, 3, listOf(30,29,30,29,29,30,29,29,30,30,29,30))
        add(1974, 4, 1, 23, listOf(30,30,29,30,29,29,30,29,29,30,30,29,30))
        add(1975, 0, 2, 11, listOf(30,30,29,30,29,29,30,29,29,30,29,30))
        add(1976, 8, 1, 31, listOf(30,30,29,30,29,30,29,30,29,29,30,29,30))
        add(1977, 0, 2, 18, listOf(30,29,30,30,29,30,29,30,29,30,29,29))
        add(1978, 0, 2, 7, listOf(30,29,30,30,29,30,30,29,30,29,30,29))
        add(1979, 6, 1, 28, listOf(30,29,29,30,29,30,30,29,30,30,29,30,29))
        add(1980, 0, 2, 16, listOf(30,29,29,30,29,30,29,30,30,29,30,30))
        add(1981, 0, 2, 5, listOf(29,30,29,29,30,29,29,30,30,29,30,30))
        add(1982, 4, 1, 25, listOf(30,29,30,29,29,30,29,29,30,29,30,30,30))
        add(1983, 0, 2, 13, listOf(30,29,30,29,29,30,29,29,30,29,30,30))
        add(1984, 10, 2, 2, listOf(30,29,30,30,29,29,30,29,29,30,29,30,30))
        add(1985, 0, 2, 20, listOf(29,30,30,29,30,29,30,29,29,30,29,30))
        add(1986, 0, 2, 9, listOf(29,30,30,29,30,30,29,30,29,30,29,29))
        add(1987, 7, 1, 29, listOf(30,29,30,29,30,30,29,30,30,29,30,29,29))
        add(1988, 0, 2, 17, listOf(30,29,30,29,30,29,30,30,29,30,30,29))
        add(1989, 0, 2, 6, listOf(30,29,29,30,29,30,29,30,29,30,30,30))
        add(1990, 5, 1, 27, listOf(29,30,29,29,30,29,29,30,29,30,30,30,30))
        add(1991, 0, 2, 15, listOf(29,30,29,29,30,29,29,30,29,30,30,30))
        add(1992, 0, 2, 4, listOf(29,30,30,29,29,30,29,29,30,29,30,30))
        add(1993, 3, 1, 23, listOf(29,30,30,29,30,29,30,29,29,30,29,30,29))
        add(1994, 0, 2, 10, listOf(30,30,30,29,30,29,30,29,29,30,29,30))
        add(1995, 8, 1, 31, listOf(29,30,30,29,30,29,30,30,29,29,30,29,30))
        add(1996, 0, 2, 19, listOf(29,30,29,30,30,29,30,29,30,30,29,29))
        add(1997, 0, 2, 7, listOf(30,29,30,29,30,29,30,30,29,30,30,29))
        add(1998, 5, 1, 28, listOf(30,29,29,30,29,29,30,30,29,30,30,30,29))
        add(1999, 0, 2, 16, listOf(30,29,29,30,29,29,30,29,30,30,30,29))
        add(2000, 0, 2, 5, listOf(30,30,29,29,30,29,29,30,29,30,30,29))
        add(2001, 4, 1, 24, listOf(30,30,29,30,29,30,29,29,30,29,30,29,30))
        add(2002, 0, 2, 12, listOf(30,30,29,30,29,30,29,29,30,29,30,29))
        add(2003, 0, 2, 1, listOf(30,30,29,30,30,29,30,29,29,30,29,30))
        add(2004, 2, 1, 22, listOf(29,30,29,30,30,29,30,29,30,29,30,29,30))
        add(2005, 0, 2, 9, listOf(29,30,29,30,29,30,30,29,30,29,30,29))
        add(2006, 7, 1, 29, listOf(30,29,30,29,30,29,30,29,30,30,29,30,30))
        add(2007, 0, 2, 18, listOf(29,29,30,29,29,30,29,30,30,30,29,30))
        add(2008, 0, 2, 7, listOf(30,29,29,30,29,29,30,29,30,30,29,30))
        add(2009, 5, 1, 26, listOf(30,30,29,29,30,29,29,30,29,30,29,30,30))
        add(2010, 0, 2, 14, listOf(30,29,30,29,30,29,29,30,29,30,29,30))
        add(2011, 0, 2, 3, listOf(30,29,30,30,29,30,29,29,30,29,30,29))
        add(2012, 4, 1, 23, listOf(30,29,30,30,29,30,30,29,29,30,29,30,29))
        add(2013, 0, 2, 10, listOf(30,29,30,29,30,30,29,30,29,30,29,30))
        add(2014, 9, 1, 31, listOf(29,30,29,30,29,30,29,30,30,29,30,29,30))
        add(2015, 0, 2, 19, listOf(29,30,29,29,30,29,30,30,30,29,30,29))
        add(2016, 0, 2, 8, listOf(30,29,30,29,29,30,29,30,30,29,30,30))
        add(2017, 6, 1, 28, listOf(29,30,29,30,29,29,30,29,30,29,30,30,30))
        add(2018, 0, 2, 16, listOf(29,30,29,30,29,29,30,29,29,30,30,30))
        add(2019, 0, 2, 5, listOf(30,29,30,29,30,29,29,30,29,29,30,30))
        add(2020, 4, 1, 25, listOf(29,30,30,30,29,30,29,29,30,29,30,29,30))
        add(2021, 0, 2, 12, listOf(29,30,30,29,30,29,30,29,30,29,30,29))
        add(2022, 0, 2, 1, listOf(30,29,30,29,30,30,29,30,29,30,29,30))
        add(2023, 2, 1, 22, listOf(29,30,29,29,30,30,29,30,30,29,30,29,30))
        add(2024, 0, 2, 10, listOf(29,30,29,29,30,29,30,30,29,30,30,29))
        add(2025, 6, 1, 29, listOf(30,29,30,29,29,30,29,30,29,30,30,30,29))
        add(2026, 0, 2, 17, listOf(30,29,30,29,29,30,29,29,30,30,30,30))
        add(2027, 0, 2, 7, listOf(29,30,29,30,29,29,30,29,29,30,30,29))
        add(2028, 5, 1, 26, listOf(30,30,30,29,30,29,29,30,29,29,30,30,29))
        add(2029, 0, 2, 13, listOf(30,30,29,30,29,30,29,30,29,29,30,29))
        add(2030, 0, 2, 2, listOf(30,30,29,30,30,29,30,29,30,29,30,29))
        add(2031, 3, 1, 23, listOf(29,30,30,29,30,29,30,30,29,30,29,30,29))
        add(2032, 0, 2, 11, listOf(30,29,29,30,29,30,30,29,30,30,29,30))
        add(2033, 11, 1, 31, listOf(29,30,29,29,30,29,30,29,30,30,30,29,30))
        add(2034, 0, 2, 19, listOf(29,30,29,29,30,29,30,29,30,30,29,30))
        add(2035, 0, 2, 8, listOf(30,29,30,29,29,30,29,29,30,30,29,30))
        add(2036, 6, 1, 28, listOf(30,30,29,30,29,29,30,29,29,30,29,30,30))
        add(2037, 0, 2, 15, listOf(30,30,29,30,29,29,30,29,29,30,29,30))
        add(2038, 0, 2, 4, listOf(30,30,29,30,29,30,29,30,29,29,30,29))
        add(2039, 5, 1, 24, listOf(30,30,29,30,30,29,30,29,30,29,30,29,29))
        add(2040, 0, 2, 12, listOf(30,29,30,30,29,30,29,30,30,29,30,29))
        add(2041, 0, 2, 1, listOf(29,30,29,30,29,30,30,29,30,30,29,30))
        add(2042, 2, 1, 22, listOf(29,30,29,29,30,29,30,29,30,30,29,30,30))
        add(2043, 0, 2, 10, listOf(29,30,29,29,30,29,29,30,30,29,30,30))
        add(2044, 7, 1, 30, listOf(30,29,30,29,29,30,29,29,30,29,30,30,30))
        add(2045, 0, 2, 17, listOf(30,29,30,29,29,30,29,29,30,29,30,30))
        add(2046, 0, 2, 6, listOf(30,29,30,29,30,29,30,29,29,30,29,30))
        add(2047, 5, 1, 26, listOf(30,29,30,30,29,30,29,30,29,29,30,29,30))
        add(2048, 0, 2, 14, listOf(29,30,30,29,30,30,29,30,29,29,30,29))
        add(2049, 0, 2, 2, listOf(30,29,30,29,30,30,29,30,30,29,30,29))
        add(2050, 3, 1, 23, listOf(29,30,29,30,29,30,29,30,30,29,30,30,29))
        add(2051, 0, 2, 11, listOf(30,29,29,30,29,29,30,30,29,30,30,30))
        add(2052, 8, 2, 1, listOf(29,30,29,29,30,29,29,30,29,30,30,30,30))
        add(2053, 0, 2, 19, listOf(29,30,29,29,30,29,29,30,29,30,30,30))
        add(2054, 0, 2, 8, listOf(29,30,30,29,29,30,29,29,30,29,30,30))
        add(2055, 6, 1, 28, listOf(29,30,30,29,30,29,30,29,29,30,29,30,29))
        add(2056, 0, 2, 15, listOf(30,30,30,29,30,29,30,29,29,30,29,30))
        add(2057, 0, 2, 4, listOf(29,30,30,29,30,29,30,30,29,29,30,29))
        add(2058, 4, 1, 24, listOf(30,29,30,29,30,29,30,30,29,30,30,29,29))
        add(2059, 0, 2, 12, listOf(30,29,30,29,30,29,30,29,30,30,30,29))
        add(2060, 0, 2, 2, listOf(30,29,29,30,29,29,30,29,30,30,30,29))
        add(2061, 3, 1, 21, listOf(30,30,29,29,30,29,29,30,29,30,30,30,29))
        add(2062, 0, 2, 9, listOf(30,30,29,29,30,29,29,30,29,30,30,29))
        add(2063, 7, 1, 29, listOf(30,30,29,30,29,30,29,29,30,29,30,29,30))
        add(2064, 0, 2, 17, listOf(30,30,29,30,29,30,29,29,30,29,30,29))
        add(2065, 0, 2, 5, listOf(30,30,29,30,30,29,30,29,29,30,29,30))
        add(2066, 5, 1, 26, listOf(29,30,29,30,30,29,30,29,30,29,30,29,30))
        add(2067, 0, 2, 14, listOf(29,30,29,30,29,30,30,29,30,29,30,29))
        add(2068, 0, 2, 3, listOf(30,29,30,29,29,30,30,29,30,30,29,30))
        add(2069, 4, 1, 23, listOf(29,30,29,30,29,29,30,29,30,30,30,29,30))
        add(2070, 0, 2, 11, listOf(30,29,29,30,29,29,30,29,30,30,29,30))
        add(2071, 8, 1, 31, listOf(30,29,30,29,30,29,29,30,29,30,29,30,30))
        add(2072, 0, 2, 19, listOf(30,29,30,29,30,29,29,30,29,30,29,30))
        add(2073, 0, 2, 7, listOf(30,29,30,30,29,30,29,29,30,29,30,29))
        add(2074, 6, 1, 27, listOf(30,29,30,30,29,30,29,30,29,30,29,30,29))
        add(2075, 0, 2, 15, listOf(30,29,30,29,30,30,29,30,29,30,29,30))
        add(2076, 0, 2, 5, listOf(29,30,29,30,29,30,29,30,30,29,30,29))
        add(2077, 4, 1, 24, listOf(30,29,30,29,29,30,29,30,30,30,29,30,29))
        add(2078, 0, 2, 12, listOf(30,29,30,29,29,30,29,30,30,29,30,30))
        add(2079, 0, 2, 2, listOf(29,30,29,30,29,29,30,29,30,29,30,30))
        add(2080, 3, 1, 22, listOf(30,29,30,29,30,29,29,30,29,29,30,30,30))
        add(2081, 0, 2, 9, listOf(29,30,30,29,30,29,29,30,29,29,30,30))
        add(2082, 7, 1, 29, listOf(29,30,30,30,29,29,30,29,30,29,29,30,30))
        add(2083, 0, 2, 17, listOf(29,30,30,29,30,29,30,29,30,29,30,29))
        add(2084, 0, 2, 6, listOf(30,29,30,29,30,30,29,30,29,30,29,30))
        add(2085, 5, 1, 26, listOf(29,30,29,29,30,30,29,30,30,29,30,29,30))
        add(2086, 0, 2, 14, listOf(29,30,29,29,30,29,30,30,29,30,30,29))
        add(2087, 0, 2, 3, listOf(30,29,30,29,29,30,29,30,29,30,30,30))
        add(2088, 4, 1, 24, listOf(29,30,29,30,29,29,30,29,29,30,30,30,29))
        add(2089, 0, 2, 10, listOf(30,30,29,30,29,29,29,30,29,30,30,29))
        add(2090, 8, 1, 30, listOf(30,30,30,29,30,29,29,30,29,29,30,30,29))
        add(2091, 0, 2, 18, listOf(30,30,29,30,29,30,29,30,29,29,30,29))
        add(2092, 0, 2, 7, listOf(30,30,29,30,30,29,30,29,30,29,30,29))
        add(2093, 6, 1, 27, listOf(29,30,30,29,30,29,30,30,29,30,29,30,29))
        add(2094, 0, 2, 15, listOf(29,30,29,30,29,30,30,29,30,30,29,30))
        add(2095, 0, 2, 5, listOf(29,30,29,29,30,29,30,29,30,30,30,29))
        add(2096, 4, 1, 25, listOf(30,29,30,29,29,30,29,29,30,30,30,29,30))
        add(2097, 0, 2, 12, listOf(30,29,30,29,29,29,30,29,30,30,29,30))
        add(2098, 0, 2, 1, listOf(30,30,29,30,29,29,29,30,29,30,29,30))
        add(2099, 2, 1, 21, listOf(30,30,29,30,30,29,29,30,29,29,30,29,30))
        add(2100, 0, 2, 9, listOf(30,30,29,30,29,30,29,30,29,29,30,29))
        add(2101, 7, 1, 29, listOf(30,30,29,30,29,30,30,29,30,29,29,30,29))
        add(2102, 0, 2, 17, listOf(30,29,30,30,29,30,29,30,30,29,30,29))
        add(2103, 0, 2, 7, listOf(29,30,29,30,29,30,29,30,30,30,29,30))
        add(2104, 5, 1, 28, listOf(29,30,29,29,30,29,30,29,30,30,29,30,30))
        add(2105, 0, 2, 15, listOf(29,30,29,29,30,29,29,30,30,29,30,30))
        add(2106, 0, 2, 4, listOf(30,29,30,29,29,30,29,29,30,29,30,30))
        add(2107, 4, 1, 24, listOf(30,30,29,30,29,29,30,29,29,30,29,30,30))
        add(2108, 0, 2, 12, listOf(30,29,30,29,30,29,30,29,29,30,29,30))
        add(2109, 9, 1, 31, listOf(30,29,30,30,29,30,29,30,29,29,30,29,30))
        add(2110, 0, 2, 19, listOf(29,30,30,29,30,30,29,30,29,29,30,29))
        add(2111, 0, 2, 8, listOf(30,29,30,29,30,30,29,30,29,30,30,29))
        add(2112, 6, 1, 29, listOf(29,30,29,30,29,30,29,30,30,29,30,30,29))
        add(2113, 0, 2, 16, listOf(30,29,29,30,29,29,30,30,29,30,30,30))
        add(2114, 0, 2, 6, listOf(29,30,29,29,30,29,29,30,29,30,30,30))
        add(2115, 4, 1, 26, listOf(30,29,30,29,29,30,29,29,30,29,30,30,30))
        add(2116, 0, 2, 14, listOf(29,30,29,30,29,30,29,29,30,29,30,30))
        add(2117, 0, 2, 2, listOf(29,30,30,29,30,29,30,29,29,30,29,30))
        add(2118, 3, 1, 22, listOf(29,30,30,29,30,30,29,30,29,29,30,29,30))
        add(2119, 0, 2, 10, listOf(29,30,29,30,30,29,30,29,30,29,30,29))
        add(2120, 7, 1, 30, listOf(30,29,30,29,30,29,30,30,29,30,29,30,29))
        add(2121, 0, 2, 17, listOf(30,29,30,29,30,29,30,29,30,30,30,29))
        add(2122, 0, 2, 7, listOf(29,30,29,30,29,29,30,29,30,30,30,29))
        add(2123, 5, 1, 27, listOf(30,30,29,29,30,29,29,30,29,30,30,30,29))
        add(2124, 0, 2, 15, listOf(30,29,30,29,30,29,29,30,29,30,30,29))
        add(2125, 0, 2, 3, listOf(30,30,29,30,29,30,29,29,30,29,30,29))
        add(2126, 4, 1, 23, listOf(30,30,30,29,30,29,30,29,29,30,29,30,29))
        add(2127, 0, 2, 11, listOf(30,30,29,30,30,29,29,30,29,30,29,30))
        add(2128, 11, 2, 1, listOf(29,30,29,30,30,29,30,29,30,29,30,29,30))
        add(2129, 0, 2, 19, listOf(29,30,29,30,29,30,30,29,30,29,30,29))
        add(2130, 0, 2, 8, listOf(30,29,30,29,29,30,30,29,30,30,29,30))
        add(2131, 6, 1, 29, listOf(29,30,29,30,29,29,30,29,30,30,30,29,30))
        add(2132, 0, 2, 17, listOf(29,30,29,30,29,29,30,29,30,30,29,30))
        add(2133, 0, 2, 5, listOf(30,29,30,29,30,29,29,29,30,30,29,30))
        add(2134, 5, 1, 25, listOf(30,30,29,30,29,30,29,29,30,29,30,29,30))
        add(2135, 0, 2, 13, listOf(30,29,30,30,29,29,30,29,30,29,30,29))
        add(2136, 0, 2, 2, listOf(30,29,30,30,29,30,29,30,29,30,29,30))
        add(2137, 2, 1, 22, listOf(29,30,29,30,29,30,30,29,30,29,30,29,30))
        add(2138, 0, 2, 10, listOf(29,30,29,30,29,30,29,30,30,29,30,29))
        add(2139, 7, 1, 30, listOf(30,29,30,29,29,30,29,30,30,30,29,30,29))
        add(2140, 0, 2, 18, listOf(30,29,30,29,29,30,29,30,30,29,30,30))
        add(2141, 0, 2, 7, listOf(29,30,29,30,29,29,30,29,30,29,30,30))
        add(2142, 5, 1, 27, listOf(30,29,30,29,30,29,29,29,30,29,30,30,30))
        add(2143, 0, 2, 15, listOf(29,30,30,29,30,29,29,29,30,29,30,30))
        add(2144, 0, 2, 4, listOf(29,30,30,30,29,29,30,29,30,29,29,30))
        add(2145, 4, 1, 23, listOf(30,29,30,30,29,30,29,30,29,30,29,30,29))
        add(2146, 0, 2, 11, listOf(29,30,30,29,30,29,30,30,29,30,29,30))
        add(2147, 0, 2, 1, listOf(29,29,30,29,30,30,29,30,30,29,30,29))
        add(2148, 1, 1, 21, listOf(30,29,30,29,29,30,29,30,30,29,30,30,29))
        add(2149, 0, 2, 8, listOf(30,29,30,29,29,30,29,30,29,30,30,30))
        add(2150, 6, 1, 29, listOf(29,30,29,30,29,29,29,30,29,30,30,30,29))
        add(2151, 0, 2, 16, listOf(30,30,29,30,29,29,29,30,29,30,30,29))
        add(2152, 0, 2, 5, listOf(30,30,30,29,30,29,29,29,30,29,30,30))
        add(2153, 5, 1, 25, listOf(29,30,30,29,30,29,30,29,30,29,29,30,29))
        add(2154, 0, 2, 12, listOf(30,30,29,30,30,29,30,29,30,29,29,30))
        add(2155, 0, 2, 2, listOf(29,30,29,30,30,29,30,30,29,30,29,30))
        add(2156, 3, 1, 23, listOf(29,29,30,29,30,29,30,30,29,30,30,29,30))
        add(2157, 0, 2, 10, listOf(29,29,30,29,30,29,30,29,30,30,30,29))
        add(2158, 7, 1, 30, listOf(30,29,30,29,29,29,30,29,30,30,30,29,30))
        add(2159, 0, 2, 18, listOf(30,29,30,29,29,29,30,29,30,30,29,30))
        add(2160, 0, 2, 7, listOf(30,30,29,30,29,29,29,30,29,30,29,30))
        add(2161, 6, 1, 26, listOf(30,30,29,30,29,30,29,29,30,29,30,29,30))
        add(2162, 0, 2, 14, listOf(30,29,30,30,29,30,29,30,29,29,30,29))
        add(2163, 0, 2, 3, listOf(30,30,29,30,29,30,30,29,30,29,29,30))
        add(2164, 4, 1, 24, listOf(29,30,29,30,29,30,30,29,30,30,29,30,29))
        add(2165, 0, 2, 11, listOf(29,30,29,30,29,30,29,30,30,30,29,30))
        add(2166, 10, 2, 1, listOf(29,29,30,29,30,29,30,29,30,30,29,30,30))
        add(2167, 0, 2, 20, listOf(29,30,29,29,29,30,29,30,30,29,30,30))
        add(2168, 0, 2, 9, listOf(30,29,30,29,29,29,30,29,30,29,30,30))
        add(2169, 6, 1, 28, listOf(30,29,30,29,30,29,29,30,29,30,29,30,30))
        add(2170, 0, 2, 16, listOf(30,29,30,29,30,29,29,30,29,30,29,30))
        add(2171, 0, 2, 5, listOf(30,29,30,30,29,30,29,29,30,29,30,29))
        add(2172, 4, 1, 25, listOf(30,29,30,30,29,30,29,30,30,29,29,30,29))
        add(2173, 0, 2, 12, listOf(30,29,30,29,30,30,29,30,29,30,30,29))
        add(2174, 0, 2, 2, listOf(29,30,29,30,29,30,29,30,30,29,30,30))
        add(2175, 3, 1, 23, listOf(29,29,30,29,29,30,29,30,30,29,30,30,30))
        add(2176, 0, 2, 11, listOf(29,30,29,29,29,30,29,30,29,30,30,30))
        add(2177, 7, 1, 30, listOf(29,30,30,29,29,29,30,29,30,29,30,30,30))
        add(2178, 0, 2, 18, listOf(29,30,29,30,29,29,30,29,30,29,30,30))
        add(2179, 0, 2, 7, listOf(29,30,30,29,30,29,29,30,29,30,29,30))
        add(2180, 6, 1, 27, listOf(29,30,30,29,30,30,29,29,30,29,30,29,30))
        add(2181, 0, 2, 14, listOf(29,30,29,30,30,29,30,29,30,29,30,29))
        add(2182, 0, 2, 3, listOf(30,29,30,29,30,29,30,30,29,30,29,30))
        add(2183, 4, 1, 24, listOf(29,30,29,30,29,29,30,30,29,30,30,29,30))
        add(2184, 0, 2, 12, listOf(29,30,29,29,30,29,30,29,30,30,30,29))
        add(2185, 0, 1, 31, listOf(30,29,30,29,29,30,29,30,29,30,30,30))
        add(2186, 2, 1, 21, listOf(29,30,29,30,29,29,30,29,30,29,30,30,29))
        add(2187, 0, 2, 8, listOf(30,30,29,30,29,29,30,29,30,29,30,29))
        add(2188, 6, 1, 28, listOf(30,30,30,29,30,29,29,30,29,30,29,30,29))
        add(2189, 0, 2, 15, listOf(30,30,29,30,29,30,29,30,29,30,29,30))
        add(2190, 0, 2, 5, listOf(29,30,29,30,30,29,30,29,30,29,30,29))
        add(2191, 5, 1, 25, listOf(30,29,30,29,30,29,30,30,29,30,29,30,29))
        add(2192, 0, 2, 13, listOf(30,29,29,30,29,30,30,29,30,30,29,30))
        add(2193, 0, 2, 2, listOf(29,30,29,29,30,29,30,29,30,30,30,29))
        add(2194, 3, 1, 22, listOf(30,29,30,29,29,30,29,30,29,30,29,30,30))
        add(2195, 0, 2, 10, listOf(30,29,30,29,29,30,29,29,30,30,29,30))
        add(2196, 7, 1, 30, listOf(30,30,29,30,29,29,30,29,29,30,29,30,30))
        add(2197, 0, 2, 17, listOf(30,29,30,30,29,29,30,29,30,29,29,30))
        add(2198, 0, 2, 6, listOf(30,29,30,30,29,30,29,30,29,30,29,30))
        add(2199, 5, 1, 27, listOf(29,30,29,30,29,30,30,29,30,29,30,29,30))
        add(2200, 0, 2, 15, listOf(29,29,30,29,30,30,29,30,30,29,30,29))
        add(2201, 0, 2, 4, listOf(30,29,29,30,29,30,29,30,30,30,29,30))
    }

    private fun MutableList<FallbackYearData>.add(
        year: Int, leap: Int, sm: Int, sd: Int, md: List<Int>
    ) {
        add(FallbackYearData(year, leap, sm, sd, md))
    }

    // ──────────────────────────────────────────────
    // 农历 → 公历转换（用于农历每月/每年重复排期）
    // ──────────────────────────────────────────────

    /**
     * 农历 → 公历日期转换
     *
     * @param lunarYear  农历年（如 2026）
     * @param lunarMonth 农历月（1-based，1=正月）
     * @param lunarDay   农历日（1-based）
     * @param isLeapMonth 是否闰月
     * @return 公历日期字符串 YYYY-MM-DD，失败返回 null
     */
    fun lunarToGregorian(lunarYear: Int, lunarMonth: Int, lunarDay: Int, isLeapMonth: Boolean = false): String? {
        val fromIcu = kotlin.runCatching { lunarToGregorianIcu(lunarYear, lunarMonth, lunarDay, isLeapMonth) }.getOrNull()
        if (fromIcu != null) return fromIcu
        return lunarToGregorianFallback(lunarYear, lunarMonth, lunarDay, isLeapMonth)
    }

    private fun lunarToGregorianIcu(lunarYear: Int, lunarMonth: Int, lunarDay: Int, isLeapMonth: Boolean): String {
        val cls = Class.forName("android.icu.util.ChineseCalendar")
        val cc = cls.getDeclaredConstructor().newInstance()
        cls.getMethod("clear").invoke(cc)

        val extendedYearField = cls.getField("EXTENDED_YEAR").get(null) as Int
        val monthField = cls.getField("MONTH").get(null) as Int
        val dayField = cls.getField("DAY_OF_MONTH").get(null) as Int
        val isLeapField = cls.getField("IS_LEAP_MONTH").get(null) as Int

        cls.getMethod("set", Integer.TYPE, Integer.TYPE).invoke(cc, extendedYearField, lunarYear + 2637)
        cls.getMethod("set", Integer.TYPE, Integer.TYPE).invoke(cc, monthField, lunarMonth - 1)
        cls.getMethod("set", Integer.TYPE, Integer.TYPE).invoke(cc, dayField, lunarDay)
        cls.getMethod("set", Integer.TYPE, Integer.TYPE).invoke(cc, isLeapField, if (isLeapMonth) 1 else 0)

        val timeInMillis = cls.getMethod("getTimeInMillis").invoke(cc) as Long
        val cal = Calendar.getInstance().apply { this.timeInMillis = timeInMillis }
        return DateUtils.formatGregorian(cal)
    }

    private fun lunarToGregorianFallback(lunarYear: Int, lunarMonth: Int, lunarDay: Int, isLeapMonth: Boolean): String? {
        val yd = FALLBACK_YEAR_DATA.firstOrNull { it.year == lunarYear } ?: return null
        val months = expandMonthDays(yd)
        var offset = 0
        var matched = false
        for (m in months) {
            if (m.monthIndex == lunarMonth && m.isLeap == isLeapMonth) {
                if (lunarDay > m.days) return null
                offset += lunarDay - 1
                matched = true
                break
            }
            offset += m.days
        }
        if (!matched) return null
        val springCal = Calendar.getInstance().apply {
            clear(); set(lunarYear, yd.springMonth - 1, yd.springDay)
        }
        springCal.add(Calendar.DAY_OF_MONTH, offset)
        return DateUtils.formatGregorian(springCal)
    }

    /**
     * 获取农历日名（用于日历格子显示）
     */
    fun getLunarDay(gregorian: Calendar): String {
        return try {
            val parts = toLunarParts(gregorian)
            lunarDayNames.getOrElse(parts.day - 1) { "${parts.day}" }
        } catch (_: Exception) {
            lunarDayNames.getOrElse(gregorian.get(Calendar.DAY_OF_MONTH) - 1) { "?" }
        }
    }

    /**
     * 农历「月+日」联合名：初一 → "六月"；其他 → "六月初二" / "五月廿二"
     * 月名后统一加"月"字（用户明确要求：五月廿二 而非 五廿二）
     */
    fun getLunarMonthDayName(gregorian: Calendar): String {
        return try {
            val parts = toLunarParts(gregorian)
            val monthName = if (parts.isLeapMonth) {
                "闰${lunarMonthNames[parts.month - 1]}"
            } else {
                lunarMonthNames[parts.month - 1]
            }
            if (parts.day == 1) "${monthName}月" else "${monthName}月${lunarDayNames[parts.day - 1]}"
        } catch (_: Exception) {
            ""
        }
    }

    fun getLunarMonthName(gregorian: Calendar): String {
        return try {
            val parts = toLunarParts(gregorian)
            if (parts.isLeapMonth) "闰${lunarMonthNames[parts.month - 1]}"
            else lunarMonthNames[parts.month - 1]
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * 月/年视图日期格子里第二行农历：初一 → "六月"；其他 → "初二"
     */
    fun getLunarDayLabel(gregorian: Calendar): String {
        return try {
            val parts = toLunarParts(gregorian)
            if (parts.day == 1) {
                if (parts.isLeapMonth) "闰${lunarMonthNames[parts.month - 1]}月"
                else "${lunarMonthNames[parts.month - 1]}月"
            } else {
                lunarDayNames[parts.day - 1]
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun getGanZhiYear(gregorian: Calendar): String {
        val year = gregorian.get(Calendar.YEAR)
        val stemIndex = ((year - 4) % 10).let { if (it < 0) it + 10 else it }
        val branchIndex = ((year - 4) % 12).let { if (it < 0) it + 12 else it }
        return "${stemArr[stemIndex]}${earthlyBranches[branchIndex]}年"
    }

    fun getZodiacSign(gregorian: Calendar): String {
        val year = gregorian.get(Calendar.YEAR)
        val zodiacIndex = (year - 4) % 12
        return zodiacSigns[zodiacIndex]
    }

    /** 获取星座（基于公历月/日） */
    fun getConstellation(month: Int, day: Int): String {
        return when {
            (month == 3 && day >= 21) || (month == 4 && day <= 19) -> "白羊座"
            (month == 4 && day >= 20) || (month == 5 && day <= 20) -> "金牛座"
            (month == 5 && day >= 21) || (month == 6 && day <= 21) -> "双子座"
            (month == 6 && day >= 22) || (month == 7 && day <= 22) -> "巨蟹座"
            (month == 7 && day >= 23) || (month == 8 && day <= 22) -> "狮子座"
            (month == 8 && day >= 23) || (month == 9 && day <= 22) -> "处女座"
            (month == 9 && day >= 23) || (month == 10 && day <= 23) -> "天秤座"
            (month == 10 && day >= 24) || (month == 11 && day <= 22) -> "天蝎座"
            (month == 11 && day >= 23) || (month == 12 && day <= 21) -> "射手座"
            (month == 12 && day >= 22) || (month == 1 && day <= 19) -> "摩羯座"
            (month == 1 && day >= 20) || (month == 2 && day <= 18) -> "水瓶座"
            else -> "双鱼座"
        }
    }

    fun getLunarDate(gregorian: Calendar): String {
        val parts = toLunarParts(gregorian)
        val monthName = if (parts.isLeapMonth) {
            "闰${lunarMonthNames[parts.month - 1]}"
        } else {
            lunarMonthNames[parts.month - 1]
        }
        return "${getGanZhiYear(gregorian)}${monthName}月${lunarDayNames.getOrElse(parts.day - 1) { "${parts.day}" }}"
    }

    fun getLunarMonthInfo(gregorian: Calendar): LunarMonthInfo {
        val parts = toLunarParts(gregorian)
        return LunarMonthInfo(parts.month, parts.isLeapMonth, 30)
    }

    fun isSolarTerm(gregorian: Calendar): Boolean {
        val month = gregorian.get(Calendar.MONTH) + 1
        val day = gregorian.get(Calendar.DAY_OF_MONTH)
        return solarTermData.any { it[0] == month && it[1] == day }
    }

    fun getSolarTerm(gregorian: Calendar): String? {
        val month = gregorian.get(Calendar.MONTH) + 1
        val day = gregorian.get(Calendar.DAY_OF_MONTH)
        for (i in solarTermData.indices) {
            if (solarTermData[i][0] == month && solarTermData[i][1] == day) {
                return solarTermNames[i]
            }
        }
        return null
    }

    fun getEightChar(gregorian: Calendar): EightChar {
        val year = gregorian.get(Calendar.YEAR)
        val month = gregorian.get(Calendar.MONTH) + 1
        val day = gregorian.get(Calendar.DAY_OF_MONTH)
        val hour = gregorian.get(Calendar.HOUR_OF_DAY)

        val yearGanZhi = "${stemArr[((year - 4) % 10).let { if (it < 0) it + 10 else it }]}${earthlyBranches[((year - 4) % 12).let { if (it < 0) it + 12 else it }]}"
        val monthGanZhi = "${stemArr[((year - 4 + month - 1) % 10).let { if (it < 0) it + 10 else it }]}${earthlyBranches[((year - 4 + month - 1) % 12).let { if (it < 0) it + 12 else it }]}"
        val dayGanZhi = "${stemArr[((year - 4 + day - 1) % 10).let { if (it < 0) it + 10 else it }]}${earthlyBranches[((year - 4 + day - 1) % 12).let { if (it < 0) it + 12 else it }]}"
        val hourGanZhi = "${stemArr[((year - 4 + day - 1 + hour - 1) % 10).let { if (it < 0) it + 10 else it }]}${earthlyBranches[((year - 4 + day - 1 + hour - 1) % 12).let { if (it < 0) it + 12 else it }]}"

        return EightChar(yearGanZhi, monthGanZhi, dayGanZhi, hourGanZhi)
    }

    fun getWeekdayShort(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "日"; 2 -> "一"; 3 -> "二"; 4 -> "三"; 5 -> "四"; 6 -> "五"; 7 -> "六"; else -> ""
    }
}
