package com.minirili.app.utils

import com.minirili.app.database.entity.EventEntity
import com.minirili.app.receivers.AlarmReceiver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OccurrenceSkipTest {

    @Test
    fun noSkipDates_returnsFalse() {
        val ev = EventEntity(id = 1L, title = "t", gregorianDate = "2026-07-11", skipDates = "")
        assertFalse(AlarmReceiver.isOccurrenceSkipped(ev, "2026-07-15"))
    }

    @Test
    fun skipDatesContainsDate_returnsTrue() {
        val ev = EventEntity(id = 1L, title = "t", gregorianDate = "2026-07-11", skipDates = "2026-07-15,2026-07-20")
        assertTrue(AlarmReceiver.isOccurrenceSkipped(ev, "2026-07-15"))
        assertTrue(AlarmReceiver.isOccurrenceSkipped(ev, "2026-07-20"))
    }

    @Test
    fun skipDatesNotContainsDate_returnsFalse() {
        val ev = EventEntity(id = 1L, title = "t", gregorianDate = "2026-07-11", skipDates = "2026-07-15")
        assertFalse(AlarmReceiver.isOccurrenceSkipped(ev, "2026-07-16"))
    }

    @Test
    fun nullEvent_returnsFalse() {
        assertFalse(AlarmReceiver.isOccurrenceSkipped(null, "2026-07-15"))
    }

    @Test
    fun blankEventDate_returnsFalse() {
        val ev = EventEntity(id = 1L, title = "t", gregorianDate = "2026-07-11", skipDates = "2026-07-15")
        assertFalse(AlarmReceiver.isOccurrenceSkipped(ev, ""))
    }

    @Test
    fun matchSurroundingWhitespace_returnsTrue() {
        val ev = EventEntity(id = 1L, title = "t", gregorianDate = "2026-07-11", skipDates = " 2026-07-15 , 2026-07-20 ")
        assertTrue(AlarmReceiver.isOccurrenceSkipped(ev, "2026-07-15"))
        assertTrue(AlarmReceiver.isOccurrenceSkipped(ev, "2026-07-20"))
    }
}
