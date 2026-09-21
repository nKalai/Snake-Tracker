package com.snaketracker.app.reminders

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * The pure encode/decode seam between the due-now plan and the flat intent
 * extras map (issue #37 WB1/WB4): driven with plain maps, no Android types.
 */
class DuePayloadTest {

    // Fixed +02:00 zone so every expectation is a hand-computed literal.
    private val zone: ZoneId = ZoneId.of("GMT+02:00")

    // 2026-09-14 12:00 local — today's 09:00 reminder has already passed.
    private val now: Instant = Instant.parse("2026-09-14T10:00:00Z")

    // Overdue snake (id 1) plus a not-yet-due snake (id 2) and a snake that
    // rolls over tomorrow 09:00 local (id 3): the plan freezes ids 1 and 3.
    private val candidates = listOf(
        ReminderCandidate(1, "Noodle", 7, Instant.parse("2026-09-01T12:00:00Z")),
        ReminderCandidate(2, "Future", 7, Instant.parse("2026-09-13T18:00:00Z")),
        ReminderCandidate(3, "Coil", 0, null)
    )

    @Test
    fun freeze_takesEveryDueSnakeIdAndNameInCandidateOrder_plusTheNextInstant() {
        val plan = ReminderPlanner.plan(candidates, now, zone)

        val payload = freezeDuePayload(plan, candidates)

        // Noodle (overdue) and Coil (never fed) are due now; Future is not.
        // Overdue at 12:00 local with today's 09:00 passed → next 09:00 tomorrow.
        assertEquals(
            FrozenDuePayload(
                dueSnakes = listOf(FrozenDueSnake(1, "Noodle"), FrozenDueSnake(3, "Coil")),
                nextAlarmAt = Instant.parse("2026-09-15T07:00:00Z")
            ),
            payload
        )
    }

    @Test
    fun encodeDecodeRoundTrip_preservesEveryDueSnakeIdAndName_plusTheNextInstant() {
        val plan = ReminderPlanner.plan(candidates, now, zone)

        val decoded = decodeDuePayload(encodeDuePayload(freezeDuePayload(plan, candidates)))

        assertEquals(
            listOf(FrozenDueSnake(1, "Noodle"), FrozenDueSnake(3, "Coil")),
            decoded.dueSnakes
        )
        assertEquals(Instant.parse("2026-09-15T07:00:00Z"), decoded.nextAlarmAt)
    }

    @Test
    fun emptyPlan_encodesAndDecodesToNoSnakes_andNoNextInstant() {
        val decoded = decodeDuePayload(
            encodeDuePayload(freezeDuePayload(ReminderPlan(emptySet(), null), emptyList()))
        )

        assertEquals(emptyList<FrozenDueSnake>(), decoded.dueSnakes)
        assertEquals(null, decoded.nextAlarmAt)
    }

    @Test
    fun missingExtras_decodeToNoSnakesAndNoNextInstant() {
        // A bare intent (no frozen payload) must still decode cleanly so the
        // receiver can post nothing and re-arm (issue #37 WB4).
        assertEquals(FrozenDuePayload(emptyList(), null), decodeDuePayload(emptyMap()))
    }

    @Test
    fun nextInstantDecodes_withoutAnySnakes_whenOnlyTheInstantWasFrozen() {
        val extras = encodeDuePayload(
            FrozenDuePayload(emptyList(), Instant.parse("2026-09-16T07:00:00Z"))
        )

        assertEquals(FrozenDuePayload(emptyList(), Instant.parse("2026-09-16T07:00:00Z")), decodeDuePayload(extras))
    }
}
