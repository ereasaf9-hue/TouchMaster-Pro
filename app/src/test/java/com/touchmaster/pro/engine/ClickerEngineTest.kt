package com.touchmaster.pro.engine

import com.touchmaster.pro.data.ClickerSettings
import com.touchmaster.pro.data.PointEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class ClickerEngineTest {
    private class RecordingExecutor : GestureExecutor {
        val calls = CopyOnWriteArrayList<List<PointEntry>>()
        val timesMs = CopyOnWriteArrayList<Long>()

        override suspend fun tap(points: List<PointEntry>, width: Int, height: Int, pressMs: Long): Result<Unit> {
            calls += points
            timesMs += System.nanoTime() / 1_000_000L
            return Result.success(Unit)
        }

        override fun cancel() = Unit
    }

    @Test
    fun runningConfigurationUsesLatestTargetsAndHonorsMultiPointDuration() = runBlocking {
        val executor = RecordingExecutor()
        val engine = ClickerEngine(executor)
        val oldTarget = PointEntry(1, .1f, .1f, "old")
        val newTarget = PointEntry(2, .7f, .7f, "new")
        val secondTarget = PointEntry(3, .8f, .8f, "second")
        val settings = ClickerSettings(intervalMs = 50, pressMs = 100, repetitions = 0)

        try {
            engine.start(listOf(oldTarget), settings, 1000, 2000)
            withTimeout(1_000) { while (executor.calls.isEmpty()) delay(5) }
            engine.updateConfiguration(listOf(newTarget, secondTarget), settings, 1000, 2000)

            delay(80)
            assertEquals("the multi-point stroke sequence needs 200 ms", 1, executor.calls.size)

            withTimeout(1_500) { while (executor.calls.size < 2) delay(5) }
            assertEquals(newTarget.id, executor.calls[1].first().id)
            assertTrue(executor.timesMs[1] - executor.timesMs[0] >= 150L)
        } finally {
            engine.stop()
        }
    }
}
