package com.snaketracker.app.reminders

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ReminderArmingTest {

    // Fixed +02:00 zone so every expectation is a hand-computed literal.
    private val zone: ZoneId = ZoneId.of("GMT+02:00")

    // 2026-09-14 12:00 local — today's 09:00 reminder has already passed.
    private val now: Instant = Instant.parse("2026-09-14T10:00:00Z")

    @Test
    fun dueNowSnakes_areNotified_onceEach_andNotDueOnesAreSkipped() {
        val candidates = listOf(
            // Fed 2026-09-01; interval 7 → due 2026-09-08 09:00 local, overdue now.
            ReminderCandidate(1, "Due", 7, Instant.parse("2026-09-01T12:00:00Z")),
            // Fed 2026-09-13 20:00 local; interval 7 → due 2026-09-20 09:00 local.
            ReminderCandidate(2, "Future", 7, Instant.parse("2026-09-13T18:00:00Z"))
        )
        val plan = ReminderPlanner.plan(candidates, now, zone)
        val notified = mutableListOf<Long>()

        notifyDueSnakesAndReschedule(
            candidates = candidates,
            plan = plan,
            notify = { notified.add(it.snakeId) },
            reschedule = { }
        )

        assertEquals(listOf(1L), notified)
    }

    @Test
    fun rescheduleReceivesThePlansNextAlarmInstant_soTheAlarmFollowsThePlan() {
        val candidates = listOf(
            // Fed 2026-09-09 20:00 local; interval 7 → due 2026-09-16 09:00 local.
            ReminderCandidate(3, "Soon", 7, Instant.parse("2026-09-09T18:00:00Z"))
        )
        val plan = ReminderPlanner.plan(candidates, now, zone)
        var rescheduledAt: Instant? = Instant.EPOCH

        notifyDueSnakesAndReschedule(
            candidates = candidates,
            plan = plan,
            notify = { },
            reschedule = { rescheduledAt = it }
        )

        assertEquals(Instant.parse("2026-09-16T07:00:00Z"), rescheduledAt)
    }

    @Test
    fun nothingToRemind_reschedulesNull_soEveryAlarmIsCancelled() {
        var rescheduledAt: Instant? = Instant.EPOCH

        notifyDueSnakesAndReschedule(
            candidates = emptyList(),
            plan = ReminderPlan(emptySet(), null),
            notify = { },
            reschedule = { rescheduledAt = it }
        )

        assertEquals(null, rescheduledAt)
    }
}
