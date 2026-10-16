package com.quqi.autohand.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * 录制用的全屏透明层：吃掉触摸、画出轨迹、抬手后把归一化坐标交给外部。
 * 归一化是为了换设备/改分辨率后回放位置不跑偏。
 */
class TrailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Callback {
        fun onGestureEnd(points: List<Pair<Float, Float>>, durationMs: Long)
    }

    var callback: Callback? = null

    private val livePath = Path()
    private val history = mutableListOf<Path>()
    private val rawPoints = mutableListOf<Pair<Float, Float>>()
    private var downTime = 0L
    private var tracking = false

    private val livePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val historyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#12B76A")
    }

    fun addHistory(points: List<Pair<Float, Float>>) {
        if (points.size < 2 || width == 0 || height == 0) return
        history.add(Path().apply {
            moveTo(points.first().first * width, points.first().second * height)
            for (i in 1 until points.size) lineTo(points[i].first * width, points[i].second * height)
        })
        invalidate()
    }

    fun clearHistory() {
        history.clear()
        livePath.reset()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 多指时只跟第一根手指，其余触点忽略
        if (event.actionIndex != 0 && event.actionMasked != MotionEvent.ACTION_UP
            && event.actionMasked != MotionEvent.ACTION_CANCEL
        ) {
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracking = true
                downTime = event.downTime
                rawPoints.clear()
                livePath.reset()
                rawPoints.add(event.x to event.y)
                livePath.moveTo(event.x, event.y)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return true
                rawPoints.add(event.x to event.y)
                livePath.lineTo(event.x, event.y)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!tracking) return true
                tracking = false
                livePath.lineTo(event.x, event.y)
                rawPoints.add(event.x to event.y)
                val duration = (event.eventTime - downTime).coerceAtLeast(1L)
                val normalized = normalize(rawPoints)
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    history.add(Path(livePath))
                    callback?.onGestureEnd(normalized, duration)
                }
                livePath.reset()
                performClick()
            }
        }
        invalidate()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun normalize(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        if (width == 0 || height == 0 || points.isEmpty()) return emptyList()
        val stepSize = (points.size / MAX_POINTS).coerceAtLeast(1)
        val picked = ArrayList<Pair<Float, Float>>(MAX_POINTS + 2)
        points.forEachIndexed { i, p ->
            if (i == 0 || i == points.size - 1 || i % stepSize == 0) {
                picked.add(p.first / width to p.second / height)
            }
        }
        val first = picked.first()
        val last = picked.last()
        return if (hypot((last.first - first.first).toDouble(), (last.second - first.second).toDouble()) < TAP_RATIO) {
            listOf(first, first)
        } else {
            picked
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        history.forEach { canvas.drawPath(it, historyPaint) }
        if (tracking) canvas.drawPath(livePath, livePaint)
    }

    companion object {
        private const val MAX_POINTS = 14
        private const val TAP_RATIO = 0.012
    }
}
