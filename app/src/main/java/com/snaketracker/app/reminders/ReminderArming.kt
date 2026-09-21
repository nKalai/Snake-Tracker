package com.snaketracker.app.reminders

import android.content.Context
import com.snaketracker.app.data.AppDatabase
import com.snaketracker.app.data.Repository
import java.time.Instant
import java.time.ZoneId

/**
 * The plan→notify→re-arm sequence behind every reminder entry point (platform
 * seams as lambdas, JVM-testable like the arm ladder in ReminderScheduler):
 * posts one notification per due-now snake and re-arms the single next alarm
 * (or cancels everything when there is none).
 */
internal fun notifyDueSnakesAndReschedule(
    candidates: List<ReminderCandidate>,
    plan: ReminderPlan,
    notify: (ReminderCandidate) -> Unit,
    reschedule: (Instant?) -> Unit
) {
    for (candidate in candidates) {
        if (candidate.snakeId in plan.dueSnakeIds) notify(candidate)
    }
    reschedule(plan.nextAlarmAt)
}

/**
 * The frozen-payload counterpart to [notifyDueSnakesAndReschedule], used on
 * the alarm-fire path (issue #37 WB2/WB3): posts one notification per frozen
 * snake straight from the decoded intent extras — no database read in the
 * notification path — and only then runs the re-arm [reschedule] (the fresh
 * snapshot→plan pass that arms the next instant or cancels when there is
 * none). An empty payload posts nothing and still re-arms (issue #37 WB4).
 */
internal suspend fun notifyFrozenPayloadAndReschedule(
    payload: FrozenDuePayload,
    notify: (FrozenDueSnake) -> Unit,
    reschedule: suspend () -> Unit
) {
    for (snake in payload.dueSnakes) {
        notify(snake)
    }
    reschedule()
}

/**
 * The reschedule-only counterpart to [notifyDueSnakesAndReschedule]: moves the
 * single alarm to the plan's [ReminderPlan.nextAlarmAt] (or cancels it when
 * there is none) and posts no due-now notification - by construction this
 * entry has no notify channel, so a bulk data change such as a backup import
 * can never burst a feeding-due notification per overdue snake (issue #28
 * WB5).
 */
internal fun rescheduleToNextAlarm(
    plan: ReminderPlan,
    reschedule: (Instant?) -> Unit
) {
    reschedule(plan.nextAlarmAt)
}

/**
 * Recomputes the reminder plan from the Repository snapshot, posts one
 * notification per due-now snake, and re-arms the single alarm (or cancels
 * everything when there is none). Sharing one sequence between the alarm-fire
 * receiver and the system-event receivers keeps them from drifting: when the
 * armed alarm never fires (device off at 09:00, rebooted at 10:00, or an
 * inexact alarm deferred past its window), the boot / permission re-grant
 * path still posts the due-now notification instead of silently rolling the
 * reminder to the next morning. Used by [AlarmReceiver], [BootReceiver], and
 * [ExactAlarmPermissionReceiver] (the system already cancels the alarm on
 * permission revoke).
 */
object ReminderArming {
    suspend fun refresh(context: Context) {
        val appContext = context.applicationContext
        val (candidates, plan) = snapshotAndPlan(appContext)
        // One payload per arming, frozen from this very plan (issue #37 WB1).
        val frozen = freezeDuePayload(plan, candidates)
        notifyDueSnakesAndReschedule(
            candidates = candidates,
            plan = plan,
            notify = { candidate ->
                NotificationHelper.showFeedingDueNotification(
                    appContext, candidate.snakeId, candidate.name
                )
            },
            reschedule = { _ -> ReminderScheduler.reschedule(appContext, frozen) }
        )
    }

    /**
     * The reschedule-only entry: recomputes the plan from the current data and
     * moves the single alarm through [rescheduleToNextAlarm] without [refresh]
     * 's due-now notification pass. A successful backup import re-arms here
     * (issue #28 WB5) - replacing the data is not a due-time event, so it must
     * not notify; the caller runs this off the main thread.
     */
    suspend fun reschedule(context: Context) {
        val appContext = context.applicationContext
        val (candidates, plan) = snapshotAndPlan(appContext)
        val frozen = freezeDuePayload(plan, candidates)
        rescheduleToNextAlarm(plan) { _ ->
            ReminderScheduler.reschedule(appContext, frozen)
        }
    }

    /**
     * The one snapshot→plan setup both entries consume: the shared
     * Repository's current data, planned against the device clock and zone.
     * Stated once so a clock/zone/snapshot change is a single edit
     * (PR #34 review 🟡); only the notify-vs-reschedule difference stays
     * at the entries.
     */
    private suspend fun snapshotAndPlan(
        appContext: Context
    ): Pair<List<ReminderCandidate>, ReminderPlan> {
        val candidates = Repository.getInstance(AppDatabase.getInstance(appContext))
            .getReminderSnapshot()
        val plan = ReminderPlanner.plan(
            candidates = candidates,
            now = Instant.now(),
            zone = ZoneId.systemDefault()
        )
        return candidates to plan
    }
}
