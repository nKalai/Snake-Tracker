package com.snaketracker.app.data

import com.snaketracker.app.data.dao.LastFeedingInfo
import com.snaketracker.app.data.entities.Snake
import com.snaketracker.app.reminders.ReminderCandidate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ReminderSnapshotTest {

    @Test
    fun snapshot_pairsEachSnakeWithItsLastFeedingInstant() {
        val snakes = listOf(
            Snake(id = 1, name = "Noodle", feedingIntervalDays = 7),
            Snake(id = 2, name = "Never-fed", feedingIntervalDays = 14)
        )
        val lastFeedings = listOf(LastFeedingInfo(snakeId = 1, lastDate = 1_700_000_000_000))

        val snapshot = buildReminderCandidates(snakes, lastFeedings)

        assertEquals(
            listOf(
                ReminderCandidate(
                    snakeId = 1, name = "Noodle", feedingIntervalDays = 7,
                    lastFeedingAt = Instant.ofEpochMilli(1_700_000_000_000)
                ),
                ReminderCandidate(
                    snakeId = 2, name = "Never-fed", feedingIntervalDays = 14,
                    lastFeedingAt = null
                )
            ),
            snapshot
        )
    }

    @Test
    fun snapshot_ignoresFeedingRowsForSnakesOutsideTheSnapshot() {
        val snakes = listOf(Snake(id = 1, name = "Noodle", feedingIntervalDays = 7))
        val lastFeedings = listOf(
            LastFeedingInfo(snakeId = 99, lastDate = 1_700_000_000_000),
            LastFeedingInfo(snakeId = 1, lastDate = 1_800_000_000_000)
        )

        val snapshot = buildReminderCandidates(snakes, lastFeedings)

        assertEquals(1, snapshot.size)
        assertEquals(Instant.ofEpochMilli(1_800_000_000_000), snapshot.single().lastFeedingAt)
    }
}
