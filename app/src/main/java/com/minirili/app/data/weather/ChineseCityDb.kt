package com.minirili.app.data.weather

import android.content.Context
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.sin
import kotlin.math.PI

/**
 * 中国城市本地数据库，覆盖全国县级行政区（含区/县/县级市/地级市/直辖市）。
 *
 * 数据从 assets/county.json 加载（约 3300 条），第一启动时由 [init] 初始化。
 * 用本地数据替代依赖于 Open-Meteo Geocoding（国内县级城市覆盖不全、省份标错）
 * 和 Nominatim（国内网络不可达）的外部搜索。搜索时与 Open-Meteo 结果合并。
 */
object ChineseCityDb {

    data class Entry(
        val name: String,       // 全名（如"武侯区"、"内江市"）
        val alias: String,      // 搜索别名（如"武侯"、"内江"）
        val province: String,   // 所属省（如"四川省"）
        val city: String,       // 所属地级市（如"内江市"；直辖市市区、省直辖县级市此值同 province）
        val latitude: Double,
        val longitude: Double,
    ) {
        fun toCity() = City(
            id = "$latitude,$longitude",
            name = name,
            latitude = latitude,
            longitude = longitude,
            country = province
        )
    }

    private var _entries: List<Entry>? = null
    private lateinit var _index: Map<String, List<Entry>>
    private lateinit var _aliasIndex: Map<String, List<Entry>>

    /** 初始化：从 assets 加载全国县级数据。在 Application.onCreate() 调用一次。 */
    fun init(context: Context) {
        _entries = ChineseCityDbData.loadEntries(context)
        _index = _entries!!.groupBy { it.name }
        _aliasIndex = _entries!!.groupBy { it.alias }
    }

    /** 测试用初始化：直接注入 Entry 列表（仅单元测试调用）。 */
    fun initForTest(entries: List<Entry>) {
        _entries = entries
        _index = entries.groupBy { it.name }
        _aliasIndex = entries.groupBy { it.alias }
    }

    private val entries: List<Entry>
        get() = _entries ?: throw IllegalStateException("ChineseCityDb.init() not called")

    private val index: Map<String, List<Entry>>
        get() = _index

    private val aliasIndex: Map<String, List<Entry>>
        get() = _aliasIndex

    /**
     * 在本地数据库中搜索城市。
     * 匹配规则：按名称/别名精确匹配 → 包含匹配 → 匹配到的城市也展示其下级区县。
     */
    fun search(query: String): List<City> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val matched = mutableSetOf<String>()

        // 1. 精确匹配：名称或别名完全匹配
        val exact = entries.filter { it.name == q || it.alias == q }
        exact.forEach { matched.add(it.name) }

        // 2. 包含匹配：名称或别名包含查询词
        val contains = entries.filter { it.name !in matched && (it.name.contains(q) || it.alias.contains(q)) }
        contains.forEach { matched.add(it.name) }

        // 3. 下级区县：已匹配到的城市，也展示其下属区县（city != name 的是区县）
        val matchedParents = (exact + contains).map { it.name }.toSet()
        val children = entries.filter { it.city != it.name && it.city in matchedParents }
        children.forEach { matched.add(it.name) }

        return (exact + contains + children).map { it.toCity() }
    }

    /**
     * 根据经纬度查找最近的城市（Haversine 距离）。
     * 返回匹配到的城市 Entry（含 name, latitude, longitude），若数据库为空则返回 null。
     */
    fun getNearestEntry(lat: Double, lon: Double): Entry? {
        var nearest: Entry? = null
        var minDist = Double.MAX_VALUE

        for (entry in entries) {
            val d = haversineDistance(entry.latitude, entry.longitude, lat, lon)
            if (d < minDist) {
                minDist = d
                nearest = entry
            }
        }
        return nearest
    }

    /** Haversine 球面距离公式，返回千米数 */
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Earth radius in km
        val dLat = toRadians(lat2 - lat1)
        val dLon = toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(toRadians(lat1)) * cos(toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * asin(sqrt(a))
        return R * c
    }

    private fun toRadians(degrees: Double) = degrees * Math.PI / 180.0
}