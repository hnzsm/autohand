package com.quqi.autohand.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import com.quqi.autohand.databinding.ViewRecordBinding
import com.quqi.autohand.model.GestureStep
import com.quqi.autohand.ui.TrailView

/** 全屏透明录制层：录一段存一段，可连续录多段，最后合成一个脚本。 */
class RecordController(private val service: AccessibilityService) {

    interface Finish {
        fun onSaved(steps: List<GestureStep>)
        fun onCancel()
    }

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var binding: ViewRecordBinding? = null
    private val steps = mutableListOf<GestureStep>()
    private var lastUpElapsed = 0L

    val isRecording: Boolean get() = binding != null

    fun start(finish: Finish) {
        if (isRecording) return
        steps.clear()
        lastUpElapsed = 0L
        val view = ViewRecordBinding.inflate(LayoutInflater.from(service))
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        view.trail.callback = object : TrailView.Callback {
            override fun onGestureEnd(points: List<Pair<Float, Float>>, durationMs: Long) {
                onCaptured(points, durationMs, view)
            }
        }
        view.btnRecordCancel.setOnClickListener { close(); finish.onCancel() }
        view.btnRecordAgain.setOnClickListener {
            view.trail.clearHistory()
            steps.clear()
            lastUpElapsed = 0L
            updateHint(view)
        }
        view.btnRecordSave.setOnClickListener {
            if (steps.isEmpty()) return@setOnClickListener
            close()
            finish.onSaved(steps.toList())
        }
        wm.addView(view.root, p)
        binding = view
        updateHint(view)
    }

    fun close() {
        binding?.let { runCatching { wm.removeView(it.root) } }
        binding = null
    }

    private fun onCaptured(points: List<Pair<Float, Float>>, durationMs: Long, view: ViewRecordBinding) {
        if (points.isEmpty()) return
        val now = android.os.SystemClock.uptimeMillis()
        // 两段手势之间你停顿的时间，直接当成下一段执行前的等待
        if (steps.isNotEmpty()) {
            val gap = if (lastUpElapsed > 0) (now - lastUpElapsed) else 0L
            val previous = steps.removeAt(steps.size - 1)
            steps.add(previous.copy(waitAfterMs = gap.coerceIn(0L, MAX_GAP)))
        }
        steps.add(GestureStep(points, durationMs))
        lastUpElapsed = now
        updateHint(view)
    }

    private fun updateHint(view: ViewRecordBinding) {
        view.recordHint.text = if (steps.isEmpty()) {
            service.getString(com.quqi.autohand.R.string.record_hint)
        } else {
            "已录 ${steps.size} 段：${steps.joinToString(" → ") { it.describe() }}"
        }
        view.btnRecordSave.alpha = if (steps.isEmpty()) 0.4f else 1f
    }

    private companion object {
        const val MAX_GAP = 8000L
    }
}
