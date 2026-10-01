package com.magpiepodcast.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun formatDate_convertsIsoUtcToBrazilianFormat() {
        assertEquals("29/09/2025", Formatters.formatDate("2025-09-29T12:00:00.000Z"))
    }

    @Test
    fun formatDate_fallsBackToFirstTenCharsWhenUnparseable() {
        assertEquals("2025-09-29", Formatters.formatDate("2025-09-29 garbage"))
        assertEquals("abc", Formatters.formatDate("abc"))
    }

    @Test
    fun formatMs_usesMinutesAndSecondsBelowOneHour() {
        assertEquals("0:00", Formatters.formatMs(0))
        assertEquals("1:05", Formatters.formatMs(65_000))
        assertEquals("59:59", Formatters.formatMs(3_599_999))
    }

    @Test
    fun formatMs_includesHoursWhenNeeded() {
        assertEquals("1:00:00", Formatters.formatMs(3_600_000))
        assertEquals("2:02:34", Formatters.formatMs(7_354_000))
    }

    @Test
    fun formatMs_clampsNegativeToZero() {
        assertEquals("0:00", Formatters.formatMs(-5_000))
    }

    @Test
    fun parseDurationLabelToMs_handlesMinutesSeconds() {
        assertEquals((122L * 60 + 34) * 1000, Formatters.parseDurationLabelToMs("122:34"))
    }

    @Test
    fun parseDurationLabelToMs_handlesHoursMinutesSeconds() {
        assertEquals((3600L + 22 * 60 + 34) * 1000, Formatters.parseDurationLabelToMs("1:22:34"))
    }

    @Test
    fun parseDurationLabelToMs_handlesSecondsOnly() {
        assertEquals(45_000L, Formatters.parseDurationLabelToMs("45"))
    }

    @Test
    fun parseDurationLabelToMs_returnsZeroForInvalid() {
        assertEquals(0L, Formatters.parseDurationLabelToMs(""))
        assertEquals(0L, Formatters.parseDurationLabelToMs("abc"))
        assertEquals(0L, Formatters.parseDurationLabelToMs("1:2:3:4"))
    }
}
