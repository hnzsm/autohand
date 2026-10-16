package com.quqi.autohand.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.quqi.autohand.R
import com.quqi.autohand.databinding.ViewBallBinding
import com.quqi.autohand.util.Prefs
import com.quqi.autohand.util.Screens
import kotlin.math.abs
import kotlin.math.ceil

/** 悬浮球：可拖动、贴边，点击展开控制面板。 */
class BallController(private val service: AccessibilityService) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var binding: ViewBallBinding? = null
    private var params: WindowManager.LayoutParams? = null
    private var onClick: (() -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var downElapsed = 0L

    val isShowing: Boolean get() = binding != null

    private var state = LoopEngine.State.IDLE

    /** 等待下一次手势还剩多少毫秒，由服务接到 LoopEngine 上。 */
    var remainingProvider: () -> Long = { 0L }

    private val ticker = object : Runnable {
        override fun run() {
            render()
            binding?.root?.postDelayed(this, TICK)
        }
    }

    fun show(onClick: () -> Unit) {
        if (binding != null) return
        this.onClick = onClick
        val size = dp(52f).toInt()
        val screen = Screens.size(service)
        val view = ViewBallBinding.inflate(LayoutInflater.from(service))
        val p = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            touchFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = Prefs.ballX(service).coerceIn(0, screen.x - size)
            y = Prefs.ballY(service).coerceIn(0, screen.y - size)
        }
        view.root.setOnTouchListener { v, e -> onTouch(v, e) }
        wm.addView(view.root, p)
        binding = view
        params = p
        applyState(LoopEngine.State.IDLE)
        view.root.postDelayed(ticker, TICK)
    }

    fun hide() {
        binding?.let {
            it.root.removeCallbacks(ticker)
            runCatching { wm.removeView(it.root) }
        }
        binding = null
        params = null
    }

    /** 屏幕常亮跟着运行状态和开关走。 */
    fun updateKeepScreenOn(enable: Boolean) {
        val b = binding ?: return
        val p = params ?: return
        p.flags = touchFlags() or if (enable) WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0
        runCatching { wm.updateViewLayout(b.root, p) }
    }

    fun applyState(state: LoopEngine.State) {
        this.state = state
        val b = binding ?: return
        val color = when (state) {
            LoopEngine.State.IDLE -> R.color.ball_idle
            LoopEngine.State.RUNNING -> R.color.ball_running
            LoopEngine.State.PAUSED -> R.color.ball_paused
        }
        // 底色始终是圆形 drawable，只换 tint，否则 setBackgroundResource(color) 会画成方块
        b.ballRoot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(service, color))
        render()
    }

    /** 运行中且在等待时显示倒计时秒数，其余时间显示播放/暂停符号。 */
    private fun render() {
        val b = binding ?: return
        val left = if (state == LoopEngine.State.RUNNING) remainingProvider() else 0L
        b.ballIcon.text = if (left > 0) "${ceil(left / 1000.0).toInt()}s" else when (state) {
            LoopEngine.State.IDLE -> "▶"
            LoopEngine.State.RUNNING -> "❚❚"
            LoopEngine.State.PAUSED -> "▶"
        }
    }

    private fun touchFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private fun onTouch(view: View, event: MotionEvent): Boolean {
        val b = binding ?: return false
        val p = params ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                startX = p.x
                startY = p.y
                downElapsed = event.eventTime
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && (abs(dx) > slop() || abs(dy) > slop())) dragging = true
                if (dragging) {
                    val screen = Screens.size(service)
                    p.x = (startX + dx).toInt().coerceIn(0, screen.x - b.root.width)
                    p.y = (startY + dy).toInt().coerceIn(0, screen.y - b.root.height)
                    runCatching { wm.updateViewLayout(b.root, p) }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    snapToEdge(b, p)
                    Prefs.setBallPosition(service, p.x, p.y)
                } else if (event.eventTime - downElapsed < LONG_PRESS) {
                    onClick?.invoke()
                }
            }

            MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    private fun snapToEdge(b: ViewBallBinding, p: WindowManager.LayoutParams) {
        val screen = Screens.size(service)
        val edge = screen.x / 2
        p.x = if (p.x + b.root.width / 2 < edge) 0 else screen.x - b.root.width
        runCatching { wm.updateViewLayout(b.root, p) }
    }

    private fun slop() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 8f, service.resources.displayMetrics
    )

    private fun dp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, service.resources.displayMetrics
    )

    private companion object {
        const val LONG_PRESS = 600L
        const val TICK = 200L
    }
}
