package com.minirili.app.data.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * ChineseCityDb 本地搜索的单元测试。
 * 纯 Kotlin 逻辑，不依赖 Android。
 */
class ChineseCityDbTest {

    companion object {
        @BeforeClass @JvmStatic
        fun setUp() {
            ChineseCityDb.initForTest(
                listOf(
                    // 直辖市
                    ChineseCityDb.Entry("北京市", "北京", "北京市", "北京市", 39.9042, 116.4074),
                    ChineseCityDb.Entry("东城区", "东城", "北京市", "北京市", 39.9175, 116.4188),
                    ChineseCityDb.Entry("西城区", "西城", "北京市", "北京市", 39.9123, 116.3660),
                    // 四川省：地级市 + 区县
                    ChineseCityDb.Entry("成都市", "成都", "四川省", "成都市", 30.5728, 104.0668),
                    ChineseCityDb.Entry("锦江区", "锦江", "四川省", "成都市", 30.6544, 104.0833),
                    ChineseCityDb.Entry("武侯区", "武侯", "四川省", "成都市", 30.5728, 104.0668),
                    ChineseCityDb.Entry("资阳市", "资阳", "四川省", "资阳市", 30.1289, 104.6351),
                    ChineseCityDb.Entry("雁江区", "雁江", "四川省", "资阳市", 30.1082, 104.6773),
                    ChineseCityDb.Entry("乐至县", "乐至", "四川省", "资阳市", 30.2761, 105.0319),
                    ChineseCityDb.Entry("安岳县", "安岳", "四川省", "资阳市", 30.0992, 105.3358),
                    ChineseCityDb.Entry("内江市", "内江", "四川省", "内江市", 29.5802, 105.0632),
                    ChineseCityDb.Entry("市中区", "市中", "四川省", "内江市", 29.5855, 105.0684),
                    ChineseCityDb.Entry("东兴区", "东兴", "四川省", "内江市", 29.5936, 105.0755),
                    ChineseCityDb.Entry("威远县", "威远", "四川省", "内江市", 29.5269, 104.6679),
                    ChineseCityDb.Entry("资中县", "资中", "四川省", "内江市", 29.7704, 104.8559),
                    ChineseCityDb.Entry("隆昌市", "隆昌", "四川省", "内江市", 29.3395, 105.2877),
                )
            )
        }
    }

    @Test
    fun `search 内江 should return 内江市 with correct province`() {
        val results = ChineseCityDb.search("内江")
        assertTrue("内江 should find results", results.isNotEmpty())
        val neijiang = results.find { it.name == "内江市" }
        assertNotNull("内江市 should be found", neijiang)
        assertEquals("内江市 province should be 四川省", "四川省", neijiang?.country)
    }

    @Test
    fun `search 内江 should include district level results`() {
        val results = ChineseCityDb.search("内江")
        val districts = listOf("市中区", "东兴区", "威远县", "资中县", "隆昌市")
        val found = results.filter { it.name in districts }
        assertTrue("内江 search should include districts: ${found.map { it.name }}", found.isNotEmpty())
    }

    @Test
    fun `search 资阳 should return 资阳市`() {
        val results = ChineseCityDb.search("资阳")
        assertTrue("资阳 should find results", results.isNotEmpty())
        val ziyang = results.find { it.name == "资阳市" }
        assertNotNull("资阳市 should be found", ziyang)
        assertEquals("资阳市 province should be 四川省", "四川省", ziyang?.country)
    }

    @Test
    fun `search 乐至 should return 乐至县`() {
        val results = ChineseCityDb.search("乐至")
        assertTrue("乐至 should find results", results.isNotEmpty())
        val lezhi = results.find { it.name == "乐至县" }
        assertNotNull("乐至县 should be found", lezhi)
        assertEquals("乐至县 province should be 四川省", "四川省", lezhi?.country)
    }

    @Test
    fun `search 安岳 should return 安岳县`() {
        val results = ChineseCityDb.search("安岳")
        assertTrue("安岳 should find results", results.isNotEmpty())
        val anyue = results.find { it.name == "安岳县" }
        assertNotNull("安岳县 should be found", anyue)
        assertEquals("安岳县 province should be 四川省", "四川省", anyue?.country)
    }

    @Test
    fun `search 北京 should return 北京市`() {
        val results = ChineseCityDb.search("北京")
        assertTrue("北京 should find results", results.isNotEmpty())
        val beijing = results.find { it.name == "北京市" }
        assertNotNull("北京市 should be found", beijing)
        assertTrue("Beijing country should not be null", beijing?.country != null)
    }

    @Test
    fun `search 成都 should return 成都市`() {
        val results = ChineseCityDb.search("成都")
        assertTrue("成都 should find results", results.isNotEmpty())
        val chengdu = results.find { it.name == "成都市" }
        assertNotNull("成都市 should be found", chengdu)
        assertEquals("成都市 province", "四川省", chengdu?.country)
    }

    @Test
    fun `search empty query should return empty`() {
        val results = ChineseCityDb.search("")
        assertTrue("empty query should return empty", results.isEmpty())
    }

    @Test
    fun `search non-existent city should return empty or not crash`() {
        val results = ChineseCityDb.search("纽约") // 不在数据库中
        assertNotNull("search should not crash", results)
    }

    @Test
    fun `search 内江 should include 内江市 districts via city field`() {
        val results = ChineseCityDb.search("内江")
        // 内江市 本身和其下辖 5 个区县
        assertTrue("内江 search should return at least 6 results (1 city + 5 districts)", results.size >= 6)
        val names = results.map { it.name }.toSet()
        assertTrue("should include 内江市", "内江市" in names)
        assertTrue("should include 市中区", "市中区" in names)
        assertTrue("should include 东兴区", "东兴区" in names)
        assertTrue("should include 威远县", "威远县" in names)
        assertTrue("should include 资中县", "资中县" in names)
        assertTrue("should include 隆昌市", "隆昌市" in names)
    }

    @Test
    fun `search 成都 should include 成都市 districts`() {
        val results = ChineseCityDb.search("成都")
        assertTrue("成都 search should return at least 2 results", results.size >= 2)
        val names = results.map { it.name }.toSet()
        assertTrue("should include 锦江区", "锦江区" in names)
        assertTrue("should include 武侯区", "武侯区" in names)
    }

    @Test
    fun `getNearestEntry should return entry with city field`() {
        val entry = ChineseCityDb.getNearestEntry(29.58, 105.06) // 内江市附近
        assertNotNull("should find nearest entry", entry)
        assertEquals("city should be 内江市", "内江市", entry?.city)
        assertEquals("province should be 四川省", "四川省", entry?.province)
    }

    @Test
    fun `search 武侯 should return 武侯区`() {
        val results = ChineseCityDb.search("武侯")
        assertTrue("武侯 should find results", results.isNotEmpty())
        val wuhou = results.find { it.name == "武侯区" }
        assertNotNull("武侯区 should be found", wuhou)
        assertEquals("武侯区 province should be 四川省", "四川省", wuhou?.country)
    }

    private fun assertNotNull(message: String, obj: Any?) {
        if (obj == null) throw AssertionError(message)
    }
}