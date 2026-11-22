package com.touchmaster.pro.service

/** Pure policy for pausing click automation and preserving explicit overlay UI on app changes. */
internal object TargetSessionTransitionPolicy {
    fun shouldPauseClicker(
        targetWasForeground: Boolean,
        targetPackage: String?,
        foregroundPackage: String?,
    ): Boolean = targetWasForeground &&
        targetPackage != null &&
        foregroundPackage != null &&
        foregroundPackage != targetPackage &&
        foregroundPackage != "com.touchmaster.pro"

    fun shouldTemporarilyHideBubble(panelOpen: Boolean, isTransientSystemWindow: Boolean): Boolean =
        isTransientSystemWindow && !panelOpen
}
