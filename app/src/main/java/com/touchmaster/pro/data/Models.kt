package com.touchmaster.pro.data

import android.content.pm.ApplicationInfo

enum class Orientation { PORTRAIT, LANDSCAPE }
enum class ClickerMode { TOGGLE, HOLD, ARMED_HOLD, SINGLE }
enum class EngineState { OFF, ARMED, RUNNING, PAUSED, ERROR }

data class PointEntry(val id: Long, val xFrac: Float, val yFrac: Float, val label: String = "", val pressMs: Long = 40, val startDelayMs: Long = 0)
data class PointSet(val id: Long, val name: String, val portrait: List<PointEntry> = emptyList(), val landscape: List<PointEntry> = emptyList()) {
    fun points(orientation: Orientation) = if (orientation == Orientation.PORTRAIT) portrait else landscape
}
data class ClickerSettings(val intervalMs: Long = 100, val pressMs: Long = 40, val repetitions: Int = 0, val countdownSec: Int = 3, val mode: ClickerMode = ClickerMode.TOGGLE)
data class AppItem(val packageName: String, val label: String, val icon: ApplicationInfo? = null)
