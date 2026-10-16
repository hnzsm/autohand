package com.quqi.autohand.util

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager

object Screens {

    fun size(context: Context): Point {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            return Point(bounds.width(), bounds.height())
        }
        @Suppress("DEPRECATION")
        val p = Point()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealSize(p)
        if (p.x > 0 && p.y > 0) return p
        @Suppress("DEPRECATION")
        return Point(wm.defaultDisplay.width, wm.defaultDisplay.height)
    }
}
