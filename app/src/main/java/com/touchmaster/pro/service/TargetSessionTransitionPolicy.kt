package com.touchmaster.pro.service

/** Pure policy for pausing click automation when a foreground event leaves the selected target. */
internal object TargetSessionTransitionPolicy {
    fun shouldPauseClicker(
        targetWasForeground: Boolean,
        targetPackage: String?,
        foregroundPackage: String?,
    ): Boolean = targetWasForeground &&
        targetPackage != null &&
        foregroundPackage != null &&
        foregroundPackage != targetPackage
}
