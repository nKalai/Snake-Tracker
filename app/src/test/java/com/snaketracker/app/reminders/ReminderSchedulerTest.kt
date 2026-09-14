package com.snaketracker.app.reminders

import org.junit.Assert.assertEquals
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

    @Test
    fun armViaLadder_degradesToTheInexactApi_whenTheExactSetThrowsSecurityException() {
        val calls = mutableListOf<String>()

        armViaPermissionLadder(
            sdkInt = 34,
            exactPermissionHeld = true,
            setExact = { calls.add("exact"); throw SecurityException("permission revoked mid-flight") },
            setInexact = { calls.add("inexact") }
        )

        // The revocation race must degrade to the inexact fallback, not crash:
        // the reminder still lands, ~15 min of tolerance on the correct day.
        assertEquals(listOf("exact", "inexact"), calls)
    }

    @Test
    fun armViaLadder_stopsAtTheExactApi_whenTheExactSetSucceeds() {
        val calls = mutableListOf<String>()

        armViaPermissionLadder(
            sdkInt = 34,
            exactPermissionHeld = true,
            setExact = { calls.add("exact") },
            setInexact = { calls.add("inexact") }
        )

        assertEquals(listOf("exact"), calls)
    }

    @Test
    fun armViaLadder_skipsTheExactApi_onAndroid12PlusWhenThePermissionIsDenied() {
        val calls = mutableListOf<String>()

        armViaPermissionLadder(
            sdkInt = 31,
            exactPermissionHeld = false,
            setExact = { calls.add("exact") },
            setInexact = { calls.add("inexact") }
        )

        assertEquals(listOf("inexact"), calls)
    }
}
