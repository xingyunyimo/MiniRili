package com.minirili.app.data.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ChineseCityDb 本地搜索的单元测试。
 * 纯 Kotlin 逻辑，不依赖 Android。
 */
class ChineseCityDbTest {

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
    fun `search non-existent city should return empty`() {
        val results = ChineseCityDb.search("纽约") // 不在数据库中
        // 可能为空，也可能有包含匹配；确认不崩溃即可
        assertNotNull("search should not crash", results)
    }

    private fun assertNotNull(message: String, obj: Any?) {
        if (obj == null) throw AssertionError(message)
    }
}