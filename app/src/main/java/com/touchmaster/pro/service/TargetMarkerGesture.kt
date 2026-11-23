package com.touchmaster.pro.service

/** Tracks one marker press so drags, long presses, and cancellations cannot overlap. */
internal class TargetMarkerGesture(private val touchSlopPx: Float) {
    enum class Completion {
        NONE,
        TAP,
        DRAG,
        STOP_HOLD,
    }

    private var pointerId: Int? = null
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var holding = false

    val activePointerId: Int? get() = pointerId
    val isHolding: Boolean get() = holding

    fun begin(pointerId: Int, x: Float, y: Float) {
        this.pointerId = pointerId
        downX = x
        downY = y
        moved = false
        holding = false
    }

    /** Returns true once movement exceeds touch slop; a live hold stays a hold until release. */
    fun move(pointerId: Int, x: Float, y: Float): Boolean {
        if (this.pointerId != pointerId || holding) return false
        if (!moved && (kotlin.math.abs(x - downX) > touchSlopPx ||
                kotlin.math.abs(y - downY) > touchSlopPx)
        ) {
            moved = true
        }
        return moved
    }

    fun beginHold(): Boolean {
        if (pointerId == null || moved || holding) return false
        holding = true
        return true
    }

    fun finish(pointerId: Int): Completion {
        if (this.pointerId != pointerId) return Completion.NONE
        val completion = when {
            holding -> Completion.STOP_HOLD
            moved -> Completion.DRAG
            else -> Completion.TAP
        }
        reset()
        return completion
    }

    fun cancel(): Completion {
        if (pointerId == null) return Completion.NONE
        val completion = if (holding) Completion.STOP_HOLD else Completion.NONE
        reset()
        return completion
    }

    private fun reset() {
        pointerId = null
        moved = false
        holding = false
    }
}
