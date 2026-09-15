package com.snaketracker.app.reminders

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestartOnFailureTest {

    @Test
    fun restartsTheBlock_afterAFailure_soTransientDataErrorsDoNotKillArming() = runTest {
        var attempts = 0
        val collected = mutableListOf<Int>()
        val failures = mutableListOf<Exception>()

        restartOnFailure(onError = { failures.add(it) }) {
            flow {
                emit(1)
                if (attempts++ == 0) throw RuntimeException("transient sqlite failure")
                emit(2)
            }.collect { collected.add(it) }
        }

        // The failing first attempt is observed, then the whole block runs again
        // from scratch — arming logic keeps following data changes.
        assertEquals(listOf(1, 1, 2), collected)
        assertEquals(1, failures.size)
    }

    @Test
    fun letsCancellationPropagate_soScopeShutdownStillStopsTheCollector() = runBlocking {
        val failures = mutableListOf<Exception>()

        try {
            restartOnFailure(onError = { failures.add(it) }) {
                throw CancellationException("scope cancelled")
            }
        } catch (_: CancellationException) {
            // expected: cancellation must not be swallowed into a restart loop
        }

        assertEquals(emptyList<Exception>(), failures)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun persistentFailures_areSeparatedByBackoff_soARetryLoopCannotSpinTheDispatcher() = runTest {
        var attempts = 0

        restartOnFailure(onError = {}) {
            attempts++
            if (attempts < 3) throw RuntimeException("persistent data failure")
        }

        // Two failures → two backoff waits (1s, then 2s exponential) on the
        // virtual clock: the retry loop is rate-limited instead of re-failing
        // as fast as the dispatcher allows. Without backoff every attempt runs
        // at t=0 and this assertion fails.
        assertEquals(3, attempts)
        assertEquals(3_000, currentTime)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun cancellationDuringTheBackoffDelay_stopsTheRetryLoop() = runTest {
        val failures = mutableListOf<Exception>()
        var attempts = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            restartOnFailure(onError = { failures.add(it) }) {
                attempts++
                if (attempts > 4) {
                    // Anti-hang guard for a no-backoff implementation: with no
                    // delay the loop spins without a suspension point, so bail
                    // out cancelled and let the assertions below fail fast.
                    throw CancellationException("retry loop never suspended")
                }
                throw RuntimeException("persistent data failure")
            }
        }

        advanceTimeBy(500) // mid-backoff: the first wait is 1s
        assertTrue("loop kept retrying during the first 500ms: attempts=$attempts", job.isActive)
        job.cancel()

        // The delay's CancellationException must propagate, not be retried.
        assertTrue(job.isCancelled)
        assertEquals(1, failures.size)
    }

    @Test
    fun doesNotLoop_whenTheBlockCompletesNormally() = runBlocking {
        var runs = 0

        restartOnFailure(onError = {}) { runs++ }

        assertEquals(1, runs)
    }
}
