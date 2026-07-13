package com.minirili.app.data

import android.content.Context

/**
 * 节假日数据模型
 * P0-CAL-06 节假日与调休标记
 *
 * 数据来源：国务院发布的法定节假日安排，存储在 assets/holidays.json
 * 商用说明：政府信息，可自由使用
 */
data class Holiday(
    val date: String, // YYYY-MM-DD
    val name: String,
    val type: HolidayType,
    val isWorkday: Boolean = false // 调休补班
)

enum class HolidayType {
    PUBLIC,     // 法定节假日
    TRANSFER,   // 调休补班
    WORKDAY     // 工作日（春节/端午/中秋/国庆调休）
}

/**
 * 节假日服务。
 *
 * 首次调用 [initFromJson] 从 assets/holidays.json 加载数据，后续通过 [isHoliday] / [getHolidayName] / [isWorkday] 查询。
 * 新增年份只需编辑 holidays.json，无需改代码。
 */
object HolidayService {

    private val holidays = mutableMapOf<Int, List<Holiday>>()
    private var initialized = false

    /** 从 assets/holidays.json 初始化节假日数据。可在 Application.onCreate 中调用。 */
    fun initFromJson(context: Context) {
        if (initialized) return
        try {
            val json = context.assets.open("holidays.json").bufferedReader().use { it.readText() }
            parseHolidayJson(json)
        } catch (_: Exception) {
            // 若 assets 读取失败，保持空数据（不崩溃）
        }
    }

    /** 从 JSON 字符串直接加载（用于单元测试）。 */
    fun initFromJsonString(json: String) {
        if (initialized) return
        parseHolidayJson(json)
    }

    /**
     * 手动解析 JSON 数组（不依赖 org.json，兼容 JVM 单元测试）。
     * 格式：[{"date":"...","name":"...","type":"..."},...]
     */
    private fun parseHolidayJson(json: String) {
        try {
            val temp = mutableMapOf<Int, MutableList<Holiday>>()
            var i = json.indexOf('{')
            while (i >= 0) {
                val end = json.indexOf('}', i)
                if (end < 0) break
                val entry = json.substring(i, end + 1)
                i = json.indexOf('{', end)

                val date = extractJsonValue(entry, "date")
                val name = extractJsonValue(entry, "name")
                val type = extractJsonValue(entry, "type")
                if (date == null || name == null || type == null) continue

                val holidayType = when (type) {
                    "PUBLIC" -> HolidayType.PUBLIC
                    "TRANSFER" -> HolidayType.TRANSFER
                    else -> HolidayType.WORKDAY
                }
                val year = date.substring(0, 4).toIntOrNull() ?: continue
                temp.getOrPut(year) { mutableListOf() }.add(Holiday(date, name, holidayType))
            }
            temp.forEach { (year, list) -> holidays[year] = list }
            initialized = true
        } catch (_: Exception) {
        }
    }

    /** 从 JSON 对象字符串中提取指定 key 的字符串值（去掉引号）。兼容 "key":"val" 和 "key": "val" 两种格式。 */
    private fun extractJsonValue(entry: String, key: String): String? {
        val patterns = listOf("\"$key\":\"", "\"$key\": \"")
        for (search in patterns) {
            val start = entry.indexOf(search)
            if (start >= 0) {
                val valueStart = start + search.length
                val valueEnd = entry.indexOf('"', valueStart)
                if (valueEnd >= 0) return entry.substring(valueStart, valueEnd)
            }
        }
        return null
    }

    /**
     * 加载指定年份的节假日数据
     */
    fun loadHolidays(year: Int): List<Holiday> {
        return holidays[year] ?: emptyList()
    }

    /**
     * 查询指定日期是否是节假日
     */
    fun isHoliday(date: String): Holiday? {
        val year = date.substring(0, 4).toIntOrNull() ?: return null
        return loadHolidays(year).find { it.date == date }
    }

    /**
     * 获取指定日期的节假日名称
     */
    fun getHolidayName(date: String): String? {
        return isHoliday(date)?.name
    }

    /**
     * 判断是否是调休补班日（TRANSFER）
     */
    fun isTransferDay(date: String): Boolean {
        return isHoliday(date)?.type == HolidayType.TRANSFER
    }

    /**
     * 判断指定日期是否为"工作日"（需要上班的日子）：
     * - 普通工作日（周一至周五）且不是法定节假日 → 工作日
     * - 调休补班日（TRANSFER，周末上班） → 工作日
     * - 法定节假日（PUBLIC）→ 非工作日
     * - 普通周末 → 非工作日
     */
    fun isWorkday(date: String): Boolean {
        val holiday = isHoliday(date)
        if (holiday != null) {
            return holiday.type == HolidayType.TRANSFER
        }
        // 无假日标记 → 按周判断
        val cal = java.util.Calendar.getInstance().apply {
            val parts = date.split("-")
            if (parts.size == 3) {
                set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
        }
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        return dow != java.util.Calendar.SATURDAY && dow != java.util.Calendar.SUNDAY
    }

    /**
     * 清空缓存
     */
    fun clearCache() {
        holidays.clear()
        initialized = false
    }
}