package com.minirili.app.data

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * HolidayService 单元测试
 */
class HolidayServiceTest {

    @Before
    fun setUp() {
        HolidayService.initFromJsonString(HOLIDAYS_JSON)
    }

    @After
    fun tearDown() {
        HolidayService.clearCache()
    }

    @Test
    fun loadHolidays_2024_returnsCorrectHolidays() {
        val holidays = HolidayService.loadHolidays(2024)

        assertEquals(22, holidays.size)

        val jan1 = holidays.find { it.date == "2024-01-01" }
        assertNotNull(jan1)
        assertEquals("元旦", jan1?.name)
        assertEquals(HolidayType.PUBLIC, jan1?.type)

        val feb10 = holidays.find { it.date == "2024-02-10" }
        assertNotNull(feb10)
        assertEquals("春节", feb10?.name)
        assertEquals(HolidayType.PUBLIC, feb10?.type)

        val apr04 = holidays.find { it.date == "2024-04-04" }
        assertNotNull(apr04)
        assertEquals("清明节", apr04?.name)
        assertEquals(HolidayType.PUBLIC, apr04?.type)

        val oct01 = holidays.find { it.date == "2024-10-01" }
        assertNotNull(oct01)
        assertEquals("国庆节", oct01?.name)
        assertEquals(HolidayType.PUBLIC, oct01?.type)
    }

    @Test
    fun loadHolidays_2025_returnsCorrectHolidays() {
        val holidays = HolidayService.loadHolidays(2025)

        assertEquals(23, holidays.size)

        val jan01 = holidays.find { it.date == "2025-01-01" }
        assertNotNull(jan01)
        assertEquals("元旦", jan01?.name)
        assertEquals(HolidayType.PUBLIC, jan01?.type)

        val feb10 = holidays.find { it.date == "2025-02-10" }
        assertNotNull(feb10)
        assertEquals("元宵节", feb10?.name)
        assertEquals(HolidayType.TRANSFER, feb10?.type)

        val may04 = holidays.find { it.date == "2025-05-04" }
        assertNotNull(may04)
        assertEquals("劳动节", may04?.name)
        assertEquals(HolidayType.TRANSFER, may04?.type)
    }

    @Test
    fun loadHolidays_2026_returnsCorrectHolidays() {
        val holidays = HolidayService.loadHolidays(2026)
        assertEquals(28, holidays.size)

        val jan01 = holidays.find { it.date == "2026-01-01" }
        assertNotNull(jan01)
        assertEquals("元旦", jan01?.name)
        assertEquals(HolidayType.PUBLIC, jan01?.type)

        val feb17 = holidays.find { it.date == "2026-02-17" }
        assertNotNull(feb17)
        assertEquals("春节", feb17?.name)
        assertEquals(HolidayType.PUBLIC, feb17?.type)
    }

    @Test
    fun isHoliday_returnsHolidayForHolidayDate() {
        val holiday = HolidayService.isHoliday("2024-01-01")
        assertNotNull(holiday)
        assertEquals("元旦", holiday?.name)
        assertEquals(HolidayType.PUBLIC, holiday?.type)
    }

    @Test
    fun isHoliday_returnsNullForNonHoliday() {
        val holiday = HolidayService.isHoliday("2024-01-02")
        assertNull(holiday)
    }

    @Test
    fun getHolidayName_returnsCorrectName() {
        assertEquals("元旦", HolidayService.getHolidayName("2024-01-01"))
        assertEquals("春节", HolidayService.getHolidayName("2024-02-10"))
        assertEquals("国庆节", HolidayService.getHolidayName("2024-10-01"))
        assertNull(HolidayService.getHolidayName("2024-01-02"))
    }

    @Test
    fun isTransferDay_returnsTrueForTransferDay() {
        assertTrue(HolidayService.isTransferDay("2024-02-24"))
        assertTrue(HolidayService.isTransferDay("2024-05-04"))
        assertTrue(HolidayService.isTransferDay("2024-10-08"))
    }

    @Test
    fun isTransferDay_returnsFalseForNormalWorkday() {
        assertFalse(HolidayService.isTransferDay("2024-01-01")) // 法定假日
        assertFalse(HolidayService.isTransferDay("2024-01-02")) // 普通工作日
    }

    @Test
    fun isWorkday_returnsTrueForNormalWorkday() {
        assertTrue(HolidayService.isWorkday("2024-01-02"))
        assertTrue(HolidayService.isWorkday("2024-01-03"))
    }

    @Test
    fun isWorkday_returnsFalseForHoliday() {
        assertFalse(HolidayService.isWorkday("2024-01-01")) // 法定假日
        assertFalse(HolidayService.isWorkday("2024-02-10")) // 春节是法定假日
    }

    @Test
    fun isWorkday_returnsTrueForTransferDay() {
        assertTrue(HolidayService.isWorkday("2024-02-24")) // 元宵节 TRANSFER，实际要上班
        assertTrue(HolidayService.isWorkday("2024-05-04")) // 劳动节 TRANSFER
        assertTrue(HolidayService.isWorkday("2024-10-08")) // 国庆节 TRANSFER
    }

    @Test
    fun clearCache_clearsCache() {
        HolidayService.loadHolidays(2024)
        val beforeClear = HolidayService.isHoliday("2024-01-01")
        assertNotNull(beforeClear)

        HolidayService.clearCache()

        // 清空后重新加载（需要再次 initFromJsonString）
        HolidayService.initFromJsonString(HOLIDAYS_JSON)
        val reloaded = HolidayService.loadHolidays(2024)
        assertEquals(22, reloaded.size)
    }

    companion object {
        private val HOLIDAYS_JSON = """
[
  {"date": "2024-01-01", "name": "元旦", "type": "PUBLIC"},
  {"date": "2024-02-10", "name": "春节", "type": "PUBLIC"},
  {"date": "2024-02-11", "name": "春节", "type": "PUBLIC"},
  {"date": "2024-02-12", "name": "春节", "type": "PUBLIC"},
  {"date": "2024-02-13", "name": "春节", "type": "PUBLIC"},
  {"date": "2024-02-14", "name": "春节", "type": "PUBLIC"},
  {"date": "2024-02-24", "name": "元宵节", "type": "TRANSFER"},
  {"date": "2024-04-04", "name": "清明节", "type": "PUBLIC"},
  {"date": "2024-05-01", "name": "劳动节", "type": "PUBLIC"},
  {"date": "2024-05-02", "name": "劳动节", "type": "PUBLIC"},
  {"date": "2024-05-03", "name": "劳动节", "type": "PUBLIC"},
  {"date": "2024-05-04", "name": "劳动节", "type": "TRANSFER"},
  {"date": "2024-06-10", "name": "端午节", "type": "PUBLIC"},
  {"date": "2024-06-11", "name": "端午节", "type": "TRANSFER"},
  {"date": "2024-10-01", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2024-10-02", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2024-10-03", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2024-10-04", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2024-10-05", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2024-10-06", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2024-10-07", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2024-10-08", "name": "国庆节", "type": "TRANSFER"},
  {"date": "2025-01-01", "name": "元旦", "type": "PUBLIC"},
  {"date": "2025-01-28", "name": "春节", "type": "PUBLIC"},
  {"date": "2025-02-01", "name": "春节", "type": "PUBLIC"},
  {"date": "2025-02-02", "name": "春节", "type": "PUBLIC"},
  {"date": "2025-02-03", "name": "春节", "type": "PUBLIC"},
  {"date": "2025-02-04", "name": "春节", "type": "PUBLIC"},
  {"date": "2025-02-05", "name": "春节", "type": "PUBLIC"},
  {"date": "2025-02-10", "name": "元宵节", "type": "TRANSFER"},
  {"date": "2025-04-04", "name": "清明节", "type": "PUBLIC"},
  {"date": "2025-05-01", "name": "劳动节", "type": "PUBLIC"},
  {"date": "2025-05-02", "name": "劳动节", "type": "PUBLIC"},
  {"date": "2025-05-03", "name": "劳动节", "type": "PUBLIC"},
  {"date": "2025-05-04", "name": "劳动节", "type": "TRANSFER"},
  {"date": "2025-05-31", "name": "端午节", "type": "PUBLIC"},
  {"date": "2025-06-01", "name": "端午节", "type": "TRANSFER"},
  {"date": "2025-10-01", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2025-10-02", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2025-10-03", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2025-10-04", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2025-10-05", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2025-10-06", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2025-10-07", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2025-10-08", "name": "国庆节", "type": "TRANSFER"},
  {"date": "2026-01-01", "name": "元旦", "type": "PUBLIC"},
  {"date": "2026-01-02", "name": "元旦", "type": "PUBLIC"},
  {"date": "2026-01-03", "name": "元旦", "type": "PUBLIC"},
  {"date": "2026-02-16", "name": "春节", "type": "PUBLIC"},
  {"date": "2026-02-17", "name": "春节", "type": "PUBLIC"},
  {"date": "2026-02-18", "name": "春节", "type": "PUBLIC"},
  {"date": "2026-02-19", "name": "春节", "type": "PUBLIC"},
  {"date": "2026-02-20", "name": "春节", "type": "PUBLIC"},
  {"date": "2026-02-21", "name": "春节", "type": "PUBLIC"},
  {"date": "2026-02-22", "name": "春节", "type": "PUBLIC"},
  {"date": "2026-04-04", "name": "清明节", "type": "PUBLIC"},
  {"date": "2026-04-05", "name": "清明节", "type": "PUBLIC"},
  {"date": "2026-04-06", "name": "清明节", "type": "PUBLIC"},
  {"date": "2026-05-01", "name": "劳动节", "type": "PUBLIC"},
  {"date": "2026-05-02", "name": "劳动节", "type": "PUBLIC"},
  {"date": "2026-05-03", "name": "劳动节", "type": "PUBLIC"},
  {"date": "2026-05-04", "name": "劳动节", "type": "PUBLIC"},
  {"date": "2026-05-05", "name": "劳动节", "type": "PUBLIC"},
  {"date": "2026-06-19", "name": "端午节", "type": "PUBLIC"},
  {"date": "2026-06-20", "name": "端午节", "type": "PUBLIC"},
  {"date": "2026-06-21", "name": "端午节", "type": "PUBLIC"},
  {"date": "2026-09-27", "name": "中秋节", "type": "PUBLIC"},
  {"date": "2026-09-28", "name": "中秋节", "type": "PUBLIC"},
  {"date": "2026-09-29", "name": "中秋节", "type": "PUBLIC"},
  {"date": "2026-10-01", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2026-10-02", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2026-10-03", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2026-10-04", "name": "国庆节", "type": "PUBLIC"}
]
        """.trimIndent()
    }
}