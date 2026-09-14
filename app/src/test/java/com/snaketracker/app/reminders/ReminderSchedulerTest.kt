package com.snaketracker.app.reminders

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSchedulerTest {

    @Test
    fun beforeAndroid12_usesTheExactApi_withoutAnyPermission() {
        // SCHEDULE_EXACT_ALARM does not exist below API 31; the exact API is free.
        assertTrue(shouldUseExactApi(sdkInt = 26, exactPermissionHeld = false))
        assertTrue(shouldUseExactApi(sdkInt = 30, exactPermissionHeld = false))
    }

    @Test
    fun android12Plus_usesTheExactApi_whenThePermissionIsHeld() {
        assertTrue(shouldUseExactApi(sdkInt = 31, exactPermissionHeld = true))
        assertTrue(shouldUseExactApi(sdkInt = 34, exactPermissionHeld = true))
    }

    @Test
    fun android12Plus_fallsBackToInexact_whenThePermissionIsDenied() {
        assertFalse(shouldUseExactApi(sdkInt = 31, exactPermissionHeld = false))
        assertFalse(shouldUseExactApi(sdkInt = 34, exactPermissionHeld = false))
    }
}
