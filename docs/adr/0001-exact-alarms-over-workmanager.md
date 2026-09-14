# ADR-0001: Exact alarms over WorkManager for feeding reminders

- **Status:** Accepted (2026-09-14)
- **Deciders:** Snake Tracker maintainers
- **Supersedes:** none (first ADR in this repo)

## Context

Feeding reminders must fire at the right time for each snake: a snake's due
instant is the calendar date of its last feeding plus its interval, normalized
to 09:00 local time (see `reminders/ReminderPlanner.kt`, the pure due-time
engine). The original implementation polled with a daily WorkManager periodic
job, which has two problems:

- **Latency:** a periodic worker runs "roughly once a day" at an OS-chosen
  time, so a due feeding could be flagged many hours late.
- **Weight:** WorkManager was kept as a dependency for a single periodic job —
  the poll was its only consumer — adding startup work and APK size for
  scheduling that one alarm-style need.

Users also expect reminders to land on the correct day even under Doze, which
a daily inexact poll does not guarantee.

## Decision

Replace the daily WorkManager poll with **one armed exact alarm** for the due-time
engine's next instant:

1. **Single next-due alarm.** On app start and whenever snake/feeding data
   changes, the app recomputes the plan (snakes + last-feeding-per-snake flows
   → `ReminderPlanner.plan`) and arms exactly one `AlarmManager` alarm for
   `ReminderPlan.nextAlarmAt`, re-arming only when that instant changes, and
   cancelling everything when it is null. The alarm fires into
   `reminders/AlarmReceiver`, which posts one notification per due-now snake
   and re-arms the next instant.
2. **Permission ladder (not `setAlarmClock`, not `USE_EXACT_ALARM`).**
   - Below Android 12: schedule with the exact-while-idle API
     (`setExactAndAllowWhileIdle`) — no permission needed.
   - Android 12+ with `SCHEDULE_EXACT_ALARM` held: exact-while-idle.
   - Android 12+ without the permission: fall back to the permission-free
     inexact-while-idle API (`setAndAllowWhileIdle`, ~15-minute tolerance) —
     the reminder still lands on the correct day.
   - A revocation race between the permission check and the set call is caught
     (`SecurityException`) and degrades to the inexact fallback; it never
     crashes.
3. **Re-arming triggers:** app start, any data change (feeding logged/deleted,
   interval edited, reminders toggled, snake added — observed via the existing
   flows), alarm fire, device reboot (`BootReceiver`), and exact-alarm
   permission re-grant (`ExactAlarmPermissionReceiver`; the system already
   cancels alarms on revoke).
4. **WorkManager is removed entirely** — dependency, worker, and scheduling
   call sites. The poll had no other consumer.

## Consequences

- Reminders fire at (or within seconds of) the planned 09:00 local instant when
  the exact permission is granted, and within ~15 minutes otherwise.
- The app depends only on the platform alarm APIs; no background-scheduling
  library remains.
- The user can revoke exact alarms in system settings (Android 12+); the app
  degrades silently and re-arms exactly when permission returns.
- `PendingIntents` are immutable; the receiver is not exported.
- Adding new reminder kinds means extending `ReminderPlanner` and the snapshot;
  the single-alarm arming model stays unchanged.

## Alternatives considered

- **Keep the WorkManager daily poll:** rejected — latency up to hours and a
  dependency kept for a single consumer.
- **`USE_EXACT_ALARM`:** rejected — it is a Play-Policy-restricted permission
  for alarm-clock/calendar core-function apps, which this tracker is not.
- **`setAlarmClock`:** rejected — it shows a system alarm-clock indicator and
  implies user-facing alarm semantics; reminders are not alarms.
