package com.snaketracker.app.reminders

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// Rate-limits the restart loop: the WorkManager poll this replaces had
// OS-managed backoff, so a persistently failing block (corrupt database,
// SQLiteFullException) must not re-fail as fast as the dispatcher allows.
private const val RESTART_BACKOFF_INITIAL_MS = 1_000L
private const val RESTART_BACKOFF_MAX_MS = 60_000L

/**
 * Runs [block], restarting it whenever it fails with anything other than a
 * [CancellationException]: a transient failure at the data boundary (e.g. a
 * SQLite error under the reminder flows) must be logged and retried instead of
 * killing reminder arming for the rest of the process lifetime. [onError]
 * observes each failure; each retry waits an exponentially growing delay
 * (1s doubling up to 60s) so a persistent failure cannot spin the loop; a
 * normal return from [block] ends the loop.
 */
internal suspend fun restartOnFailure(
    onError: (Exception) -> Unit,
    block: suspend () -> Unit
) {
    var backoffMs = RESTART_BACKOFF_INITIAL_MS
    while (currentCoroutineContext().isActive) {
        try {
            block()
            return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e)
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(RESTART_BACKOFF_MAX_MS)
        }
    }
}
