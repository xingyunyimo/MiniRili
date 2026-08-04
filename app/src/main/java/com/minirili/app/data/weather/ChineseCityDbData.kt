package com.minirili.app.data.weather

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 从 assets/county.json 加载全国县级行政区数据。
 * 数据源：modood/Administrative-divisions-of-China (MIT)
 */
internal object ChineseCityDbData {

    /** 直辖市列表，用于 city 字段归一化 */
    private val DIRECT_CITIES = setOf("北京市", "天津市", "上海市", "重庆市")

    /**
     * 从 assets 读取 county.json 并解析为 Entry 列表。
     * 条目数约 3300（含区县 + 地级市 + 直辖市）。
     */
    fun loadEntries(context: Context): List<ChineseCityDb.Entry> {
        val json = context.assets.open("county.json")
            .bufferedReader()
            .use { it.readText() }
        val arr = JSONArray(json)
        val list = mutableListOf<ChineseCityDb.Entry>()

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val name = obj.getString("name")
            val province = obj.getString("province")
            var city = obj.optString("city", "")

            // 直辖市下属区县的 city 字段为"市辖区"，归一化为直辖市名
            if (city == "市辖区" && province in DIRECT_CITIES) {
                city = province
            }
            // 省直辖县级市（如"省直辖县级行政区划"）视为省=城市
            if (city.isEmpty() || city == "省直辖县级行政区划" || city.startsWith("省直辖")) {
                city = province
            }

            val lat = obj.getDouble("lat")
            val lon = obj.getDouble("lon")
            val alias = aliasOf(name)

            list.add(ChineseCityDb.Entry(name, alias, province, city, lat, lon))
        }
        return list
    }

    /** 从行政区全名生成搜索别名：去掉"市/区/县/旗/自治县/自治旗/特区/林区/矿区"后缀 */
    private fun aliasOf(name: String): String {
        val suffixes = listOf(
            "市", "区", "县", "旗", "自治县", "自治旗",
            "特区", "林区", "矿区", "地区", "盟",
        )
        for (suffix in suffixes) {
            if (name.endsWith(suffix) && name.length > suffix.length) {
                return name.removeSuffix(suffix)
            }
        }
        return name
    }
}