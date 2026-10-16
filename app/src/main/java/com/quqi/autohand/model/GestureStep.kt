package com.quqi.autohand.model

import android.graphics.Path
import android.graphics.Point
import org.json.JSONArray
import org.json.JSONObject

/** 一次录制下来的手势：归一化轨迹（0~1）+ 实际用时 + 之后要等待多久。 */
data class GestureStep(
    val points: List<Pair<Float, Float>>,
    val durationMs: Long,
    val waitAfterMs: Long = 0L
) {
    val isTap: Boolean
        get() {
            if (points.size < 2) return true
            val (sx, sy) = points.first()
            val (ex, ey) = points.last()
            return Math.hypot((ex - sx).toDouble(), (ey - sy).toDouble()) < 0.012
        }

    fun describe(): String {
        val dur = if (durationMs >= 1000) "%.1fs".format(durationMs / 1000.0) else "${durationMs}ms"
        return if (isTap) {
            val (x, y) = points.first()
            "点击 ${pct(x)}, ${pct(y)} · $dur"
        } else {
            val (x, y) = points.first()
            val (ex, ey) = points.last()
            "滑动 ${pct(x)},${pct(y)} → ${pct(ex)},${pct(ey)} · $dur"
        }
    }

    private fun pct(v: Float) = "${(v * 100).toInt()}%"

    fun buildPath(screen: Point): Path {
        val path = Path()
        val sx = points.first().first * screen.x
        val sy = points.first().second * screen.y
        path.moveTo(sx, sy)
        if (isTap) {
            // 零长度轨迹在部分 ROM 上会被丢弃，补一个极短位移
            path.lineTo(sx + 1f, sy + 1f)
        } else {
            for (i in 1 until points.size) path.lineTo(points[i].first * screen.x, points[i].second * screen.y)
        }
        return path
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("p", JSONArray().apply {
            points.forEach { pt ->
                put(JSONArray().put(pt.first.toDouble()).put(pt.second.toDouble()))
            }
        })
        put("d", durationMs)
        if (waitAfterMs > 0) put("w", waitAfterMs)
    }

    companion object {
        fun fromJson(o: JSONObject): GestureStep? {
            val arr = o.optJSONArray("p") ?: return null
            val pts = ArrayList<Pair<Float, Float>>(arr.length())
            for (i in 0 until arr.length()) {
                val pair = arr.optJSONArray(i) ?: continue
                pts.add(pair.optDouble(0).toFloat() to pair.optDouble(1).toFloat())
            }
            if (pts.isEmpty()) return null
            return GestureStep(pts, o.optLong("d", 200L), o.optLong("w", 0L))
        }
    }
}
