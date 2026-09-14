package com.snaketracker.app.reminders

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Runs [block], restarting it whenever it fails with anything other than a
 * [CancellationException]: a transient failure at the data boundary (e.g. a
 * SQLite error under the reminder flows) must be logged and retried instead of
 * killing reminder arming for the rest of the process lifetime. [onError]
 * observes each failure; a normal return from [block] ends the loop.
 */
internal suspend fun restartOnFailure(
    onError: (Exception) -> Unit,
    block: suspend () -> Unit
) {
    while (currentCoroutineContext().isActive) {
        try {
            block()
            return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e)
        }
    }
}
