package com.touchmaster.pro.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.touchmaster.pro.data.ClickerSettings
import com.touchmaster.pro.data.EngineState
import com.touchmaster.pro.data.PointEntry
import com.touchmaster.pro.engine.AccessibilityGestureExecutor
import com.touchmaster.pro.engine.ClickerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class TouchMasterAccessibilityService : AccessibilityService() {
    companion object {
        val connected = MutableStateFlow(false)
        var instance: TouchMasterAccessibilityService? = null

        private const val PANEL_WIDTH_DP = 340
        private const val TARGET_EXIT_GRACE_MS = 900L
        private const val MIN_INTERVAL_MS = 50L
        private const val MAX_INTERVAL_MS = 5_000L
        private const val MIN_PRESS_MS = 10L
        private const val MAX_PRESS_MS = 500L
        private const val PROFILE_PREFS = "touchmaster_game_profiles"
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
    private var stateCollectorJob: Job? = null

    private var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panel: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelAttached = false
    private var statusText: TextView? = null
    private var targetCountText: TextView? = null
    private var intervalText: TextView? = null
    private var pressText: TextView? = null
    private var repeatText: TextView? = null
    private var runButton: TextView? = null

    private val activePoints = mutableListOf<PointEntry>()
    private val markerViews = mutableListOf<View>()
    private val pendingMarkerLongPresses = mutableListOf<Runnable>()
    private var activeSettings = ClickerSettings()
    private var targetPackage: String? = null
    private var targetLabel: String = "Uygulama"
    private var lastForegroundPackage: String? = null
    private var targetWasForeground = false

    private val transientSystemPackages = setOf(
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.intentresolver",
    )

    private var bubbleAlpha = 0.96f
    private var bubbleSizeDp = 58
    private var bubbleXFraction = 0.94f
    private var bubbleYFraction = 0.26f
    private var markerAlpha = 0.86f
    private var markerSizeDp = 42
    private var panelAlpha = 0.97f
    private var panelXFraction = 0.04f
    private var panelYFraction = 0.12f

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
        stateCollectorJob?.cancel()
        stateCollectorJob = serviceScope.launch { clicker.state.collect { state -> updateRunState(state) } }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        connected.value = false
        stateCollectorJob?.cancel()
        stateCollectorJob = null
        if (::clicker.isInitialized) clicker.stop()
        removePanel()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stateCollectorJob?.cancel()
        stateCollectorJob = null
        removePanel()
        connected.value = false
        serviceScope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val foreground = event.packageName?.toString() ?: return
        lastForegroundPackage = foreground
        if (foreground == targetPackage) {
            targetWasForeground = true
            mainHandler.removeCallbacks(leaveTargetRunnable)
            if (bubble == null) {
                try {
                    createBubble()
                    renderMarkers()
                } catch (_: RuntimeException) {
                    removePanel()
                }
            }
        } else if (targetWasForeground) {
            if (foreground in transientSystemPackages) {
                mainHandler.removeCallbacks(leaveTargetRunnable)
                if (::clicker.isInitialized && clicker.state.value == EngineState.RUNNING) clicker.pause()
                hideSettingsPanel()
                bubble?.let { view -> try { windowManager.removeView(view) } catch (_: RuntimeException) { } }
                bubble = null
                bubbleParams = null
                removeMarkers()
            } else {
                mainHandler.removeCallbacks(leaveTargetRunnable)
                mainHandler.postDelayed(leaveTargetRunnable, TARGET_EXIT_GRACE_MS)
            }
        }
    }

    override fun onInterrupt() {
        if (::clicker.isInitialized) clicker.stop()
        updateRunState(EngineState.OFF)
    }

    /** Starts a target-app session. Only the round TouchMaster bubble is shown initially. */
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
        targetWasForeground = false
        activeSettings = settings
        activePoints.clear()
        loadProfile(targetPackage, points, settings)
        return try {
            createBubble()
            renderMarkers()
            saveProfile()
            true
        } catch (_: RuntimeException) {
            removePanel()
            false
        }
    }

    private fun createBubble() {
        val size = dp(bubbleSizeDp)
        val view = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(ACCENT_DARK, 0xFF7A5CFF.toInt()),
            ).apply { cornerRadius = size.toFloat() }
            elevation = dp(10).toFloat()
            contentDescription = "TouchMaster denetim menüsünü aç"
            isClickable = true
            setOnClickListener { toggleSettingsPanel() }
            addView(ImageView(this@TouchMasterAccessibilityService).apply {
                setImageResource(com.touchmaster.pro.R.drawable.ic_launcher)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = null
            }, FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER))
        }
        val metrics = resources.displayMetrics
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (bubbleXFraction * (metrics.widthPixels - size).coerceAtLeast(0)).roundToInt()
            y = (bubbleYFraction * (metrics.heightPixels - size).coerceAtLeast(0)).roundToInt().coerceAtLeast(dp(24))
        }
        bubble = view
        bubbleParams = params
        view.alpha = bubbleAlpha
        val down = IntArray(2)
        val origin = IntArray(2)
        var moved = false
        view.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    moved = false
                    down[0] = event.rawX.roundToInt()
                    down[1] = event.rawY.roundToInt()
                    origin[0] = params.x
                    origin[1] = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX.roundToInt() - down[0]
                    val dy = event.rawY.roundToInt() - down[1]
                    val slop = ViewConfiguration.get(this).scaledTouchSlop
                    if (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop) moved = true
                    if (moved) {
                        val maxX = (resources.displayMetrics.widthPixels - size).coerceAtLeast(0)
                        val maxY = (resources.displayMetrics.heightPixels - size).coerceAtLeast(dp(24))
                        params.x = (origin[0] + dx).coerceIn(0, maxX)
                        params.y = (origin[1] + dy).coerceIn(dp(24), maxY)
                        updateBubbleLayout()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        bubbleXFraction = params.x.toFloat() / (resources.displayMetrics.widthPixels - size).coerceAtLeast(1)
                        bubbleYFraction = params.y.toFloat() / (resources.displayMetrics.heightPixels - size).coerceAtLeast(1)
                        saveProfile()
                    } else target.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
        windowManager.addView(view, params)
    }

    private fun toggleSettingsPanel() {
        if (panelAttached) hideSettingsPanel() else showSettingsPanel()
    }

    private fun showSettingsPanel() {
        if (targetPackage == null) return
        if (panel == null) {
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = rounded(CARD, 22)
                elevation = dp(12).toFloat()
            }
            panel = root
            buildPanel(root)
            val metrics = resources.displayMetrics
            val width = minOf(dp(PANEL_WIDTH_DP), metrics.widthPixels.coerceAtLeast(1))
            val height = minOf(dp(620), metrics.heightPixels.coerceAtLeast(1))
            panelParams = WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = (panelXFraction * (metrics.widthPixels - width).coerceAtLeast(0)).roundToInt()
                val maxY = (metrics.heightPixels - height).coerceAtLeast(0)
                val minY = dp(24).coerceAtMost(maxY)
                y = (panelYFraction * maxY).roundToInt().coerceIn(minY, maxY)
            }
        }
        val view = panel ?: return
        val params = panelParams ?: return
        view.alpha = panelAlpha
        try {
            if (!panelAttached) {
                windowManager.addView(view, params)
                panelAttached = true
            }
            updatePanelContent()
        } catch (_: RuntimeException) {
            Toast.makeText(this, "Ayar menüsü gösterilemedi. Ekran üstü iznini kontrol edin.", Toast.LENGTH_LONG).show()
        }
    }

    private fun hideSettingsPanel() {
        val view = panel ?: return
        if (!panelAttached) return
        try { windowManager.removeView(view) } catch (_: RuntimeException) { }
        panelAttached = false
    }

    private fun buildPanel(root: LinearLayout) {
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleColumn.addView(label("TOUCHMASTER · DENETİM", 10, ACCENT, bold = true))
        titleColumn.addView(label(targetLabel, 17, WHITE, bold = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        header.addView(titleColumn, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(actionText("⌄", 36, 36, PANEL_BUTTON) { hideSettingsPanel() }, marginParams(36, 36, start = 4))
        header.addView(actionText("×", 36, 36, PANEL_BUTTON) { removePanel() }, marginParams(36, 36, start = 4))

        val dragStart = IntArray(2)
        val origin = IntArray(2)
        var moved = false
        titleColumn.contentDescription = "Menüyü taşımak için sürükleyin; dokununca menü küçülür."
        titleColumn.setOnClickListener { hideSettingsPanel() }
        titleColumn.setOnTouchListener { view, event ->
            val params = panelParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    moved = false
                    dragStart[0] = event.rawX.roundToInt()
                    dragStart[1] = event.rawY.roundToInt()
                    origin[0] = params.x
                    origin[1] = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX.roundToInt() - dragStart[0]
                    val dy = event.rawY.roundToInt() - dragStart[1]
                    val slop = ViewConfiguration.get(this).scaledTouchSlop
                    if (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop) moved = true
                    val metrics = resources.displayMetrics
                    val maxX = (metrics.widthPixels - params.width).coerceAtLeast(0)
                    val maxY = (metrics.heightPixels - params.height).coerceAtLeast(0)
                    val minY = dp(24).coerceAtMost(maxY)
                    params.x = (origin[0] - dx).coerceIn(0, maxX)
                    params.y = (origin[1] + dy).coerceIn(minY, maxY)
                    updatePanelLayout()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        val metrics = resources.displayMetrics
                        panelXFraction = params.x.toFloat() / (metrics.widthPixels - params.width).coerceAtLeast(1)
                        panelYFraction = params.y.toFloat() / (metrics.heightPixels - params.height).coerceAtLeast(1)
                        saveProfile()
                    } else view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
        root.addView(header)
        root.addView(divider())

        val scroll = ScrollView(this).apply { isFillViewport = false; overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(6))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val statusRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        statusText = label("HAZIR · Bir hedef ekleyin", 12, ACCENT, bold = true)
        statusRow.addView(statusText, LinearLayout.LayoutParams(0, dp(34), 1f))
        targetCountText = label("0 hedef", 12, MUTED)
        statusRow.addView(targetCountText)
        content.addView(statusRow)

        content.addView(sectionTitle("HEDEF NOKTALARI"))
        val targetRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val targetInfo = label("Numaralı işaretçiyi ekranda sürükleyin", 12, MUTED)
        targetRow.addView(targetInfo, LinearLayout.LayoutParams(0, dp(42), 1f))
        targetRow.addView(actionText("+ HEDEF EKLE", 116, 40, ACCENT_DARK) { addTarget() })
        content.addView(targetRow)
        content.addView(label("İşaretçiyi uzun basılı tutarak kaldırabilirsiniz.", 11, MUTED))

        content.addView(sectionTitle("TIKLAMA DAVRANIŞI"))
        intervalText = label("", 12, WHITE, bold = true)
        content.addView(stepperRow("Tıklama aralığı", intervalText!!, "−", "＋", { changeInterval(-50) }, { changeInterval(50) }))
        content.addView(label("Çoklu hedeflerde gerçek aralık, bir turun tamamlanma süresinden kısa olmaz.", 10, MUTED))
        pressText = label("", 12, WHITE, bold = true)
        content.addView(stepperRow("Basılı tutma süresi", pressText!!, "−", "＋", { changePress(-10) }, { changePress(10) }))
        repeatText = label("", 12, WHITE, bold = true)
        content.addView(stepperRow("Tekrar", repeatText!!, "−", "＋", { changeRepeats(-1) }, { changeRepeats(1) }))

        content.addView(sectionTitle("GÖRÜNÜM VE KONUM"))
        addSlider(content, "Balon saydamlığı", 35, 100, (bubbleAlpha * 100).roundToInt(), { "$it%" }) {
            bubbleAlpha = it / 100f
            bubble?.alpha = bubbleAlpha
            saveProfile()
        }
        addSlider(content, "Balon boyutu", 44, 80, bubbleSizeDp, { "$it dp" }) {
            bubbleSizeDp = it
            resizeBubble()
            saveProfile()
        }
        addSlider(content, "Hedef saydamlığı", 25, 100, (markerAlpha * 100).roundToInt(), { "$it%" }) {
            markerAlpha = it / 100f
            markerViews.forEach { marker -> marker.alpha = markerAlpha }
            saveProfile()
        }
        addSlider(content, "Hedef boyutu", 28, 72, markerSizeDp, { "$it dp" }) {
            markerSizeDp = it
            renderMarkers()
            saveProfile()
        }
        addSlider(content, "Menü saydamlığı", 55, 100, (panelAlpha * 100).roundToInt(), { "$it%" }) {
            panelAlpha = it / 100f
            panel?.alpha = panelAlpha
            saveProfile()
        }
        content.addView(label("Balonu taşımak için balonu, menüyü taşımak için üst başlığı sürükleyin.", 11, MUTED))

        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val compact = isCompactWidth()
        runButton = actionText(if (compact) "Başlat" else "▶  Başlat", 0, 48, ACCENT_DARK) { toggleRun() }.apply { gravity = Gravity.CENTER }
        controls.addView(runButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        val stopWidth = if (compact) 78 else 96
        controls.addView(actionText(if (compact) "Durdur" else "■  Durdur", stopWidth, 48, STOP_DARK) { stopRun() }, marginParams(stopWidth, 48, start = if (compact) 6 else 8))
        content.addView(spacer(12))
        content.addView(controls)
    }

    private fun sectionTitle(text: String) = label(text, 10, ACCENT, bold = true).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)).apply { topMargin = dp(8) }
    }

    private fun stepperRow(
        title: String,
        value: TextView,
        minus: String,
        plus: String,
        onMinus: () -> Unit,
        onPlus: () -> Unit,
    ): LinearLayout {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val compact = isCompactWidth()
        val buttonWidth = if (compact) 30 else 36
        val valueWidth = if (compact) 60 else 82
        row.addView(label(title, 13, WHITE), LinearLayout.LayoutParams(0, dp(42), 1f))
        row.addView(actionText(minus, buttonWidth, 34, PANEL_BUTTON, onMinus))
        value.gravity = Gravity.CENTER
        row.addView(value, LinearLayout.LayoutParams(dp(valueWidth), dp(38)))
        row.addView(actionText(plus, buttonWidth, 34, PANEL_BUTTON, onPlus))
        return row
    }

    private fun addSlider(
        parent: LinearLayout,
        title: String,
        min: Int,
        max: Int,
        current: Int,
        format: (Int) -> String,
        onChanged: (Int) -> Unit,
    ) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val labelRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        labelRow.addView(label(title, 12, WHITE), LinearLayout.LayoutParams(0, dp(26), 1f))
        val valueLabel = label(format(current.coerceIn(min, max)), 12, ACCENT, bold = true)
        labelRow.addView(valueLabel)
        row.addView(labelRow)
        row.addView(SeekBar(this).apply {
            this.max = max - min
            progress = current.coerceIn(min, max) - min
            progressTintList = android.content.res.ColorStateList.valueOf(ACCENT)
            thumbTintList = android.content.res.ColorStateList.valueOf(ACCENT)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = min + progress
                    valueLabel.text = format(value)
                    if (fromUser) onChanged(value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })
        parent.addView(row)
    }

    private fun addTarget() {
        val id = (activePoints.maxOfOrNull { it.id } ?: 0L) + 1L
        activePoints += PointEntry(id, 0.5f, 0.5f, id.toString())
        renderMarkers()
        syncRunningConfiguration()
        updatePanelContent()
        saveProfile()
        Toast.makeText(this, "Hedef eklendi. Numaralı daireyi istediğiniz noktaya sürükleyin.", Toast.LENGTH_SHORT).show()
    }

    private fun removeTarget(index: Int) {
        if (index !in activePoints.indices) return
        val label = activePoints[index].label.ifBlank { (index + 1).toString() }
        activePoints.removeAt(index)
        renderMarkers()
        syncRunningConfiguration()
        updatePanelContent()
        saveProfile()
        Toast.makeText(this, "Hedef $label kaldırıldı.", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun renderMarkers() {
        removeMarkers()
        if (targetPackage == null) return
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
                alpha = markerAlpha
                contentDescription = "Hedef ${index + 1}. Sürükleyin; kaldırmak için uzun basın."
                isClickable = true
                setOnClickListener { setStatus("Hedef seçildi · sürükleyerek konumlandırın", ACCENT) }
            }
            val size = dp(markerSizeDp)
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
            var downRawX = 0f
            var downRawY = 0f
            val longPressRunnable = Runnable { longPressHandled = true; removeTarget(index) }
            pendingMarkerLongPresses += longPressRunnable
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
                        val slop = ViewConfiguration.get(this).scaledTouchSlop
                        if (kotlin.math.abs(event.rawX - downRawX) > slop || kotlin.math.abs(event.rawY - downRawY) > slop) {
                            markerMoved = true
                            mainHandler.removeCallbacks(longPressRunnable)
                            pendingMarkerLongPresses.remove(longPressRunnable)
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
                        pendingMarkerLongPresses.remove(longPressRunnable)
                        if (!longPressHandled && !markerMoved) view.performClick()
                        if (markerMoved) {
                            saveProfile()
                            syncRunningConfiguration()
                        }
                        updatePanelContent()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        mainHandler.removeCallbacks(longPressRunnable)
                        pendingMarkerLongPresses.remove(longPressRunnable)
                        true
                    }
                    else -> true
                }
            }
            try {
                windowManager.addView(marker, params)
                markerViews += marker
            } catch (_: RuntimeException) {
                Toast.makeText(this, "Hedef işaretçisi gösterilemedi. Ekran üstü iznini kontrol edin.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun removeMarkers() {
        pendingMarkerLongPresses.forEach { mainHandler.removeCallbacks(it) }
        pendingMarkerLongPresses.clear()
        markerViews.forEach { marker -> try { windowManager.removeView(marker) } catch (_: RuntimeException) { } }
        markerViews.clear()
    }

    private fun toggleRun() {
        if (activePoints.isEmpty()) {
            setStatus("Önce + HEDEF EKLE ile hedef ekleyin", ACCENT)
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

    private fun syncRunningConfiguration() {
        if (!::clicker.isInitialized || clicker.state.value != EngineState.RUNNING) return
        if (activePoints.isEmpty()) {
            clicker.stop()
            return
        }
        val width = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val height = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        clicker.updateConfiguration(activePoints.toList(), activeSettings, width, height)
    }

    private fun changeInterval(delta: Long) {
        activeSettings = activeSettings.copy(intervalMs = (activeSettings.intervalMs + delta).coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS))
        syncRunningConfiguration()
        saveProfile()
        updatePanelContent()
    }

    private fun changePress(delta: Long) {
        activeSettings = activeSettings.copy(pressMs = (activeSettings.pressMs + delta).coerceIn(MIN_PRESS_MS, MAX_PRESS_MS))
        syncRunningConfiguration()
        saveProfile()
        updatePanelContent()
    }

    private fun changeRepeats(delta: Int) {
        val next = (activeSettings.repetitions + delta).coerceIn(0, 100_000)
        activeSettings = activeSettings.copy(repetitions = next)
        syncRunningConfiguration()
        saveProfile()
        updatePanelContent()
    }

    private fun updateRunState(state: EngineState) {
        if (panel == null) return
        when (state) {
            EngineState.RUNNING -> { setStatus("ÇALIŞIYOR · Dokununca duraklatılır", ACCENT); runButton?.text = if (isCompactWidth()) "Duraklat" else "Ⅱ  Duraklat" }
            EngineState.PAUSED -> { setStatus("DURAKLATILDI · Devam etmek için dokunun", WARNING); runButton?.text = if (isCompactWidth()) "Devam et" else "▶  Devam et" }
            EngineState.ERROR -> { setStatus("HATA · Erişilebilirlik servisini kontrol edin", ERROR); runButton?.text = if (isCompactWidth()) "Yeniden dene" else "▶  Yeniden dene" }
            else -> {
                setStatus(if (activePoints.isEmpty()) "HAZIR · Bir hedef ekleyin" else "HAZIR · Başlatmaya hazır", ACCENT)
                runButton?.text = if (isCompactWidth()) "Başlat" else "▶  Başlat"
            }
        }
    }

    private fun updatePanelContent() {
        targetCountText?.text = "${activePoints.size} hedef"
        intervalText?.text = "${activeSettings.intervalMs} ms"
        pressText?.text = "${activeSettings.pressMs} ms"
        repeatText?.text = if (activeSettings.repetitions == 0) "Süresiz" else "${activeSettings.repetitions} kez"
        if (clicker.state.value != EngineState.RUNNING && clicker.state.value != EngineState.PAUSED) updateRunState(clicker.state.value)
    }

    fun removePanel() {
        mainHandler.removeCallbacks(leaveTargetRunnable)
        if (::clicker.isInitialized) clicker.stop()
        removeMarkers()
        bubble?.let { view -> try { windowManager.removeView(view) } catch (_: RuntimeException) { } }
        bubble = null
        bubbleParams = null
        panel?.let { view -> try { windowManager.removeView(view) } catch (_: RuntimeException) { } }
        panel = null
        panelParams = null
        panelAttached = false
        statusText = null
        targetCountText = null
        intervalText = null
        pressText = null
        repeatText = null
        runButton = null
        activePoints.clear()
        targetPackage = null
        lastForegroundPackage = null
        targetWasForeground = false
    }

    private fun updateBubbleLayout() {
        val view = bubble ?: return
        val params = bubbleParams ?: return
        try { windowManager.updateViewLayout(view, params) } catch (_: RuntimeException) { }
    }

    private fun resizeBubble() {
        val view = bubble ?: return
        val params = bubbleParams ?: return
        val size = dp(bubbleSizeDp)
        params.width = size
        params.height = size
        view.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(ACCENT_DARK, 0xFF7A5CFF.toInt()),
        ).apply { cornerRadius = size.toFloat() }
        val metrics = resources.displayMetrics
        params.x = (bubbleXFraction * (metrics.widthPixels - size).coerceAtLeast(0)).roundToInt()
        params.y = (bubbleYFraction * (metrics.heightPixels - size).coerceAtLeast(dp(24))).roundToInt().coerceAtLeast(dp(24))
        try { windowManager.updateViewLayout(view, params) } catch (_: RuntimeException) { }
    }

    private fun updatePanelLayout() {
        val view = panel ?: return
        val params = panelParams ?: return
        try { windowManager.updateViewLayout(view, params) } catch (_: RuntimeException) { }
    }

    private fun loadProfile(packageName: String, fallbackPoints: List<PointEntry>, defaults: ClickerSettings) {
        resetVisualProfileDefaults()
        val stored = getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE).getString("profile:$packageName", null)
        if (stored == null) {
            activePoints.clear()
            activePoints.addAll(fallbackPoints)
            activeSettings = defaults
            return
        }
        try {
            val profile = JSONObject(stored)
            bubbleAlpha = profile.optDouble("bubbleAlpha", bubbleAlpha.toDouble()).toFloat().coerceIn(.35f, 1f)
            bubbleSizeDp = profile.optInt("bubbleSizeDp", bubbleSizeDp).coerceIn(44, 80)
            bubbleXFraction = profile.optDouble("bubbleX", bubbleXFraction.toDouble()).toFloat().coerceIn(0f, 1f)
            bubbleYFraction = profile.optDouble("bubbleY", bubbleYFraction.toDouble()).toFloat().coerceIn(0f, 1f)
            markerAlpha = profile.optDouble("markerAlpha", markerAlpha.toDouble()).toFloat().coerceIn(.25f, 1f)
            markerSizeDp = profile.optInt("markerSizeDp", markerSizeDp).coerceIn(28, 72)
            panelAlpha = profile.optDouble("panelAlpha", panelAlpha.toDouble()).toFloat().coerceIn(.55f, 1f)
            panelXFraction = profile.optDouble("panelX", panelXFraction.toDouble()).toFloat().coerceIn(0f, 1f)
            panelYFraction = profile.optDouble("panelY", panelYFraction.toDouble()).toFloat().coerceIn(0f, 1f)
            activeSettings = defaults.copy(
                intervalMs = profile.optLong("intervalMs", defaults.intervalMs).coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS),
                pressMs = profile.optLong("pressMs", defaults.pressMs).coerceIn(MIN_PRESS_MS, MAX_PRESS_MS),
                repetitions = profile.optInt("repetitions", defaults.repetitions).coerceIn(0, 100_000),
            )
            activePoints.clear()
            val savedPoints = profile.optJSONArray("points")
            if (savedPoints == null) activePoints.addAll(fallbackPoints)
            else for (i in 0 until savedPoints.length()) {
                val point = savedPoints.optJSONObject(i) ?: continue
                activePoints += PointEntry(
                    id = point.optLong("id", (i + 1).toLong()).coerceAtLeast(1L),
                    xFrac = point.optDouble("x", .5).toFloat().coerceIn(0f, 1f),
                    yFrac = point.optDouble("y", .5).toFloat().coerceIn(0f, 1f),
                    label = point.optString("label", (i + 1).toString()),
                )
            }
        } catch (_: Exception) {
            resetVisualProfileDefaults()
            activePoints.clear()
            activePoints.addAll(fallbackPoints)
            activeSettings = defaults
        }
    }

    private fun resetVisualProfileDefaults() {
        bubbleAlpha = 0.96f
        bubbleSizeDp = 58
        bubbleXFraction = 0.94f
        bubbleYFraction = 0.26f
        markerAlpha = 0.86f
        markerSizeDp = 42
        panelAlpha = 0.97f
        panelXFraction = 0.04f
        panelYFraction = 0.12f
    }

    private fun saveProfile() {
        val packageName = targetPackage ?: return
        val profile = JSONObject().apply {
            put("bubbleAlpha", bubbleAlpha)
            put("bubbleSizeDp", bubbleSizeDp)
            put("bubbleX", bubbleXFraction)
            put("bubbleY", bubbleYFraction)
            put("markerAlpha", markerAlpha)
            put("markerSizeDp", markerSizeDp)
            put("panelAlpha", panelAlpha)
            put("panelX", panelXFraction)
            put("panelY", panelYFraction)
            put("intervalMs", activeSettings.intervalMs)
            put("pressMs", activeSettings.pressMs)
            put("repetitions", activeSettings.repetitions)
            put("points", JSONArray().apply {
                activePoints.forEach { point ->
                    put(JSONObject().apply {
                        put("id", point.id)
                        put("x", point.xFrac)
                        put("y", point.yFrac)
                        put("label", point.label)
                    })
                }
            })
        }
        getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE).edit()
            .putString("profile:$packageName", profile.toString())
            .apply()
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
    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radiusDp).toFloat() }
    private fun isCompactWidth() = resources.displayMetrics.widthPixels / resources.displayMetrics.density < 300f
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
