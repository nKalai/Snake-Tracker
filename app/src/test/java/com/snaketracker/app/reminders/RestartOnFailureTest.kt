package com.snaketracker.app.reminders

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RestartOnFailureTest {

    @Test
    fun restartsTheBlock_afterAFailure_soTransientDataErrorsDoNotKillArming() = runBlocking {
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

    @Test
    fun doesNotLoop_whenTheBlockCompletesNormally() = runBlocking {
        var runs = 0

        restartOnFailure(onError = {}) { runs++ }

        assertEquals(1, runs)
    }
}
