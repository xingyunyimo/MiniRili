package com.minirili.app.utils

import com.minirili.app.database.entity.EventEntity
import org.junit.Test
import org.junit.Assert.*

class IcsUtilsTest {

    @Test
    fun generateICS_generatesValidICS() {
        val events = listOf(
            EventEntity(
                id = 1,
                title = "测试事件",
                description = "这是一个测试事件",
                type = "普通",
                gregorianDate = "2024-02-15"
            )
        )

        val icsContent = IcsUtils.generateICS(events)

        assertTrue(icsContent.contains("BEGIN:VCALENDAR"))
        assertTrue(icsContent.contains("BEGIN:VEVENT"))
        assertTrue(icsContent.contains("SUMMARY:测试事件"))
        assertTrue(icsContent.contains("DESCRIPTION:这是一个测试事件"))
        assertTrue(icsContent.contains("END:VEVENT"))
        assertTrue(icsContent.contains("END:VCALENDAR"))
    }

    @Test
    fun generateICS_multipleEvents() {
        val events = listOf(
            EventEntity(id = 1, title = "事件1", description = "描述1", type = "普通", gregorianDate = "2024-02-15"),
            EventEntity(id = 2, title = "事件2", description = "描述2", type = "生日", gregorianDate = "2024-02-15"),
            EventEntity(id = 3, title = "事件3", description = "描述3", type = "纪念日", gregorianDate = "2024-02-15")
        )

        val icsContent = IcsUtils.generateICS(events)

        var eventCount = 0
        val lines = icsContent.lines()
        for (line in lines) {
            if (line == "BEGIN:VEVENT") eventCount++
        }
        assertEquals(3, eventCount)
    }

    @Test
    fun parseICS_parsesValidICS() {
        val icsContent = """BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//MiniRili//MiniRili 1.0//CN
CALSCALE:GREGORIAN
METHOD:PUBLISH
BEGIN:VEVENT
UID:1@calendar.app
DTSTAMP:20240215T120000Z
DTSTART:20240215T120000Z
DTEND:20240215T130000Z
SUMMARY:测试事件
DESCRIPTION:这是一个测试
CLASS:PUBLIC
STATUS:CONFIRMED
END:VEVENT
END:VCALENDAR"""

        val events = IcsUtils.parseICS(icsContent)

        assertEquals(1, events.size)
        assertEquals("测试事件", events[0].title)
        assertEquals("这是一个测试", events[0].description)
        assertEquals("2024-02-15", events[0].gregorianDate)
    }

    @Test
    fun parseICS_emptyICS_returnsEmptyList() {
        val events = IcsUtils.parseICS("")
        assertEquals(0, events.size)
    }

    @Test
    fun parseICS_invalidFormat_returnsEmptyList() {
        val events = IcsUtils.parseICS("INVALID ICS CONTENT")
        assertEquals(0, events.size)
    }

    @Test
    fun escapeICS_value_escapesSpecialCharacters() {
        val testCases = listOf(
            "test,comma" to "test\\,comma",
            "test;semicolon" to "test\\;semicolon",
            "test\\backslash" to "test\\\\backslash",
            "test\nnewline" to "test\\nnewline",
            "test\rreturn" to "test\\rreturn",
            "normal text" to "normal text"
        )

        testCases.forEach { (input, expected) ->
            assertEquals(expected, IcsUtils.escapeICSValue(input))
        }
    }

    @Test
    fun formatICSDate_formatsCorrectly() {
        val result = IcsUtils.formatICSDate("2024-02-15")
        assertEquals("20240215", result)
    }

    @Test
    fun formatICSDateTime_formatsCorrectly() {
        val calendar = java.util.Calendar.getInstance().apply {
            set(2024, 1, 15, 14, 30, 0)
        }
        val result = IcsUtils.formatICSDateTime(calendar.timeInMillis)
        assertEquals("20240215T143000Z", result)
    }

    @Test
    fun saveICSToFile_savesToFile() {
        val events = listOf(
            EventEntity(id = 1, title = "测试事件", gregorianDate = "2024-02-15")
        )
        val tempFile = java.io.File(System.getProperty("java.io.tmpdir"), "test_calendar.ics")

        try {
            val result = IcsUtils.saveICSToFile(events, tempFile.absolutePath)
            assertTrue(result)
            assertTrue(tempFile.exists())
            assertTrue(tempFile.length() > 0)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    @Test
    fun loadICSFromFile_loadsFromExistingFile() {
        val events = listOf(
            EventEntity(id = 1, title = "测试事件", gregorianDate = "2024-02-15")
        )
        val tempFile = java.io.File(System.getProperty("java.io.tmpdir"), "test_calendar.ics")

        try {
            IcsUtils.saveICSToFile(events, tempFile.absolutePath)
            val loadedEvents = IcsUtils.loadICSFromFile(tempFile.absolutePath)
            assertEquals(1, loadedEvents.size)
            assertEquals("测试事件", loadedEvents[0].title)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}