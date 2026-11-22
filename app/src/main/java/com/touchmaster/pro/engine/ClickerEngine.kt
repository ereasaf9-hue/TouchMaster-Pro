package com.touchmaster.pro.engine

import com.touchmaster.pro.data.ClickerMode
import com.touchmaster.pro.data.ClickerSettings
import com.touchmaster.pro.data.EngineState
import com.touchmaster.pro.data.PointEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ClickerEngine(
    private val executor: GestureExecutor,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private data class RuntimeConfig(
        val points: List<PointEntry>,
        val settings: ClickerSettings,
        val width: Int,
        val height: Int,
    )

    private val _state = MutableStateFlow(EngineState.OFF)
    val state: StateFlow<EngineState> = _state.asStateFlow()
    private var job: Job? = null
    @Volatile private var runtimeConfig: RuntimeConfig? = null
    @Volatile private var runGeneration = 0

    fun arm() {
        if (_state.value == EngineState.OFF) _state.value = EngineState.ARMED
    }

    fun toggle(points: List<PointEntry>, settings: ClickerSettings, width: Int, height: Int) {
        if (_state.value == EngineState.RUNNING) stop() else start(points, settings, width, height)
    }

    fun start(points: List<PointEntry>, settings: ClickerSettings, width: Int, height: Int) {
        if (points.isEmpty()) {
            job?.cancel()
            runtimeConfig = null
            executor.cancel()
            _state.value = EngineState.ERROR
            return
        }
        val generation = ++runGeneration
        job?.cancel()
        runtimeConfig = RuntimeConfig(points.toList(), settings, width, height)
        _state.value = EngineState.RUNNING
        job = scope.launch {
            val thisJob = currentCoroutineContext()[Job]
            var count = 0L
            val started = monotonicMillis()
            try {
                while (isActive) {
                    val config = runtimeConfig ?: break
                    if (config.settings.repetitions > 0 && count >= config.settings.repetitions) break
                    val deadline = nextDeadline(started, count, effectiveInterval(config))
                    val wait = deadline - monotonicMillis()
                    if (wait > 0) {
                        // Re-check live settings regularly so a changed interval takes effect promptly.
                        delay(wait.coerceAtMost(50L))
                        continue
                    }

                    val latest = runtimeConfig ?: break
                    if (latest.settings.repetitions > 0 && count >= latest.settings.repetitions) break
                    if (nextDeadline(started, count, effectiveInterval(latest)) > monotonicMillis()) continue
                    executor.tap(latest.points, latest.width, latest.height, latest.settings.pressMs).getOrThrow()
                    count++
                }
                if (isActive && generation == runGeneration) _state.value = EngineState.OFF
            } catch (_: CancellationException) {
                if (generation == runGeneration && _state.value != EngineState.PAUSED) _state.value = EngineState.OFF
            } catch (_: Throwable) {
                if (generation == runGeneration) _state.value = EngineState.ERROR
            } finally {
                if (generation == runGeneration && job === thisJob && _state.value != EngineState.RUNNING) {
                    runtimeConfig = null
                }
            }
        }
    }

    /** Applies changes to the next gesture without cancelling the gesture already dispatched. */
    fun updateConfiguration(points: List<PointEntry>, settings: ClickerSettings, width: Int, height: Int) {
        if (_state.value == EngineState.RUNNING && points.isNotEmpty()) {
            runtimeConfig = RuntimeConfig(points.toList(), settings, width, height)
        }
    }

    private fun effectiveInterval(config: RuntimeConfig): Long {
        val pressDuration = config.settings.pressMs.coerceAtLeast(1L)
        val minimumCycle = config.points.size.toLong() * pressDuration
        return maxOf(config.settings.intervalMs.coerceAtLeast(1L), minimumCycle)
    }

    private fun monotonicMillis() = System.nanoTime() / 1_000_000L

    fun pause() {
        if (_state.value == EngineState.RUNNING) {
            runGeneration++
            job?.cancel()
            _state.value = EngineState.PAUSED
            executor.cancel()
        }
    }

    fun resume(points: List<PointEntry>, settings: ClickerSettings, width: Int, height: Int) {
        if (_state.value == EngineState.PAUSED) start(points, settings, width, height)
    }

    fun stop() {
        runGeneration++
        job?.cancel()
        job = null
        runtimeConfig = null
        executor.cancel()
        _state.value = EngineState.OFF
    }

    fun holdDown(points: List<PointEntry>, settings: ClickerSettings, width: Int, height: Int) {
        if (settings.mode == ClickerMode.ARMED_HOLD && _state.value != EngineState.ARMED) return
        start(points, settings.copy(repetitions = 0), width, height)
    }

    fun holdUp() = stop()
}
