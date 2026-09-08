package com.snaketracker.app.ui

import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale

object CalendarUtils {
    /**
     * Returns a flat list of cells (7 per row) for [month], padded with nulls
     * before the 1st and after the last day so the grid always lines up under
     * fixed weekday headers.
     */
    fun daysGridFor(month: YearMonth): List<java.time.LocalDate?> {
        val firstOfMonth = month.atDay(1)
        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val offset = (firstOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7

        val cells = mutableListOf<java.time.LocalDate?>()
        repeat(offset) { cells.add(null) }
        for (day in 1..month.lengthOfMonth()) cells.add(month.atDay(day))
        while (cells.size % 7 != 0) cells.add(null)
        return cells
    }

    fun weekdayLabels(): List<String> {
        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        return (0..6).map { offset ->
            firstDayOfWeek.plus(offset.toLong())
                .getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
        }
    }
}
