package com.touchmaster.pro.service

/** Pure timing policy for ignoring noisy package events immediately after the target app opens. */
internal object TargetSessionTransitionPolicy {
    fun isWithinStartupGrace(
        firstTargetForegroundAtElapsedMs: Long?,
        nowElapsedMs: Long,
        gracePeriodMs: Long,
    ): Boolean {
        val firstForegroundAt = firstTargetForegroundAtElapsedMs ?: return false
        if (gracePeriodMs <= 0L || nowElapsedMs < firstForegroundAt) return false
        return nowElapsedMs - firstForegroundAt < gracePeriodMs
    }
}
