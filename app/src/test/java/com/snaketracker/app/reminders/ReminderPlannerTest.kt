package com.snaketracker.app.reminders

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ReminderPlannerTest {

    // Fixed +02:00 zone so every expectation is a hand-computed literal.
    private val zone: ZoneId = ZoneId.of("GMT+02:00")

    // 2026-09-14 12:00 local. Today's 09:00 local is 07:00Z (already past);
    // the next 09:00 local is 2026-09-15T07:00:00Z.
    private val now: Instant = Instant.parse("2026-09-14T10:00:00Z")

    @Test
    fun neverFedSnake_isDueNow_andNextAlarmIsTomorrowAt0900() {
        val plan = ReminderPlanner.plan(
            candidates = listOf(
                ReminderCandidate(snakeId = 1, name = "Noodle", feedingIntervalDays = 7, lastFeedingAt = null)
            ),
            now = now,
            zone = zone
        )

        assertEquals(setOf(1L), plan.dueSnakeIds)
        assertEquals(Instant.parse("2026-09-15T07:00:00Z"), plan.nextAlarmAt)
    }

    @Test
    fun fedSnakeNotYetDue_isNotDue_andNextAlarmIsItsDueInstant() {
        // Fed 2026-09-13 20:00 local; interval 7 → due 2026-09-20 09:00 local.
        val fedAt = Instant.parse("2026-09-13T18:00:00Z")
        val plan = ReminderPlanner.plan(
            candidates = listOf(
                ReminderCandidate(snakeId = 2, name = "Slinky", feedingIntervalDays = 7, lastFeedingAt = fedAt)
            ),
            now = now,
            zone = zone
        )

        assertEquals(emptySet<Long>(), plan.dueSnakeIds)
        assertEquals(Instant.parse("2026-09-20T07:00:00Z"), plan.nextAlarmAt)
    }

    @Test
    fun dueAtExactlyNow_isDue_andNextAlarmRollsToTomorrowAt0900() {
        // Fed 2026-09-07; interval 7 → due exactly 2026-09-14 09:00 local = now.
        val fedAt = Instant.parse("2026-09-07T08:00:00Z")
        val atBoundary = Instant.parse("2026-09-14T07:00:00Z")
        val plan = ReminderPlanner.plan(
            candidates = listOf(
                ReminderCandidate(snakeId = 3, name = "Boa", feedingIntervalDays = 7, lastFeedingAt = fedAt)
            ),
            now = atBoundary,
            zone = zone
        )

        assertEquals(setOf(3L), plan.dueSnakeIds)
        assertEquals(Instant.parse("2026-09-15T07:00:00Z"), plan.nextAlarmAt)
    }

    @Test
    fun overdueSnake_isDueNow_andNextAlarmIsTomorrowAt0900() {
        // Due 2026-09-08 09:00 local, still unfed at 2026-09-14 12:00 local.
        val fedAt = Instant.parse("2026-09-01T12:00:00Z")
        val plan = ReminderPlanner.plan(
            candidates = listOf(
                ReminderCandidate(snakeId = 4, name = "Old Due", feedingIntervalDays = 7, lastFeedingAt = fedAt)
            ),
            now = now,
            zone = zone
        )

        assertEquals(setOf(4L), plan.dueSnakeIds)
        assertEquals(Instant.parse("2026-09-15T07:00:00Z"), plan.nextAlarmAt)
    }

    @Test
    fun overdueSnake_beforeTodayReminder_getsTodayAt0900AsNextAlarm() {
        // Now is 2026-09-14 08:00 local, before today's 09:00 reminder.
        val fedAt = Instant.parse("2026-09-01T12:00:00Z")
        val morningNow = Instant.parse("2026-09-14T06:00:00Z")
        val plan = ReminderPlanner.plan(
            candidates = listOf(
                ReminderCandidate(snakeId = 4, name = "Old Due", feedingIntervalDays = 7, lastFeedingAt = fedAt)
            ),
            now = morningNow,
            zone = zone
        )

        assertEquals(setOf(4L), plan.dueSnakeIds)
        assertEquals(Instant.parse("2026-09-14T07:00:00Z"), plan.nextAlarmAt)
    }

    @Test
    fun multipleSnakes_nextAlarmIsTheEarliestCandidateInstant() {
        val dueSep20 = ReminderCandidate(
            snakeId = 5, name = "Late", feedingIntervalDays = 7,
            lastFeedingAt = Instant.parse("2026-09-13T18:00:00Z") // due 2026-09-20 09:00 local
        )
        val dueSep16 = ReminderCandidate(
            snakeId = 6, name = "Soon", feedingIntervalDays = 7,
            lastFeedingAt = Instant.parse("2026-09-09T18:00:00Z") // due 2026-09-16 09:00 local
        )
        val plan = ReminderPlanner.plan(listOf(dueSep20, dueSep16), now, zone)

        assertEquals(emptySet<Long>(), plan.dueSnakeIds)
        assertEquals(Instant.parse("2026-09-16T07:00:00Z"), plan.nextAlarmAt)
    }

    @Test
    fun mixedOverdueAndFutureSnakes_nextAlarmIsEarliestAcrossBoth() {
        val overdue = ReminderCandidate(
            snakeId = 4, name = "Old Due", feedingIntervalDays = 7,
            lastFeedingAt = Instant.parse("2026-09-01T12:00:00Z")
        )
        val future = ReminderCandidate(
            snakeId = 6, name = "Soon", feedingIntervalDays = 7,
            lastFeedingAt = Instant.parse("2026-09-09T18:00:00Z") // due 2026-09-16 09:00 local
        )
        val plan = ReminderPlanner.plan(listOf(overdue, future), now, zone)

        assertEquals(setOf(4L), plan.dueSnakeIds)
        // Overdue rolls to tomorrow 09:00 (2026-09-15), which beats 2026-09-16.
        assertEquals(Instant.parse("2026-09-15T07:00:00Z"), plan.nextAlarmAt)
    }

    @Test
    fun noCandidates_yieldsEmptyPlanWithNoAlarm() {
        val plan = ReminderPlanner.plan(emptyList(), now, zone)

        assertEquals(emptySet<Long>(), plan.dueSnakeIds)
        assertEquals(null, plan.nextAlarmAt)
    }

    @Test
    fun dueSnake_afterInexactDrift_isStillDue_andNextAlarmRollsToTomorrow() {
        // Fed 2026-09-07 12:00 local; interval 7 → due 2026-09-14 09:00 local
        // (= 07:00Z). An inexact fallback alarm fires ~15 minutes late, so
        // now is dueAt + 15min: the snake is still due now and the next alarm
        // rolls to tomorrow's 09:00 local — the "~15-minute tolerance, always
        // the correct day" contract at its boundary.
        val fedAt = Instant.parse("2026-09-07T10:00:00Z")
        val driftedNow = Instant.parse("2026-09-14T07:15:00Z")
        val plan = ReminderPlanner.plan(
            candidates = listOf(
                ReminderCandidate(snakeId = 9, name = "Drift", feedingIntervalDays = 7, lastFeedingAt = fedAt)
            ),
            now = driftedNow,
            zone = zone
        )

        assertEquals(setOf(9L), plan.dueSnakeIds)
        assertEquals(Instant.parse("2026-09-15T07:00:00Z"), plan.nextAlarmAt)
    }

    @Test
    fun dueInstant_landsAt0900Local_whenIntervalCrossesADstTransition() {
        // Fed 2026-03-07T12:00:00Z = 2026-03-07 07:00 EST (UTC-5) in New York,
        // so the local feeding date is Mar 7. DST starts Mar 8; interval 7 lands
        // on Mar 14 in EDT (UTC-4), where 09:00 local is 13:00Z. A naive fixed
        // 24h/day arithmetic would aim at 12:00Z (08:00 EDT) and fail this test.
        val nyZone = ZoneId.of("America/New_York")
        val fedAt = Instant.parse("2026-03-07T12:00:00Z")
        val plan = ReminderPlanner.plan(
            candidates = listOf(
                ReminderCandidate(snakeId = 8, name = "Kaa", feedingIntervalDays = 7, lastFeedingAt = fedAt)
            ),
            now = Instant.parse("2026-03-10T12:00:00Z"),
            zone = nyZone
        )

        assertEquals(emptySet<Long>(), plan.dueSnakeIds)
        assertEquals(Instant.parse("2026-03-14T13:00:00Z"), plan.nextAlarmAt)
    }

    @Test
    fun utcMidnightFeedingTimestamp_usesLocalCalendarDateInNonUtcZone() {
        // Stored feeding instant is UTC midnight 2026-01-10, which is already
        // 2026-01-09 19:00 in the fixed -05:00 zone — so the local feeding date
        // is Jan 9, and interval 7 lands on 2026-01-16 09:00 local (14:00Z).
        // A UTC-date implementation would wrongly aim at 2026-01-17.
        val fedAtUtcMidnight = Instant.parse("2026-01-10T00:00:00Z")
        val nonUtcZone = ZoneId.of("GMT-05:00")
        val plan = ReminderPlanner.plan(
            candidates = listOf(
                ReminderCandidate(snakeId = 7, name = "Zebra", feedingIntervalDays = 7, lastFeedingAt = fedAtUtcMidnight)
            ),
            now = Instant.parse("2026-01-12T00:00:00Z"),
            zone = nonUtcZone
        )

        assertEquals(emptySet<Long>(), plan.dueSnakeIds)
        assertEquals(Instant.parse("2026-01-16T14:00:00Z"), plan.nextAlarmAt)
    }
}
