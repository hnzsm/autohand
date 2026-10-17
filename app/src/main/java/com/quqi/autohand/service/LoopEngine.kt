package com.quqi.autohand.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import com.quqi.autohand.model.GestureStep
import com.quqi.autohand.model.Script
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/** 协程驱动的循环回放器：脚本内各段手势顺序执行，脚本之间按间隔和随机抖动等待。 */
class LoopEngine(service: AccessibilityService) {

    enum class State { IDLE, RUNNING, PAUSED }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val runner = GestureRunner(service)

    var state: State = State.IDLE
        private set
    var round: Int = 0
        private set
    private var job: Job? = null
    private var paused = false

    /** 状态或轮次变化时回调，用于刷新悬浮球、面板和通知。 */
    var onChange: ((State, Int) -> Unit)? = null

    /** 每段手势开始注入前回调，用于描回放轨迹。 */
    var onStep: ((GestureStep) -> Unit)? = null

    private val remainingMs = AtomicLong(0)

    @Volatile
    private var touchHeld = false

    @Volatile
    private var inWait = false

    /** 距离下一次手势还有多少毫秒；正在执行手势或已暂停时为 0，供悬浮球倒计时。 */
    fun remainingWaitMs(): Long = remainingMs.get().coerceAtLeast(0L)

    /**
     * 真手按在屏幕上时冻住等待倒计时，抬手继续。
     * 只在等待阶段接受「按下」，避免手势注入期间的触摸事件把倒计时误冻。
     */
    fun setTouchHeld(held: Boolean) {
        if (held && !inWait) return
        touchHeld = held
    }

    fun start(script: Script, loops: Int, gapMs: Long, jitterMs: Long) {
        job?.cancel()
        if (script.steps.isEmpty()) return
        paused = false
        touchHeld = false
        state = State.RUNNING
        round = 0
        onChange?.invoke(state, round)
        job = scope.launch {
            var failures = 0
            while (isActiveGate()) {
                awaitRunning()
                for (step in script.steps) {
                    awaitRunning()
                    onStep?.invoke(step)
                    val budget = step.durationMs + TIMEOUT_SLACK
                    val ok = withTimeoutOrNull(budget) { runner.perform(step) } == true
                    if (!ok) {
                        failures++
                        Log.w(TAG, "第 $failures 次手势未被系统接受")
                    }
                    if (step.waitAfterMs > 0) sleepInterruptible(step.waitAfterMs)
                }
                round++
                onChange?.invoke(state, round)
                if (loops > 0 && round >= loops) break
                awaitRunning()
                val wait = gapMs + if (jitterMs > 0) Random.nextLong(jitterMs) else 0L
                sleepInterruptible(wait)
            }
            val done = state
            state = State.IDLE
            Log.i(TAG, "循环结束 state=$done rounds=$round failures=$failures")
            onChange?.invoke(state, round)
        }
    }

    fun togglePause() {
        if (state == State.IDLE) return
        paused = !paused
        state = if (paused) State.PAUSED else State.RUNNING
        onChange?.invoke(state, round)
    }

    fun stop() {
        job?.cancel()
        remainingMs.set(0)
        paused = false
        touchHeld = false
        if (state != State.IDLE) {
            state = State.IDLE
            onChange?.invoke(state, round)
        }
    }

    private fun isActiveGate(): Boolean = job?.isCancelled != true

    private suspend fun awaitRunning() {
        while (paused) {
            delay(GATE_TICK)
            if (!isActiveGate()) return
        }
    }

    private suspend fun sleepInterruptible(ms: Long) {
        inWait = true
        try {
            var left = ms
            while (left > 0) {
                if (!isActiveGate()) {
                    remainingMs.set(0)
                    return
                }
                // 暂停时 left 不递减，倒计时自然停住
                awaitRunning()
                // 真手按屏期间同样停住，抬手后从剩下的时间继续
                while (touchHeld) {
                    if (!isActiveGate()) {
                        remainingMs.set(0)
                        return
                    }
                    remainingMs.set(left)
                    delay(GATE_TICK)
                }
                remainingMs.set(left)
                val slice = minOf(left, GATE_TICK)
                delay(slice)
                left -= slice
            }
            remainingMs.set(0)
        } finally {
            inWait = false
        }
    }

    companion object {
        private const val TAG = "LoopEngine"
        private const val GATE_TICK = 150L
        private const val TIMEOUT_SLACK = 4000L
    }
}
