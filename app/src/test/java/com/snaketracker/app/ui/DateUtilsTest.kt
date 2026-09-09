package com.snaketracker.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DateUtilsTest {

    @Test
    fun daysAgo_returnsWholeDaysElapsed() {
        val now = System.currentTimeMillis()
        val dayMillis = 24L * 60 * 60 * 1000

        // A timestamp 2 days in the past reports "2 days ago" (integer days).
        assertEquals(2L, DateUtils.daysAgo(now - 2 * dayMillis))
        // A timestamp right now reports "0 days ago".
        assertEquals(0L, DateUtils.daysAgo(now))
    }

    @Test
    fun daysAgo_returnsZeroForSubDayElapsed() {
        val now = System.currentTimeMillis()
        val halfDayMillis = 12L * 60 * 60 * 1000

        // A timestamp less than one full day in the past reports "0 days ago":
        // integer division floors, so a value that has not fully elapsed a day
        // cannot regress into 1.
        assertEquals(0L, DateUtils.daysAgo(now - halfDayMillis))
    }
}
