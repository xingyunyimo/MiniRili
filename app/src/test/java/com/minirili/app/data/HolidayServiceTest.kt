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
        assertEquals(39, holidays.size)

        val jan01 = holidays.find { it.date == "2026-01-01" }
        assertNotNull(jan01)
        assertEquals("元旦", jan01?.name)
        assertEquals(HolidayType.PUBLIC, jan01?.type)

        val jan04 = holidays.find { it.date == "2026-01-04" }
        assertNotNull(jan04)
        assertEquals("元旦", jan04?.name)
        assertEquals(HolidayType.TRANSFER, jan04?.type) // 元旦调休补班

        val feb15 = holidays.find { it.date == "2026-02-15" }
        assertNotNull(feb15)
        assertEquals("春节", feb15?.name)
        assertEquals(HolidayType.PUBLIC, feb15?.type) // 春节起点（9天）

        val feb17 = holidays.find { it.date == "2026-02-17" }
        assertNotNull(feb17)
        assertEquals("春节", feb17?.name)
        assertEquals(HolidayType.PUBLIC, feb17?.type)

        val feb23 = holidays.find { it.date == "2026-02-23" }
        assertNotNull(feb23)
        assertEquals("春节", feb23?.name)
        assertEquals(HolidayType.PUBLIC, feb23?.type) // 春节终点

        val feb14 = holidays.find { it.date == "2026-02-14" }
        assertNotNull(feb14)
        assertEquals(HolidayType.TRANSFER, feb14?.type) // 春节补班

        val feb28 = holidays.find { it.date == "2026-02-28" }
        assertNotNull(feb28)
        assertEquals(HolidayType.TRANSFER, feb28?.type) // 春节补班

        val may09 = holidays.find { it.date == "2026-05-09" }
        assertNotNull(may09)
        assertEquals(HolidayType.TRANSFER, may09?.type) // 劳动节补班

        val sep25 = holidays.find { it.date == "2026-09-25" }
        assertNotNull(sep25)
        assertEquals("中秋节", sep25?.name)
        assertEquals(HolidayType.PUBLIC, sep25?.type) // 修正中秋漂移（旧数据为9/27）

        val sep20 = holidays.find { it.date == "2026-09-20" }
        assertNotNull(sep20)
        assertEquals(HolidayType.TRANSFER, sep20?.type) // 国庆调休补班

        val oct07 = holidays.find { it.date == "2026-10-07" }
        assertNotNull(oct07)
        assertEquals(HolidayType.PUBLIC, oct07?.type) // 国庆终点（7天）

        val oct10 = holidays.find { it.date == "2026-10-10" }
        assertNotNull(oct10)
        assertEquals(HolidayType.TRANSFER, oct10?.type) // 国庆补班

        // 旧错误中秋日期不应残留（旧数据曾把中秋放在 9/27-9/29）
        assertNull(holidays.find { it.date == "2026-09-28" })
        assertNull(holidays.find { it.date == "2026-09-29" })
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
  {"date": "2026-01-04", "name": "元旦", "type": "TRANSFER"},
  {"date": "2026-02-14", "name": "春节", "type": "TRANSFER"},
  {"date": "2026-02-15", "name": "春节", "type": "PUBLIC"},
  {"date": "2026-02-16", "name": "春节", "type": "PUBLIC"},
  {"date": "2026-02-17", "name": "春节", "type": "PUBLIC"},
  {"date": "2026-02-18", "name": "春节", "type": "PUBLIC"},
  {"date": "2026-02-19", "name": "春节", "type": "PUBLIC"},
  {"date": "2026-02-20", "name": "春节", "type": "PUBLIC"},
  {"date": "2026-02-21", "name": "春节", "type": "PUBLIC"},
  {"date": "2026-02-22", "name": "春节", "type": "PUBLIC"},
  {"date": "2026-02-23", "name": "春节", "type": "PUBLIC"},
  {"date": "2026-02-28", "name": "春节", "type": "TRANSFER"},
  {"date": "2026-04-04", "name": "清明节", "type": "PUBLIC"},
  {"date": "2026-04-05", "name": "清明节", "type": "PUBLIC"},
  {"date": "2026-04-06", "name": "清明节", "type": "PUBLIC"},
  {"date": "2026-05-01", "name": "劳动节", "type": "PUBLIC"},
  {"date": "2026-05-02", "name": "劳动节", "type": "PUBLIC"},
  {"date": "2026-05-03", "name": "劳动节", "type": "PUBLIC"},
  {"date": "2026-05-04", "name": "劳动节", "type": "PUBLIC"},
  {"date": "2026-05-05", "name": "劳动节", "type": "PUBLIC"},
  {"date": "2026-05-09", "name": "劳动节", "type": "TRANSFER"},
  {"date": "2026-06-19", "name": "端午节", "type": "PUBLIC"},
  {"date": "2026-06-20", "name": "端午节", "type": "PUBLIC"},
  {"date": "2026-06-21", "name": "端午节", "type": "PUBLIC"},
  {"date": "2026-09-20", "name": "国庆节", "type": "TRANSFER"},
  {"date": "2026-09-25", "name": "中秋节", "type": "PUBLIC"},
  {"date": "2026-09-26", "name": "中秋节", "type": "PUBLIC"},
  {"date": "2026-09-27", "name": "中秋节", "type": "PUBLIC"},
  {"date": "2026-10-01", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2026-10-02", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2026-10-03", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2026-10-04", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2026-10-05", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2026-10-06", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2026-10-07", "name": "国庆节", "type": "PUBLIC"},
  {"date": "2026-10-10", "name": "国庆节", "type": "TRANSFER"}
]
        """.trimIndent()
    }
}