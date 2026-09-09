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
}
