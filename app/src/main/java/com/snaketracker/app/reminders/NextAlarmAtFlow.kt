package com.snaketracker.app.reminders

import com.snaketracker.app.data.buildReminderCandidates
import com.snaketracker.app.data.dao.LastFeedingInfo
import com.snaketracker.app.data.entities.Snake
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.ZoneId

/**
 * The reminder-arming stream: observes the existing snakes and last-feeding-per-snake
 * flows and emits the due-time engine's next alarm instant (null when nothing needs
 * arming). Consecutive duplicates are suppressed, so a data change only re-arms when
 * the computed instant actually changes.
 */
internal fun nextAlarmAtFlow(
    snakes: Flow<List<Snake>>,
    lastFeedings: Flow<List<LastFeedingInfo>>,
    now: () -> Instant,
    zone: ZoneId
): Flow<Instant?> =
    combine(snakes, lastFeedings) { snakeList, feedingList ->
        ReminderPlanner.plan(
            candidates = buildReminderCandidates(snakeList.filter { it.remindersEnabled }, feedingList),
            now = now(),
            zone = zone
        ).nextAlarmAt
    }.distinctUntilChanged()
