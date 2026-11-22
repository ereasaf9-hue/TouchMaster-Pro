package com.touchmaster.pro.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetSessionTransitionPolicyTest {
    @Test
    fun pausesAfterTargetIsSeenAndForegroundMovesElsewhere() {
        assertTrue(TargetSessionTransitionPolicy.shouldPauseClicker(true, "game.package", "launcher.package"))
    }

    @Test
    fun doesNotPauseWhileTargetRemainsForeground() {
        assertFalse(TargetSessionTransitionPolicy.shouldPauseClicker(true, "game.package", "game.package"))
    }

    @Test
    fun doesNotPauseBeforeTargetHasBeenForeground() {
        assertFalse(TargetSessionTransitionPolicy.shouldPauseClicker(false, "game.package", "launcher.package"))
    }

    @Test
    fun missingPackageNamesDoNotTriggerPausePolicy() {
        assertFalse(TargetSessionTransitionPolicy.shouldPauseClicker(true, null, "launcher.package"))
        assertFalse(TargetSessionTransitionPolicy.shouldPauseClicker(true, "game.package", null))
    }
}
