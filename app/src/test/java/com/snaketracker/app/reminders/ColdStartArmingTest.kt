package com.snaketracker.app.reminders

import com.snaketracker.app.data.dao.LastFeedingInfo
import com.snaketracker.app.data.entities.Snake
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Issue #40: the launch-time observer runs the full notify + re-arm pass, so a
 * relaunch after a missed alarm shows the due snakes right away, then keeps
 * following the next-instant stream without stacking duplicate arms. The
 * recorders mirror the production seams exactly as [com.snaketracker.app.SnakeTrackerApp]
 * hands them: notify = NotificationHelper, reschedule = ReminderScheduler.
 */
class ColdStartArmingTest {

    // Fixed +02:00 zone so every expectation is a hand-computed literal.
    private val zone: ZoneId = ZoneId.of("GMT+02:00")

    // 2026-09-14 12:00 local — today's 09:00 reminder has already passed; the
    // next armed instant is 2026-09-15T07:00:00Z.
    private val now: Instant = Instant.parse("2026-09-14T10:00:00Z")

    // Epoch millis for a local wall-clock time in the fixed zone.
    private fun epochMillisOf(localDateTime: String): Long =
        ZonedDateTime.of(LocalDateTime.parse(localDateTime), zone).toInstant().toEpochMilli()

    /**
     * WB1: on a cold start the first pass posts every snake already due at
     * launch — overdue Noodle (fed 09-01, interval 7) and never-fed Coil —
     * and runs the arm exactly once for the following instant. "Future", due
     * 09-20, is not posted.
     */
    @Test
    fun firstLaunchPass_notifiesEveryDueSnake_thenArmsTheNextInstant_once() = runBlocking {
        val snakes = listOf(
            Snake(id = 1, name = "Noodle", feedingIntervalDays = 7),
            Snake(id = 2, name = "Coil", feedingIntervalDays = 7),
            Snake(id = 3, name = "Future", feedingIntervalDays = 7)
        )
        val feedings = listOf(
            LastFeedingInfo(snakeId = 1, lastDate = epochMillisOf("2026-09-01T20:00")),
            // Coil (id 2) has no row: never fed, so it is due immediately.
            LastFeedingInfo(snakeId = 3, lastDate = epochMillisOf("2026-09-13T20:00"))
        )
        val calls = mutableListOf<String>()

        launchArmingPass(
            payloadFlow = nextAlarmAtFlow(
                snakes = flow { emit(snakes) },
                lastFeedings = flow { emit(feedings) },
                now = { now },
                zone = zone
            ),
            notify = { snake -> calls.add("notify:${snake.snakeId}") },
            reschedule = { payload -> calls.add("arm:${payload.nextAlarmAt}") }
        )

        // Both due snakes posted in candidate order, then one arm — the
        // instant is the plan's, not any snake's stale one.
        assertEquals(listOf("notify:1", "notify:2", "arm:2026-09-15T07:00:00Z"), calls)
    }

    /**
     * WB2: after the first pass the observer follows the next-instant stream.
     * A data change whose frozen payload is identical (an older feeding row
     * beside the same latest one) is suppressed upstream, so the recorder
     * shows exactly one notify and one arm despite two upstream emissions.
     */
    @Test
    fun dataChange_thatDoesNotMoveTheInstant_causesNoSecondArm() = runBlocking {
        val snake = Snake(id = 1, name = "Noodle", feedingIntervalDays = 3)
        val latestOnly = listOf(LastFeedingInfo(snakeId = 1, lastDate = epochMillisOf("2026-09-12T20:00")))
        val latestPlusOlder =
            listOf(LastFeedingInfo(snakeId = 1, lastDate = epochMillisOf("2026-09-01T20:00"))) + latestOnly
        val calls = mutableListOf<String>()

        launchArmingPass(
            payloadFlow = nextAlarmAtFlow(
                snakes = flow { emit(listOf(snake)) },
                lastFeedings = flow { emit(latestOnly); emit(latestPlusOlder) },
                now = { now },
                zone = zone
            ),
            notify = { snake -> calls.add("notify:${snake.snakeId}") },
            reschedule = { payload -> calls.add("arm:${payload.nextAlarmAt}") }
        )

        // One pass total: the duplicate payload never re-notifies and never
        // re-arms, so the shade and the alarm stay single-valued.
        assertEquals(listOf("notify:1", "arm:2026-09-15T07:00:00Z"), calls)
    }

    /**
     * WB4 (with #28 WB5): the reschedule-only entry — the backup-import hook's
     * shape — hands the payload straight to the scheduler seam: the alarm
     * moves to the plan's instant, and no notification posts at all.
     */
    @Test
    fun rescheduleOnlyEntry_movesTheAlarm_andPostsNothing() = runBlocking {
        val snake = Snake(id = 1, name = "Noodle", feedingIntervalDays = 3)
        val feedings = listOf(LastFeedingInfo(snakeId = 1, lastDate = epochMillisOf("2026-09-12T20:00")))
        val calls = mutableListOf<String>()

        launchArmingPass(
            payloadFlow = nextAlarmAtFlow(
                snakes = flow { emit(listOf(snake)) },
                lastFeedings = flow { emit(feedings) },
                now = { now },
                zone = zone
            ),
            notify = null,
            reschedule = { payload -> calls.add("arm:${payload.nextAlarmAt}") }
        )

        assertEquals(listOf("arm:2026-09-15T07:00:00Z"), calls)
    }
}
