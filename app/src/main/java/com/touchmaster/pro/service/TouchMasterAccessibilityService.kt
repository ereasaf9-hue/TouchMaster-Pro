package com.touchmaster.pro.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout

/** Erişilebilirlik yalnızca kullanıcı tarafından açıkça başlatılan dokunmaları uygular. */
class TouchMasterAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { super.onServiceConnected(); active = this; _connected.value = true }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* Ekran içeriği okunmaz. */ }
    override fun onInterrupt() { _connected.value = false; active = null }
    override fun onDestroy() { _connected.value = false; active = null; super.onDestroy() }

    suspend fun tap(x: Float, y: Float, durationMs: Long): Boolean {
        val dm = resources.displayMetrics
        requireInside(x, y, dm.widthPixels, dm.heightPixels)
        val path = Path().apply { moveTo(x, y); lineTo(x + 0.1f, y + 0.1f) }
        return dispatch(path, durationMs)
    }

    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
        val dm = resources.displayMetrics
        requireInside(x1, y1, dm.widthPixels, dm.heightPixels)
        requireInside(x2, y2, dm.widthPixels, dm.heightPixels)
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        return dispatch(path, durationMs)
    }

    private fun requireInside(x: Float, y: Float, width: Int, height: Int) {
        require(x.isFinite() && y.isFinite() && x >= 0f && y >= 0f && x < width && y < height) {
            "Koordinat ekran sınırlarının dışında."
        }
    }

    private suspend fun dispatch(path: Path, durationMs: Long): Boolean {
        val maximum = GestureDescription.getMaxGestureDuration().toLong()
        require(durationMs in 1L..maximum) { "Süre cihazın desteklediği 1..$maximum ms aralığında olmalı." }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()
        val result = CompletableDeferred<Boolean>()
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { result.complete(true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { result.complete(false) }
        }, Handler(Looper.getMainLooper()))
        if (!accepted) return false
        return withTimeout(maximum + 5_000L) { result.await() }
    }

    companion object {
        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()
        @Volatile private var active: TouchMasterAccessibilityService? = null
        fun service(): TouchMasterAccessibilityService? = active
    }
}
