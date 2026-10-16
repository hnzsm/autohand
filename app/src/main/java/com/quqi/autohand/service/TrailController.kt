package com.quqi.autohand.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.view.WindowManager
import com.quqi.autohand.model.GestureStep
import com.quqi.autohand.ui.PlaybackTrailView

/** 回放轨迹的悬浮窗宿主：全屏、不挡触摸，随服务停止而移除。 */
class TrailController(private val service: AccessibilityService) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: PlaybackTrailView? = null

    fun show(step: GestureStep) {
        val v = view ?: PlaybackTrailView(service).also { new ->
            runCatching { wm.addView(new, params()) }
            view = new
        }
        v.play(step.points, step.durationMs.coerceIn(MIN_MS, MAX_MS))
    }

    fun hide() {
        view?.stop()
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )

    companion object {
        private const val MIN_MS = 60L
        private const val MAX_MS = 120_000L
    }
}
