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
        val repository = Repository.getInstance(AppDatabase.getInstance(appContext))
        val candidates = repository.getReminderSnapshot()
        val plan = ReminderPlanner.plan(
            candidates = candidates,
            now = Instant.now(),
            zone = ZoneId.systemDefault()
        )
        notifyDueSnakesAndReschedule(
            candidates = candidates,
            plan = plan,
            notify = { candidate ->
                NotificationHelper.showFeedingDueNotification(
                    appContext, candidate.snakeId, candidate.name
                )
            },
            reschedule = { at -> ReminderScheduler.reschedule(appContext, at) }
        )
    }
}
