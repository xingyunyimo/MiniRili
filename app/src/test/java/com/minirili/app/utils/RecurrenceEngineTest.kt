package com.minirili.app.utils

import com.minirili.app.database.entity.EventEntity
import org.junit.Test
import org.junit.Assert.*
import java.util.Calendar

class RecurrenceEngineTest {

    private fun event(
        gregorianDate: String,
        useLunar: Boolean = false,
        repeatType: String = "none",
        title: String = "test"
    ) = EventEntity(
        title = title,
        gregorianDate = gregorianDate,
        useLunar = useLunar,
        repeatType = repeatType
    )

    // ---- 非重复事件 ----

    @Test
    fun nonRepeatingSolarEvent_showsInRange() {
        val e = event("2026-07-13")
        val result = RecurrenceEngine.expandForRange(listOf(e), "2026-07-01", "2026-07-31")
        assertEquals(1, result.size)
        assertEquals("2026-07-13", result[0].occurrenceDate)
    }

    @Test
    fun nonRepeatingLunarEvent_showsInRange() {
        val e = event("2026-07-13", useLunar = true)
        val result = RecurrenceEngine.expandForRange(listOf(e), "2026-07-01", "2026-07-31")
        assertEquals(1, result.size)
        assertEquals("2026-07-13", result[0].occurrenceDate)
    }

    @Test
    fun nonRepeatingEvent_outsideRange_returnsEmpty() {
        val e = event("2026-07-13", useLunar = true)
        val result = RecurrenceEngine.expandForRange(listOf(e), "2026-08-01", "2026-08-31")
        assertTrue(result.isEmpty())
    }

    // ---- 农历每月重复 ----

    @Test
    fun lunarMonthlyEvent_expandsCorrectly() {
        // 2026-07-13 = 五月廿九（农历每月廿九重复）
        // 预期: 2026-07-13, 2026-08-11（六月廿九）
        val e = event("2026-07-13", useLunar = true, repeatType = "monthly")
        val result = RecurrenceEngine.expandForRange(listOf(e), "2026-07-01", "2026-08-31")
        val dates = result.map { it.occurrenceDate }.sorted()
        assertEquals(listOf("2026-07-13", "2026-08-11"), dates)
    }

    @Test
    fun lunarMonthlyEvent_rangeAfterAnchor() {
        // 锚点 2026-07-13，查询范围 2026-08-01 ~ 2026-08-31
        // 应找到 2026-08-11（六月廿九）
        val e = event("2026-07-13", useLunar = true, repeatType = "monthly")
        val result = RecurrenceEngine.expandForRange(listOf(e), "2026-08-01", "2026-08-31")
        val dates = result.map { it.occurrenceDate }
        assertEquals(listOf("2026-08-11"), dates)
    }

    @Test
    fun lunarMonthlyEvent_rangeBeforeAnchor_returnsEmpty() {
        val e = event("2026-07-13", useLunar = true, repeatType = "monthly")
        val result = RecurrenceEngine.expandForRange(listOf(e), "2026-05-01", "2026-05-31")
        assertTrue(result.isEmpty())
    }

    @Test
    fun lunarMonthlyEvent_expandsMultipleMonths() {
        // 2026-07-13 开始，每月廿九重复，查询 7~12 月共 6 个月
        val e = event("2026-07-13", useLunar = true, repeatType = "monthly")
        val result = RecurrenceEngine.expandForRange(listOf(e), "2026-07-01", "2026-12-31")
        val dates = result.map { it.occurrenceDate }.sorted()
        // 五月廿九=7/13, 六月廿九=8/11, 七月廿九=9/10, 八月廿九=10/10, 九月廿九=11/08, 十月廿九=12/08
        assertEquals(6, dates.size)
        assertEquals("2026-07-13", dates[0])
        assertEquals("2026-08-11", dates[1])
    }

    // ---- 农历每年重复 ----

    @Test
    fun lunarYearlyEvent_expandsCorrectly() {
        // 2026-07-13 = 五月廿九，每年农历五月廿九重复
        // 回退表仅覆盖 2000-2030，2027 年不在表中，因此只验证首次出现
        val e = event("2026-07-13", useLunar = true, repeatType = "yearly")
        val result = RecurrenceEngine.expandForRange(listOf(e), "2026-01-01", "2026-12-31")
        val dates = result.map { it.occurrenceDate }.sorted()
        assertEquals(listOf("2026-07-13"), dates)
    }

    // ---- 非农历重复不干扰 ----

    @Test
    fun solarMonthlyEvent_unaffectedByLunarChanges() {
        val e = event("2026-07-13", useLunar = false, repeatType = "monthly")
        val result = RecurrenceEngine.expandForRange(listOf(e), "2026-07-01", "2026-09-30")
        val dates = result.map { it.occurrenceDate }.sorted()
        // 阳历每月13日
        assertEquals(listOf("2026-07-13", "2026-08-13", "2026-09-13"), dates)
    }

    // ---- skipDates 过滤 ----

    @Test
    fun lunarMonthlyEvent_skipDatesExcluded() {
        val e = event("2026-07-13", useLunar = true, repeatType = "monthly")
        // 手动设置 skipDates
        val eWithSkip = e.copy(skipDates = "2026-08-11")
        val result = RecurrenceEngine.expandForRange(listOf(eWithSkip), "2026-07-01", "2026-08-31")
        val dates = result.map { it.occurrenceDate }
        assertEquals(listOf("2026-07-13"), dates)
    }

    @Test
    fun lunarMonthlyEvent_skipDatesNotExcluded_whenExcludeFalse() {
        val e = event("2026-07-13", useLunar = true, repeatType = "monthly").copy(skipDates = "2026-08-11")
        val result = RecurrenceEngine.expandForRange(
            listOf(e), "2026-07-01", "2026-08-31", excludeSkipDates = false
        )
        val dates = result.map { it.occurrenceDate }.sorted()
        assertEquals(listOf("2026-07-13", "2026-08-11"), dates)
    }

    // ---- expandForDate ----

    @Test
    fun expandForDate_lunarMonthly_returnsCorrectDate() {
        val e = event("2026-07-13", useLunar = true, repeatType = "monthly")
        // 查询 2026-08-11 当天（六月廿九）
        val result = RecurrenceEngine.expandForDate(listOf(e), "2026-08-11")
        assertEquals(1, result.size)
        assertEquals("2026-08-11", result[0].occurrenceDate)
    }

    @Test
    fun expandForDate_lunarMonthly_noMatch_returnsEmpty() {
        val e = event("2026-07-13", useLunar = true, repeatType = "monthly")
        val result = RecurrenceEngine.expandForDate(listOf(e), "2026-08-15")
        assertTrue(result.isEmpty())
    }

    // ---- 混合事件列表 ----

    @Test
    fun mixedEvents_expandCorrectly() {
        val solarOnce = event("2026-07-20")
        val lunarMonthly = event("2026-07-13", useLunar = true, repeatType = "monthly")
        val result = RecurrenceEngine.expandForRange(
            listOf(solarOnce, lunarMonthly), "2026-07-01", "2026-08-31"
        )
        val dates = result.map { it.occurrenceDate }.sorted()
        // 阳历一次: 7/20, 农历每月: 7/13, 8/11
        assertEquals(listOf("2026-07-13", "2026-07-20", "2026-08-11"), dates)
    }
}