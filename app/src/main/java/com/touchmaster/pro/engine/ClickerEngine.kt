package com.touchmaster.pro.engine

import com.touchmaster.pro.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.os.SystemClock

class ClickerEngine(private val executor: GestureExecutor, private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)) {
    private val _state = MutableStateFlow(EngineState.OFF); val state: StateFlow<EngineState> = _state.asStateFlow()
    private var job: Job? = null
    fun arm() { if (_state.value == EngineState.OFF) _state.value = EngineState.ARMED }
    fun toggle(points: List<PointEntry>, settings: ClickerSettings, width: Int, height: Int) { if (_state.value == EngineState.RUNNING) stop() else start(points, settings, width, height) }
    fun start(points: List<PointEntry>, settings: ClickerSettings, width: Int, height: Int) {
        if (points.isEmpty()) { _state.value = EngineState.ERROR; return }
        job?.cancel(); _state.value = EngineState.RUNNING
        job = scope.launch {
            var count = 0; val started = SystemClock.uptimeMillis()
            try {
                while (isActive && (settings.repetitions == 0 || count < settings.repetitions)) {
                    val deadline = nextDeadline(started, count.toLong(), settings.intervalMs.coerceAtLeast(1))
                    val wait = deadline - SystemClock.uptimeMillis(); if (wait > 0) delay(wait)
                    executor.tap(points, width, height, settings.pressMs).getOrThrow(); count++
                }
                if (isActive) _state.value = EngineState.OFF
            } catch (_: CancellationException) { if (_state.value != EngineState.PAUSED) _state.value = EngineState.OFF }
            catch (_: Throwable) { _state.value = EngineState.ERROR }
        }
    }
    fun pause() { if (_state.value == EngineState.RUNNING) { job?.cancel(); _state.value = EngineState.PAUSED; executor.cancel() } }
    fun resume(points: List<PointEntry>, settings: ClickerSettings, width: Int, height: Int) { if (_state.value == EngineState.PAUSED) start(points, settings, width, height) }
    fun stop() { job?.cancel(); job = null; executor.cancel(); _state.value = EngineState.OFF }
    fun holdDown(points: List<PointEntry>, settings: ClickerSettings, width: Int, height: Int) { if (settings.mode == ClickerMode.ARMED_HOLD && _state.value != EngineState.ARMED) return; start(points, settings.copy(repetitions = 0), width, height) }
    fun holdUp() { stop() }
}
