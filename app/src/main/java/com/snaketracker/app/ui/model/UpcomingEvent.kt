package com.snaketracker.app.ui.model

data class UpcomingEvent(
    val snakeId: Long,
    val snakeName: String,
    val dueDateMillis: Long,
    val isOverdue: Boolean
)
