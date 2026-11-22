package com.touchmaster.pro.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.touchmaster.pro.data.ClickerSettings
import com.touchmaster.pro.data.EngineState
import com.touchmaster.pro.data.PointEntry
import com.touchmaster.pro.engine.AccessibilityGestureExecutor
import com.touchmaster.pro.engine.ClickerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class TouchMasterAccessibilityService : AccessibilityService() {
    companion object {
        val connected = MutableStateFlow(false)
        var instance: TouchMasterAccessibilityService? = null
        private const val PANEL_WIDTH_DP = 320
        private const val MIN_PANEL_WIDTH_DP = 220
        private const val MARKER_SIZE_DP = 42
        private const val TARGET_EXIT_GRACE_MS = 900L
        private const val MIN_INTERVAL_MS = 50L
        private const val MAX_INTERVAL_MS = 5_000L
        private const val CARD = 0xF21A1D27.toInt()
        private const val PANEL_BUTTON = 0xFF2B3040.toInt()
        private const val ACCENT = 0xFF65D7FF.toInt()
        private const val ACCENT_DARK = 0xFF3563A8.toInt()
        private const val STOP_DARK = 0xFF593746.toInt()
        private const val DIVIDER = 0xFF353A49.toInt()
        private const val WHITE = 0xFFF4F6FC.toInt()
        private const val MUTED = 0xFFACB3C4.toInt()
        private const val WARNING = 0xFFFFC46B.toInt()
        private const val ERROR = 0xFFFF7777.toInt()
    }

    private lateinit var executor: AccessibilityGestureExecutor
    lateinit var clicker: ClickerEngine
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var panel: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var statusText: TextView? = null
    private var targetCountText: TextView? = null
    private var intervalText: TextView? = null
    private var runButton: TextView? = null
    private val activePoints = mutableListOf<PointEntry>()
    private val markerViews = mutableListOf<View>()
    private var activeSettings = ClickerSettings()
    private var targetPackage: String? = null
    private var targetLabel: String = "Uygulama"
    private var lastForegroundPackage: String? = null
    private var collapsed = false

    private val leaveTargetRunnable = Runnable {
        if (targetPackage != null && lastForegroundPackage != targetPackage) removePanel()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WindowManager::class.java)
        instance = this
        connected.value = true
        executor = AccessibilityGestureExecutor(this)
        clicker = ClickerEngine(executor)
        serviceScope.launch {
            clicker.state.collect { state -> updateRunState(state) }
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        connected.value = false
        if (::clicker.isInitialized) clicker.stop()
        removePanel()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        removePanel()
        connected.value = false
        serviceScope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val foreground = event.packageName?.toString() ?: return
        val previousForeground = lastForegroundPackage
        lastForegroundPackage = foreground
        if (foreground == targetPackage) {
            mainHandler.removeCallbacks(leaveTargetRunnable)
        } else if (previousForeground == targetPackage) {
            mainHandler.removeCallbacks(leaveTargetRunnable)
            mainHandler.postDelayed(leaveTargetRunnable, TARGET_EXIT_GRACE_MS)
        }
    }

    override fun onInterrupt() {
        if (::clicker.isInitialized) clicker.stop()
        updateRunState(EngineState.OFF)
    }

    /** Creates the session overlay only after the target app and required permissions are validated. */
    fun showPanel(
        targetPackage: String,
        targetLabel: String,
        points: List<PointEntry> = emptyList(),
        settings: ClickerSettings = ClickerSettings(),
    ): Boolean {
        if (!::clicker.isInitialized) return false
        removePanel()
        this.targetPackage = targetPackage
        this.targetLabel = targetLabel
        lastForegroundPackage = null
        activePoints.clear()
        activePoints.addAll(points)
        activeSettings = settings
        collapsed = false

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(CARD, 22)
            elevation = dp(12).toFloat()
        }
        panel = root
        buildPanel(root)

        val params = WindowManager.LayoutParams(
            minOf(dp(PANEL_WIDTH_DP), (resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(MIN_PANEL_WIDTH_DP))),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(96)
        }
        panelParams = params
        return try {
            windowManager.addView(root, params)
            renderMarkers()
            updatePanelContent()
            true
        } catch (_: RuntimeException) {
            panel = null
            panelParams = null
            removeMarkers()
            this.targetPackage = null
            false
        }
    }

    private fun buildPanel(root: LinearLayout) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleColumn.addView(label("TOUCHMASTER", 10, MUTED, bold = true).apply { letterSpacing = .12f })
        titleColumn.addView(label(targetLabel, 17, WHITE, bold = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        header.addView(titleColumn, LinearLayout.LayoutParams(0, dp(48), 1f))
        val minimize = actionText("—", 40, 40, PANEL_BUTTON) { toggleCollapsed() }
        val close = actionText("×", 40, 40, PANEL_BUTTON) { removePanel() }
        header.addView(minimize, marginParams(40, 40, start = 6))
        header.addView(close, marginParams(40, 40, start = 8))
        val dragStart = IntArray(2)
        val windowStart = IntArray(2)
        var headerMoved = false
        titleColumn.setOnClickListener { toggleCollapsed() }
        titleColumn.setOnTouchListener { view, event ->
            val params = panelParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    headerMoved = false
                    dragStart[0] = event.rawX.roundToInt()
                    dragStart[1] = event.rawY.roundToInt()
                    windowStart[0] = params.x
                    windowStart[1] = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX.roundToInt() - dragStart[0]
                    val deltaY = event.rawY.roundToInt() - dragStart[1]
                    if (kotlin.math.abs(deltaX) > ViewConfiguration.get(this).scaledTouchSlop ||
                        kotlin.math.abs(deltaY) > ViewConfiguration.get(this).scaledTouchSlop) headerMoved = true
                    val maxX = (resources.displayMetrics.widthPixels - params.width).coerceAtLeast(0)
                    val maxY = (resources.displayMetrics.heightPixels - (panel?.height ?: 0)).coerceAtLeast(dp(24))
                    params.x = (windowStart[0] - deltaX).coerceIn(0, maxX)
                    params.y = (windowStart[1] + deltaY).coerceIn(dp(24), maxY)
                    updatePanelLayout()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!headerMoved) view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
        root.addView(header)
        root.addView(spacer(10))

        val statusRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        statusText = label("HAZIR · Başlamak için hedef ekleyin", 12, ACCENT, bold = true)
        statusRow.addView(statusText, LinearLayout.LayoutParams(0, dp(32), 1f))
        targetCountText = label("0 hedef", 12, MUTED)
        statusRow.addView(targetCountText)
        root.addView(statusRow)
        root.addView(divider())

        val targetRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val targetInfo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        targetInfo.addView(label("DOKUNMA NOKTALARI", 11, WHITE, bold = true))
        targetInfo.addView(label("Numaralı hedefi sürükleyin", 11, MUTED))
        targetRow.addView(targetInfo, LinearLayout.LayoutParams(0, dp(46), 1f))
        targetRow.addView(actionText("+ HEDEF", 86, 38, ACCENT_DARK) { addTarget() })
        root.addView(targetRow)
        root.addView(spacer(8))

        val speedRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        speedRow.addView(label("Tıklama aralığı", 13, WHITE), LinearLayout.LayoutParams(0, dp(40), 1f))
        speedRow.addView(actionText("−", 38, 36, PANEL_BUTTON) { changeInterval(-50) })
        intervalText = label("${activeSettings.intervalMs} ms", 12, WHITE, bold = true).apply { gravity = Gravity.CENTER }
        speedRow.addView(intervalText, LinearLayout.LayoutParams(dp(66), dp(36)))
        speedRow.addView(actionText("+", 38, 36, PANEL_BUTTON) { changeInterval(50) })
        root.addView(speedRow)
        root.addView(spacer(10))

        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        runButton = actionText("▶  Başlat", 0, 46, ACCENT_DARK) { toggleRun() }.apply { gravity = Gravity.CENTER }
        controls.addView(runButton, LinearLayout.LayoutParams(0, dp(46), 1f))
        controls.addView(actionText("■  Durdur", 94, 46, STOP_DARK) { stopRun() }, marginParams(94, 46, start = 8))
        root.addView(controls)
        root.addView(spacer(4))
        root.addView(label("Bir hedefe uzun basarak kaldırabilirsiniz.", 10, MUTED).apply { gravity = Gravity.CENTER })
    }

    private fun addTarget() {
        val id = (activePoints.maxOfOrNull { it.id } ?: 0L) + 1L
        activePoints += PointEntry(id, 0.5f, 0.5f, id.toString())
        renderMarkers()
        updatePanelContent()
        Toast.makeText(this, "Hedef eklendi. Numaralı daireyi istediğiniz noktaya sürükleyin.", Toast.LENGTH_SHORT).show()
    }

    private fun removeTarget(index: Int) {
        if (index !in activePoints.indices) return
        val label = activePoints[index].label.ifBlank { (index + 1).toString() }
        activePoints.removeAt(index)
        renderMarkers()
        updatePanelContent()
        Toast.makeText(this, "Hedef $label kaldırıldı.", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun renderMarkers() {
        removeMarkers()
        if (panel == null) return
        val metrics = resources.displayMetrics
        activePoints.forEachIndexed { index, point ->
            val marker = TextView(this).apply {
                text = point.label.ifBlank { (index + 1).toString() }
                textSize = 14f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = rounded(ACCENT, 100)
                elevation = dp(8).toFloat()
                contentDescription = "Hedef ${index + 1}. Sürükleyin; kaldırmak için uzun basın."
            }
            val size = dp(MARKER_SIZE_DP)
            val params = WindowManager.LayoutParams(
                size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (point.xFrac.coerceIn(0f, 1f) * metrics.widthPixels).roundToInt() - size / 2
                y = (point.yFrac.coerceIn(0f, 1f) * metrics.heightPixels).roundToInt() - size / 2
                x = x.coerceIn(0, (metrics.widthPixels - size).coerceAtLeast(0))
                y = y.coerceIn(dp(24), (metrics.heightPixels - size).coerceAtLeast(dp(24)))
            }
            var longPressHandled = false
            var markerMoved = false
            val longPressRunnable = Runnable {
                longPressHandled = true
                removeTarget(index)
            }
            var downRawX = 0f
            var downRawY = 0f
            marker.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        longPressHandled = false
                        markerMoved = false
                        downRawX = event.rawX
                        downRawY = event.rawY
                        mainHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
                        if (kotlin.math.abs(event.rawX - downRawX) > touchSlop || kotlin.math.abs(event.rawY - downRawY) > touchSlop) {
                            markerMoved = true
                            mainHandler.removeCallbacks(longPressRunnable)
                        }
                        if (longPressHandled) return@setOnTouchListener true
                        params.x = (event.rawX - size / 2f).roundToInt().coerceIn(0, (metrics.widthPixels - size).coerceAtLeast(0))
                        params.y = (event.rawY - size / 2f).roundToInt().coerceIn(dp(24), (metrics.heightPixels - size).coerceAtLeast(dp(24)))
                        try { windowManager.updateViewLayout(view, params) } catch (_: RuntimeException) { }
                        val centerX = params.x + size / 2f
                        val centerY = params.y + size / 2f
                        if (index in activePoints.indices) {
                            activePoints[index] = activePoints[index].copy(
                                xFrac = (centerX / metrics.widthPixels.coerceAtLeast(1)).coerceIn(0f, 1f),
                                yFrac = (centerY / metrics.heightPixels.coerceAtLeast(1)).coerceIn(0f, 1f),
                            )
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        mainHandler.removeCallbacks(longPressRunnable)
                        if (!longPressHandled && !markerMoved) view.performClick() else updatePanelContent()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        mainHandler.removeCallbacks(longPressRunnable)
                        true
                    }
                    else -> true
                }
            }
            marker.setOnClickListener { setStatus("Hedef seçildi · sürükleyerek konumlandırın", ACCENT) }
            try {
                windowManager.addView(marker, params)
                markerViews += marker
            } catch (_: RuntimeException) {
                Toast.makeText(this, "Hedef işaretçisi gösterilemedi. Ekran üstü iznini kontrol edin.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun removeMarkers() {
        markerViews.forEach { marker ->
            try { windowManager.removeView(marker) } catch (_: RuntimeException) { }
        }
        markerViews.clear()
    }

    private fun toggleRun() {
        if (activePoints.isEmpty()) {
            setStatus("Önce + HEDEF ile hedef ekleyin", ACCENT)
            return
        }
        val width = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val height = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        when (clicker.state.value) {
            EngineState.RUNNING -> clicker.pause()
            EngineState.PAUSED -> clicker.resume(activePoints.toList(), activeSettings, width, height)
            else -> clicker.start(activePoints.toList(), activeSettings, width, height)
        }
        updateRunState(clicker.state.value)
    }

    private fun stopRun() {
        clicker.stop()
        updateRunState(EngineState.OFF)
    }

    private fun changeInterval(delta: Long) {
        activeSettings = activeSettings.copy(intervalMs = (activeSettings.intervalMs + delta).coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS))
        intervalText?.text = "${activeSettings.intervalMs} ms"
    }

    private fun updateRunState(state: EngineState) {
        if (panel == null) return
        when (state) {
            EngineState.RUNNING -> {
                setStatus("ÇALIŞIYOR · Dokunmak için Başlat/Duraklat", ACCENT)
                runButton?.text = "Ⅱ  Duraklat"
            }
            EngineState.PAUSED -> {
                setStatus("DURAKLATILDI · Devam etmek için dokunun", WARNING)
                runButton?.text = "▶  Devam et"
            }
            EngineState.ERROR -> {
                setStatus("HATA · Erişilebilirlik servisini kontrol edin", ERROR)
                runButton?.text = "▶  Yeniden dene"
            }
            else -> {
                setStatus(if (activePoints.isEmpty()) "HAZIR · Başlamak için hedef ekleyin" else "HAZIR · Başlatmaya hazır", ACCENT)
                runButton?.text = "▶  Başlat"
            }
        }
    }

    private fun updatePanelContent() {
        targetCountText?.text = "${activePoints.size} hedef"
        intervalText?.text = "${activeSettings.intervalMs} ms"
        if (clicker.state.value != EngineState.RUNNING && clicker.state.value != EngineState.PAUSED) {
            updateRunState(clicker.state.value)
        }
    }

    private fun toggleCollapsed() {
        collapsed = !collapsed
        panel?.let { root ->
            for (index in 1 until root.childCount) root.getChildAt(index).visibility = if (collapsed) View.GONE else View.VISIBLE
            panelParams?.let {
                try { windowManager.updateViewLayout(root, it) } catch (_: RuntimeException) { }
            }
        }
    }

    fun removePanel() {
        mainHandler.removeCallbacks(leaveTargetRunnable)
        if (::clicker.isInitialized) clicker.stop()
        removeMarkers()
        panel?.let { view ->
            try { windowManager.removeView(view) } catch (_: RuntimeException) { }
        }
        panel = null
        panelParams = null
        statusText = null
        targetCountText = null
        intervalText = null
        runButton = null
        activePoints.clear()
        targetPackage = null
        lastForegroundPackage = null
        collapsed = false
    }

    private fun updatePanelLayout() {
        val view = panel ?: return
        val params = panelParams ?: return
        try { windowManager.updateViewLayout(view, params) } catch (_: RuntimeException) { }
    }

    private fun setStatus(message: String, color: Int) {
        statusText?.text = message
        statusText?.setTextColor(color)
    }

    private fun label(text: String, sizeSp: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = sizeSp.toFloat()
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private fun actionText(text: String, widthDp: Int, heightDp: Int, color: Int, action: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (color == ACCENT_DARK) Color.WHITE else WHITE)
        gravity = Gravity.CENTER
        background = rounded(color, 12)
        isClickable = true
        isFocusable = false
        setPadding(dp(8), dp(4), dp(8), dp(4))
        setOnClickListener { action() }
        if (widthDp > 0) layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(heightDp))
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(DIVIDER)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(5)
            bottomMargin = dp(8)
        }
    }

    private fun spacer(heightDp: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(heightDp)) }

    private fun marginParams(width: Int, height: Int, start: Int) = LinearLayout.LayoutParams(dp(width), dp(height)).apply { marginStart = dp(start) }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

}
