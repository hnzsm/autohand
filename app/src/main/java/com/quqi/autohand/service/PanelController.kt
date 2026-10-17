package com.quqi.autohand.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.quqi.autohand.databinding.ViewPanelBinding
import com.quqi.autohand.util.Screens

private const val REPEAT_INTERVAL_MS = 120L
private const val HOLD_DELAY_MS = 400L

/** 悬浮球点开后的控制面板，本身也是一个无障碍悬浮窗。 */
class PanelController(private val service: AccessibilityService) {

    class Listener {
        var stateProvider: () -> LoopEngine.State = { LoopEngine.State.IDLE }
        var onRecord: () -> Unit = {}
        var onRun: () -> Unit = {}
        var onStop: () -> Unit = {}
        var onPause: () -> Unit = {}
        var onLoopDelta: (Int) -> Unit = {}
        var onGapDelta: (Int) -> Unit = {}
        var onJitterDelta: (Int) -> Unit = {}
        var onScriptDelta: (Int) -> Unit = {}
        var onClose: () -> Unit = {}
        var onOutsideTouch: () -> Unit = {}
    }

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var binding: ViewPanelBinding? = null
    private var params: WindowManager.LayoutParams? = null

    val isShowing: Boolean get() = binding != null

    fun toggle(anchorX: Int, anchorY: Int, listener: Listener) {
        if (isShowing) close() else show(anchorX, anchorY, listener)
    }

    fun show(anchorX: Int, anchorY: Int, listener: Listener) {
        if (isShowing) return
        val view = ViewPanelBinding.inflate(LayoutInflater.from(service))
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = placeX(anchorX)
            y = placeY(anchorY)
        }
        bindEvents(view, listener)
        view.root.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                listener.onOutsideTouch()
                true
            } else {
                false
            }
        }
        wm.addView(view.root, p)
        binding = view
        params = p
    }

    fun close() {
        stopRepeat()
        binding?.let { runCatching { wm.removeView(it.root) } }
        binding = null
        params = null
    }

    fun refresh(
        scriptName: String,
        state: LoopEngine.State,
        round: Int,
        loops: Int,
        gapMs: Long,
        jitterMs: Long,
        scriptCount: Int
    ) {
        val b = binding ?: return
        b.panelScriptName.text = if (scriptCount == 0) "没有脚本" else "$scriptName（$scriptCount 个）"
        b.panelState.text = when (state) {
            LoopEngine.State.IDLE -> "待命"
            LoopEngine.State.RUNNING -> if (loops > 0) "运行中 · $round/$loops 轮" else "运行中 · 第 $round 轮"
            LoopEngine.State.PAUSED -> "已暂停"
        }
        b.btnRun.text = when (state) {
            LoopEngine.State.RUNNING -> service.getString(com.quqi.autohand.R.string.panel_pause)
            LoopEngine.State.PAUSED -> service.getString(com.quqi.autohand.R.string.panel_resume)
            LoopEngine.State.IDLE -> service.getString(com.quqi.autohand.R.string.panel_start)
        }
        b.valueLoop.text = if (loops <= 0) service.getString(com.quqi.autohand.R.string.loop_times_infinite) else "$loops"
        b.valueGap.text = if (gapMs <= 0) "无" else "${gapMs / 1000}s"
        b.valueJitter.text =
            if (jitterMs <= 0) "无" else String.format(java.util.Locale.US, "±%.1fs", jitterMs / 1000.0)
    }

    private fun bindEvents(b: ViewPanelBinding, l: Listener) {
        b.btnRecord.setOnClickListener { l.onRecord() }
        b.btnStop.setOnClickListener { l.onStop() }
        b.btnRun.setOnClickListener {
            when (l.stateProvider()) {
                LoopEngine.State.RUNNING -> l.onPause()
                else -> l.onRun()
            }
        }
        b.btnLoopMinus.setOnClickListener { l.onLoopDelta(-1) }
        b.btnLoopPlus.setOnClickListener { l.onLoopDelta(1) }
        b.btnGapMinus.setOnClickListener { if (!suppressClick) l.onGapDelta(-1) }
        b.btnGapPlus.setOnClickListener { if (!suppressClick) l.onGapDelta(1) }
        bindHoldRepeat(b.btnGapMinus) { l.onGapDelta(-1) }
        bindHoldRepeat(b.btnGapPlus) { l.onGapDelta(1) }
        b.btnJitterMinus.setOnClickListener { if (!suppressClick) l.onJitterDelta(-1) }
        b.btnJitterPlus.setOnClickListener { if (!suppressClick) l.onJitterDelta(1) }
        bindHoldRepeat(b.btnJitterMinus) { l.onJitterDelta(-1) }
        bindHoldRepeat(b.btnJitterPlus) { l.onJitterDelta(1) }
        b.btnPrevScript.setOnClickListener { l.onScriptDelta(-1) }
        b.btnNextScript.setOnClickListener { l.onScriptDelta(1) }
        b.btnHidePanel.setOnClickListener { l.onClose() }
    }

    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatTask: Runnable? = null
    private var suppressClick = false

    /** 按住超过 HOLD_DELAY_MS 后按固定节奏连续触发 step，抬手或面板关闭即停。 */
    private fun bindHoldRepeat(view: View, step: () -> Unit) {
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    suppressClick = false
                    startRepeat(step)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stopRepeat()
            }
            false
        }
    }

    private fun startRepeat(step: () -> Unit) {
        stopRepeat()
        val task = object : Runnable {
            override fun run() {
                suppressClick = true
                step()
                repeatHandler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
        repeatTask = task
        repeatHandler.postDelayed(task, HOLD_DELAY_MS)
    }

    private fun stopRepeat() {
        repeatTask?.let { repeatHandler.removeCallbacks(it) }
        repeatTask = null
    }

    private var stateHint: LoopEngine.State = LoopEngine.State.IDLE

    fun setStateHint(state: LoopEngine.State) {
        stateHint = state
    }

    private fun currentState() = stateHint

    private fun placeX(anchorX: Int): Int {
        val screen = Screens.size(service)
        val width = dp(252f).toInt()
        val ball = dp(52f).toInt()
        return if (anchorX + ball / 2 < screen.x / 2) {
            (anchorX + ball + dp(6f)).toInt().coerceAtMost(screen.x - width)
        } else {
            (anchorX - width - dp(6f)).toInt().coerceAtLeast(0)
        }
    }

    private fun placeY(anchorY: Int): Int {
        val screen = Screens.size(service)
        val height = dp(250f).toInt()
        return (anchorY - dp(30f)).toInt().coerceIn(0, (screen.y - height).coerceAtLeast(0))
    }

    private fun dp(value: Float): Float = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_DIP, value, service.resources.displayMetrics
    )
}
