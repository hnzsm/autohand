package com.quqi.autohand.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.util.Log
import com.quqi.autohand.model.GestureStep
import com.quqi.autohand.util.Screens
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** 把录制下来的 [GestureStep] 通过无障碍服务注入系统。 */
class GestureRunner(private val service: AccessibilityService) {

    suspend fun perform(step: GestureStep): Boolean {
        val screen = Screens.size(service)
        val path = step.buildPath(screen)
        val duration = step.durationMs.coerceIn(MIN_MS, MAX_MS)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
            .build()

        return suspendCancellableCoroutine { cont ->
            var finished = false
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (finished || !cont.isActive) return
                    finished = true
                    cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (finished || !cont.isActive) return
                    finished = true
                    Log.w(TAG, "gesture cancelled: ${step.describe()}")
                    cont.resume(false)
                }
            }
            val accepted = try {
                service.dispatchGesture(gesture, callback, null)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "bad gesture", e)
                false
            }
            if (!accepted && !finished && cont.isActive) cont.resume(false)
        }
    }

    companion object {
        private const val TAG = "GestureRunner"
        private const val MIN_MS = 60L
        private const val MAX_MS = 120_000L
    }
}
