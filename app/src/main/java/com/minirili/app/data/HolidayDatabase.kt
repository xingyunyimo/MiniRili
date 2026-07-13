package com.minirili.app.data

/**
 * 节假日数据库（兼容层）
 * P0-CAL-06 节假日与调休标记
 *
 * 数据来源：国务院发布的法定节假日安排
 * 底层使用 HolidayService 的完整节假日数据
 */
object HolidayDatabase {
    fun loadHolidays(year: Int): List<Holiday> = HolidayService.loadHolidays(year)

    fun isHoliday(date: String): Holiday? = HolidayService.isHoliday(date)
}