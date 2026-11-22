package com.touchmaster.pro.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.accessibility.AccessibilityEvent
import com.touchmaster.pro.data.*
import com.touchmaster.pro.engine.*
import kotlinx.coroutines.flow.MutableStateFlow

class TouchMasterAccessibilityService : AccessibilityService() {
    companion object { val connected = MutableStateFlow(false); var instance: TouchMasterAccessibilityService? = null }
    private lateinit var executor: AccessibilityGestureExecutor
    lateinit var clicker: ClickerEngine; private var panel: View? = null; private var lastPackage: String? = null
    override fun onServiceConnected() { super.onServiceConnected(); instance = this; connected.value = true; executor = AccessibilityGestureExecutor(this); clicker = ClickerEngine(executor) }
    override fun onUnbind(intent: android.content.Intent?): Boolean { connected.value = false; clicker.stop(); removePanel(); instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { removePanel(); connected.value = false; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) lastPackage = event.packageName?.toString() }
    override fun onInterrupt() { clicker.stop() }
    fun showPanel(points: List<PointEntry>, settings: ClickerSettings) {
        if (panel != null) return
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 8, 12, 8); setBackgroundColor(0xEE151520.toInt()) }
        val status = TextView(this).apply { text = "TouchMaster Pro • Hazır"; setTextColor(0xFFFFFFFF.toInt()) }
        val start = Button(this).apply { text = "▶ Başlat"; setOnClickListener { clicker.toggle(points, settings, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels); status.text = "Durum: ${clicker.state.value}" } }
        val stop = Button(this).apply { text = "⏹ Acil durdur"; setOnClickListener { clicker.stop(); status.text = "Durum: Durduruldu" } }
        box.addView(status); box.addView(start); box.addView(stop)
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; y = 80 }
        getSystemService(WindowManager::class.java).addView(box, params); panel = box
    }
    fun removePanel() { panel?.let { getSystemService(WindowManager::class.java).removeView(it) }; panel = null }
}
