package com.touchmaster.pro.macro

import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class MacroCoreTest {
    private val parser = MacroParser(MacroLimits(maxX = 100, maxY = 200, maxLoopDepth = 2, maxLoopCount = 100))

    @Test fun parsesCommandsAndComments() {
        val result = parser.parse("# header\nCOMMENT hello world\nCLICK 1 2\nCLICK 3 4 50\nPRESS 5 6 70\nSWIPE 1 2 3 4 80\nDELAY 9\nSTOP_MACRO")
        assertTrue(result.errors.toString(), result.isSuccess)
        assertEquals(8, result.steps.size)
        assertEquals("header", (result.steps[0] as MacroStep.Comment).text)
        assertEquals(50L, (result.steps[3] as MacroStep.Click).durationMs)
        assertEquals(8, result.steps.last().line)
    }

    @Test fun rejectsBadArgumentsCoordinatesAndUnknownCommandsWithLines() {
        val result = parser.parse("CLICK 101 0\nDELAY nope\nWHAT\nPRESS 1 2")
        assertFalse(result.isSuccess)
        assertTrue(result.steps.isEmpty())
        assertEquals(listOf(1, 2, 3, 4), result.errors.map { it.line })
        assertTrue(result.errors.all { it.message.isNotBlank() })
    }

    @Test fun validatesLoopsAndDepth() {
        val stray = parser.parse("END_LOOP")
        assertEquals(1, stray.errors.single().line)
        val missing = parser.parse("LOOP 2\nCLICK 1 1")
        assertTrue(missing.errors.any { it.line == 1 && it.message.contains("END_LOOP") })
        val depth = parser.parse("LOOP 1\nLOOP 1\nLOOP 1\nEND_LOOP\nEND_LOOP\nEND_LOOP")
        assertTrue(depth.errors.any { it.line == 3 && it.message.contains("sınırı") })
    }

    @Test fun rejectsInvalidDurationsAndLoopCounts() {
        val r = parser.parse("DELAY 0\nPRESS 1 2 -1\nLOOP 101\nCLICK 1 1 86400001")
        assertEquals(listOf(1, 2, 3, 4), r.errors.map { it.line })
    }

    @Test fun executorRunsNestedLoopsAndStops() {
        val result = parser.parse("LOOP 2\nCLICK 1 2\nLOOP 2\nDELAY 3\nEND_LOOP\nEND_LOOP\nSTOP_MACRO\nCLICK 9 9")
        assertTrue(result.errors.toString(), result.isSuccess)
        val runtime = RecordingRuntime()
        runSuspend { MacroExecutor(runtime).execute(result.steps) }
        assertEquals(2, runtime.performed.count { it is MacroStep.Click })
        assertEquals(4, runtime.delays.size)
        assertFalse(runtime.performed.any { it is MacroStep.Click && it.x == 9 })
    }

    @Test fun infiniteLoopCanBeCancelledAtCheckpoint() {
        val result = parser.parse("LOOP_FOREVER\nCLICK 1 1\nEND_LOOP")
        var checks = 0
        val runtime = object : MacroRuntime {
            var performed = 0
            override suspend fun perform(step: MacroStep) { performed++ }
            override suspend fun delay(milliseconds: Long) = Unit
            override suspend fun checkpoint() { if (++checks > 5) throw CancellationExceptionForTest() }
        }
        try {
            runSuspend { MacroExecutor(runtime).execute(result.steps) }
            fail("Expected cooperative cancellation")
        } catch (_: CancellationExceptionForTest) { }
        assertTrue(runtime.performed > 0)
        assertTrue(checks > 5)
    }

    private class RecordingRuntime : MacroRuntime {
        val performed = mutableListOf<MacroStep>()
        val delays = mutableListOf<Long>()
        override suspend fun perform(step: MacroStep) { performed += step }
        override suspend fun delay(milliseconds: Long) { delays += milliseconds }
        override suspend fun checkpoint() = Unit
    }

    private class CancellationExceptionForTest : RuntimeException()

    private fun runSuspend(block: suspend () -> Unit) {
        var failure: Throwable? = null
        var completed = false
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) {
                result.fold({ completed = true }, { failure = it })
            }
        })
        failure?.let { throw it }
        check(completed) { "Test runtime suspended unexpectedly" }
    }
}
