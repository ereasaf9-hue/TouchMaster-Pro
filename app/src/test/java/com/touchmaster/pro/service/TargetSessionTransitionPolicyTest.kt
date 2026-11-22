package com.touchmaster.pro.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetSessionTransitionPolicyTest {
    @Test
    fun ignoresNonTargetEventsInsideStartupGrace() {
        assertTrue(TargetSessionTransitionPolicy.isWithinStartupGrace(1_000L, 9_999L, 10_000L))
    }

    @Test
    fun doesNotIgnoreAtOrAfterGraceBoundary() {
        assertFalse(TargetSessionTransitionPolicy.isWithinStartupGrace(1_000L, 11_000L, 10_000L))
        assertFalse(TargetSessionTransitionPolicy.isWithinStartupGrace(1_000L, 11_001L, 10_000L))
    }

    @Test
    fun missingStartOrInvalidClockDoesNotEnterGrace() {
        assertFalse(TargetSessionTransitionPolicy.isWithinStartupGrace(null, 1_000L, 10_000L))
        assertFalse(TargetSessionTransitionPolicy.isWithinStartupGrace(2_000L, 1_000L, 10_000L))
        assertFalse(TargetSessionTransitionPolicy.isWithinStartupGrace(1_000L, 1_000L, 0L))
    }
}
