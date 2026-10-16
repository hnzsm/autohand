package com.quqi.autohand.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.View
import kotlin.math.hypot

/** 回放时的全屏透明层：按执行进度把手势轨迹描出来，结束后淡出。不接收任何触摸。 */
class PlaybackTrailView(context: Context) : View(context) {

    private var points: List<Pair<Float, Float>> = emptyList()
    private var isTap = false
    private var progress = 0f
    private var fade = 1f
    private var animator: ValueAnimator? = null

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(9f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val path = Path()

    fun play(pts: List<Pair<Float, Float>>, durationMs: Long) {
        animator?.cancel()
        points = pts
        isTap = pts.size < 2 || run {
            val (sx, sy) = pts.first()
            val (ex, ey) = pts.last()
            hypot((ex - sx).toDouble(), (ey - sy).toDouble()) < TAP_RATIO
        }
        progress = 0f
        fade = 1f
        val total = durationMs + HOLD_MS + FADE_MS
        animator = ValueAnimator.ofFloat(0f, total.toFloat()).apply {
            duration = total
            addUpdateListener {
                val t = it.animatedValue as Float
                progress = (t / durationMs).coerceIn(0f, 1f)
                fade = 1f - ((t - durationMs - HOLD_MS) / FADE_MS).coerceIn(0f, 1f)
                invalidate()
            }
            start()
        }
        invalidate()
    }

    fun stop() {
        animator?.cancel()
        animator = null
        points = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty() || width == 0 || height == 0 || fade <= 0f) return
        strokePaint.color = TRAIL_GRAY
        strokePaint.alpha = (STROKE_ALPHA * fade).toInt()
        headPaint.color = TRAIL_GRAY
        headPaint.alpha = (HEAD_ALPHA * fade).toInt()

        if (isTap) {
            val (x, y) = points.first()
            val cx = x * width
            val cy = y * height
            canvas.drawCircle(cx, cy, dp(15f) * progress, strokePaint)
            canvas.drawCircle(cx, cy, dp(5f), headPaint)
            return
        }

        path.reset()
        val last = progress * (points.size - 1)
        val full = last.toInt()
        path.moveTo(points[0].first * width, points[0].second * height)
        for (i in 1..full.coerceAtMost(points.size - 1)) {
            path.lineTo(points[i].first * width, points[i].second * height)
        }
        val head = if (full < points.size - 1) {
            val frac = last - full
            val a = points[full]
            val b = points[full + 1]
            val hx = (a.first + (b.first - a.first) * frac) * width
            val hy = (a.second + (b.second - a.second) * frac) * height
            path.lineTo(hx, hy)
            hx to hy
        } else {
            val p = points.last()
            p.first * width to p.second * height
        }
        canvas.drawPath(path, strokePaint)
        canvas.drawCircle(head.first, head.second, dp(6f), headPaint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
    )

    companion object {
        private const val TAP_RATIO = 0.012
        private const val HOLD_MS = 250L
        private const val FADE_MS = 400L
        private const val TRAIL_GRAY = 0xFFF2F2F2.toInt()
        private const val STROKE_ALPHA = 0x66
        private const val HEAD_ALPHA = 0x99
    }
}
