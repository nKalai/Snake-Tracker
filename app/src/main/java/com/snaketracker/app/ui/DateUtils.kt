package com.snaketracker.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtils {
    private val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun format(millis: Long): String = displayFormat.format(Date(millis))

    fun daysAgo(millis: Long): Long {
        val diff = System.currentTimeMillis() - millis
        return diff / (1000 * 60 * 60 * 24)
    }
}
