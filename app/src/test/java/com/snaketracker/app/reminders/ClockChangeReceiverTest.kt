package com.snaketracker.app.reminders

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Issue #39: a manual clock edit ([Intent.ACTION_TIME_CHANGED]) or a zone
 * switch ([Intent.ACTION_TIMEZONE_CHANGED]) must push the single armed instant
 * through the same plan→notify→re-arm funnel the boot and permission receivers
 * use, so the reminder follows the new local time. [handleClockChangeIntent]
 * is the receiver's pure action-guard half: one matched action calls the
 * shared refresh exactly once, anything else is ignored.
 */
class ClockChangeReceiverTest {

    @Test
    fun timeChanged_callsTheSharedRefresh_exactlyOnce() {
        var refreshCalls = 0
        handleClockChangeIntent(Intent.ACTION_TIME_CHANGED) { refreshCalls += 1 }
        assertEquals(1, refreshCalls)
    }

    @Test
    fun timezoneChanged_callsTheSharedRefresh_exactlyOnce() {
        var refreshCalls = 0
        handleClockChangeIntent(Intent.ACTION_TIMEZONE_CHANGED) { refreshCalls += 1 }
        assertEquals(1, refreshCalls)
    }

    @Test
    fun eachMatchedActionOnTheSameReceiver_isHandledOncePerIntent() {
        var refreshCalls = 0
        handleClockChangeIntent(Intent.ACTION_TIME_CHANGED) { refreshCalls += 1 }
        handleClockChangeIntent(Intent.ACTION_TIMEZONE_CHANGED) { refreshCalls += 1 }
        assertEquals(2, refreshCalls)
    }

    @Test
    fun unrelatedAction_isIgnored_andNeverReachesTheRefresh() {
        var refreshCalls = 0
        handleClockChangeIntent(Intent.ACTION_EDIT) { refreshCalls += 1 }
        assertEquals(0, refreshCalls)
    }

    @Test
    fun nullAction_isIgnored() {
        var refreshCalls = 0
        handleClockChangeIntent(null) { refreshCalls += 1 }
        assertEquals(0, refreshCalls)
    }
}
