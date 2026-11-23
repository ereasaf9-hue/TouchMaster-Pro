package com.touchmaster.pro.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetMarkerGestureTest {
    @Test
    fun stationaryPressBecomesHoldAndStopsOnRelease() {
        val gesture = TargetMarkerGesture(touchSlopPx = 8f)
        gesture.begin(pointerId = 2, x = 100f, y = 200f)

        assertTrue(gesture.beginHold())
        assertEquals(TargetMarkerGesture.Completion.STOP_HOLD, gesture.finish(pointerId = 2))
        assertEquals(null, gesture.activePointerId)
    }

    @Test
    fun movementCancelsPendingHoldAndFinishesAsDrag() {
        val gesture = TargetMarkerGesture(touchSlopPx = 8f)
        gesture.begin(pointerId = 0, x = 10f, y = 10f)

        assertTrue(gesture.move(pointerId = 0, x = 19f, y = 10f))
        assertFalse(gesture.beginHold())
        assertEquals(TargetMarkerGesture.Completion.DRAG, gesture.finish(pointerId = 0))
    }

    @Test
    fun shortPressRemainsATapWhenMovementStaysWithinSlop() {
        val gesture = TargetMarkerGesture(touchSlopPx = 8f)
        gesture.begin(pointerId = 0, x = 10f, y = 10f)

        assertFalse(gesture.move(pointerId = 0, x = 16f, y = 14f))
        assertEquals(TargetMarkerGesture.Completion.TAP, gesture.finish(pointerId = 0))
    }

    @Test
    fun smallFingerMovementDoesNotCancelStationaryHold() {
        val gesture = TargetMarkerGesture(touchSlopPx = 8f)
        gesture.begin(pointerId = 1, x = 10f, y = 10f)

        assertFalse(gesture.move(pointerId = 1, x = 16f, y = 14f))
        assertTrue(gesture.beginHold())
        assertFalse(gesture.move(pointerId = 1, x = 30f, y = 40f))
        assertEquals(TargetMarkerGesture.Completion.STOP_HOLD, gesture.finish(pointerId = 1))
    }

    @Test
    fun cancelStopsAnActiveHoldButNeverCreatesATap() {
        val gesture = TargetMarkerGesture(touchSlopPx = 8f)
        gesture.begin(pointerId = 3, x = 0f, y = 0f)
        gesture.beginHold()

        assertEquals(TargetMarkerGesture.Completion.STOP_HOLD, gesture.cancel())
        assertEquals(TargetMarkerGesture.Completion.NONE, gesture.cancel())
    }

    @Test
    fun releaseFromAnotherPointerDoesNotFinishTheActiveGesture() {
        val gesture = TargetMarkerGesture(touchSlopPx = 8f)
        gesture.begin(pointerId = 5, x = 0f, y = 0f)

        assertEquals(TargetMarkerGesture.Completion.NONE, gesture.finish(pointerId = 6))
        assertEquals(5, gesture.activePointerId)
    }

    @Test
    fun sameMarkerCanStartASecondHoldAfterTheFirstRelease() {
        val gesture = TargetMarkerGesture(touchSlopPx = 8f)
        gesture.begin(pointerId = 0, x = 20f, y = 30f)
        assertTrue(gesture.beginHold())
        assertEquals(TargetMarkerGesture.Completion.STOP_HOLD, gesture.finish(pointerId = 0))

        gesture.begin(pointerId = 0, x = 20f, y = 30f)
        assertTrue(gesture.beginHold())
        assertEquals(TargetMarkerGesture.Completion.STOP_HOLD, gesture.finish(pointerId = 0))
    }
}
