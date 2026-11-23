package com.touchmaster.pro.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import com.touchmaster.pro.data.PointEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

interface GestureExecutor { suspend fun tap(points: List<PointEntry>, width: Int, height: Int, pressMs: Long): Result<Unit>; fun cancel() }

class AccessibilityGestureExecutor(private val service: AccessibilityService) : GestureExecutor {
    // Gesture dispatch shares the service's main-thread window/input lifecycle.
    override suspend fun tap(points: List<PointEntry>, width: Int, height: Int, pressMs: Long): Result<Unit> = withContext(Dispatchers.Main.immediate) {
        if (points.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Bu yönde nokta yok."))
        val builder = GestureDescription.Builder()
        points.forEachIndexed { index, point ->
            val x = (point.xFrac.coerceIn(0f, 1f) * width).coerceIn(0f, width - 1f)
            val y = (point.yFrac.coerceIn(0f, 1f) * height).coerceIn(0f, height - 1f)
            val path = Path().apply { moveTo(x, y) }
            builder.addStroke(GestureDescription.StrokeDescription(path, index * pressMs, pressMs.coerceAtLeast(1)))
        }
        suspendCancellableCoroutine { cont ->
            val ok = service.dispatchGesture(builder.build(), object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(Result.success(Unit)) }
                override fun onCancelled(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(Result.failure(IllegalStateException("Gesture iptal edildi."))) }
            }, null)
            if (!ok && cont.isActive) cont.resume(Result.failure(IllegalStateException("Gesture gönderilemedi.")))
        }
    }
    // Android exposes no API to cancel a gesture after dispatchGesture() accepted it.
    // ClickerEngine cancels the repeating job, so no subsequent taps are scheduled.
    override fun cancel() = Unit
}

class FakeGestureExecutor : GestureExecutor {
    val calls = mutableListOf<List<PointEntry>>()
    override suspend fun tap(points: List<PointEntry>, width: Int, height: Int, pressMs: Long): Result<Unit> { calls += points; return Result.success(Unit) }
    override fun cancel() {}
}

fun nextDeadline(start: Long, count: Long, interval: Long) = start + count * interval
