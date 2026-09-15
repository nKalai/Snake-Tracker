package com.snaketracker.app.ui.screens

import java.time.LocalDate
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The SAF picker's suggested name (issue #26 WB1): an ISO-dated literal for
 * a fixed date, pinned independent of device locale - LocalDate.toString is
 * proleptic ISO `yyyy-MM-dd` by contract, so even locales whose calendars
 * render years differently must produce this exact string.
 */
class BackupFileNameTest {

    private val defaultLocale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun `backupFileName is the iso-stamped literal for a fixed date`() {
        assertEquals(
            "snake-tracker-backup-2026-04-01.json",
            backupFileName(LocalDate.of(2026, 4, 1))
        )
    }

    @Test
    fun `backupFileName ignores locale`() {
        for (tag in listOf("en-US", "de-DE", "th-TH", "ar-SA")) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            assertEquals(
                "snake-tracker-backup-2026-04-01.json",
                backupFileName(LocalDate.of(2026, 4, 1))
            )
        }
    }
}
