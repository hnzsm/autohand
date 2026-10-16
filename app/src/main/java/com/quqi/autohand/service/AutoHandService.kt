package com.quqi.autohand.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.quqi.autohand.R
import com.quqi.autohand.model.GestureStep
import com.quqi.autohand.model.Script
import com.quqi.autohand.util.Prefs

/**
 * 唯一的运行时载体：无障碍服务既能跨应用注入手势，又能挂 TYPE_ACCESSIBILITY_OVERLAY 悬浮窗。
 * 界面只负责开关，真正干活的是这里持有的三个控制器和 [LoopEngine]。
 */
class AutoHandService : AccessibilityService() {

    private var ball: BallController? = null
    private var panel: PanelController? = null
    private var recorder: RecordController? = null
    private var trail: TrailController? = null
    private var engine: LoopEngine? = null

    private val panelListener = PanelController.Listener().apply {
        stateProvider = { engine?.state ?: LoopEngine.State.IDLE }
        onRecord = { startRecording() }
        onRun = { runOrToggle() }
        onStop = { stopRun() }
        onPause = { engine?.togglePause(); refreshUi() }
        onLoopDelta = { delta ->
            Prefs.setLoopCount(this@AutoHandService, (Prefs.loopCount(this@AutoHandService) + delta).coerceIn(0, 999))
            refreshUi()
        }
        onGapDelta = { delta ->
            Prefs.setGapMs(this@AutoHandService, (Prefs.gapMs(this@AutoHandService) + delta * Prefs.GAP_STEP_MS).coerceIn(Prefs.GAP_MIN_MS, Prefs.GAP_MAX_MS))
            refreshUi()
        }
        onJitterToggle = {
            Prefs.setJitter(this@AutoHandService, !Prefs.jitterEnabled(this@AutoHandService))
            refreshUi()
        }
        onScriptDelta = { delta -> stepScript(delta) }
        onClose = { panel?.close() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ball = BallController(this).apply {
            remainingProvider = { engine?.remainingWaitMs() ?: 0L }
        }
        panel = PanelController(this)
        recorder = RecordController(this)
        trail = TrailController(this)
        createChannel()
        if (Prefs.ballEnabled(this)) ball?.show(::onBallClick)
        Log.i(TAG, "无障碍服务已连接，悬浮球=" + Prefs.ballEnabled(this))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        stopRun()
        panel?.close()
        recorder?.close()
        ball?.hide()
        trail?.hide()
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    /** 通知栏按钮打进来的指令。 */
    fun handleControl(action: String?) {
        when (action) {
            ACT_PAUSE -> { engine?.togglePause(); refreshUi() }
            ACT_STOP -> stopRun()
        }
    }

    fun refreshBallVisibility() {        if (Prefs.ballEnabled(this)) {
            if (ball?.isShowing != true) ball?.show(::onBallClick)
        } else {
            panel?.close()
            ball?.hide()
        }
    }

    private fun onBallClick() {
        val p = panel ?: return
        if (p.isShowing) {
            p.close()
        } else {
            p.show(Prefs.ballX(this), Prefs.ballY(this), panelListener)
            refreshUi()
        }
    }

    private fun stepScript(delta: Int) {
        val list = Prefs.readScripts(this)
        if (list.isEmpty()) return
        val current = list.indexOfFirst { it.id == Prefs.selectedId(this) }.coerceAtLeast(0)
        Prefs.setSelected(this, list[(current + delta).coerceIn(0, list.size - 1)].id)
        refreshUi()
    }

    private fun startRecording() {
        panel?.close()
        recorder?.start(object : RecordController.Finish {
            override fun onSaved(steps: List<GestureStep>) {
                val context = this@AutoHandService
                val list = Prefs.readScripts(context)
                // 选中脚本还是空的就覆盖它，否则新建一个
                val target = Prefs.resolveSelected(context)
                val script = if (target != null && target.steps.isEmpty()) target
                else Script(name = Prefs.nextScriptName(context))
                script.steps.clear()
                script.steps.addAll(steps)
                if (list.none { it.id == script.id }) list.add(script)
                Prefs.writeScripts(context, list)
                Prefs.setSelected(context, script.id)
                refreshUi()
            }

            override fun onCancel() = refreshUi()
        })
    }

    private fun runOrToggle() {
        val e = engine ?: LoopEngine(this).also { new ->
            new.onChange = { _, round -> onRound(new, round) }
            new.onStep = { trail?.show(it) }
            engine = new
        }
        when (e.state) {
            LoopEngine.State.IDLE -> {
                val script = Prefs.resolveSelected(this) ?: return
                if (script.steps.isEmpty()) return
                e.start(script, Prefs.loopCount(this), gapMs(), jitterMs())
            }

            else -> e.togglePause()
        }
        ball?.updateKeepScreenOn(Prefs.keepScreenOn(this) && e.state != LoopEngine.State.IDLE)
        refreshUi()
    }

    private fun stopRun() {
        val wasRunning = engine?.state != LoopEngine.State.IDLE
        engine?.stop()
        engine = null
        trail?.hide()
        if (wasRunning) {
            ball?.updateKeepScreenOn(false)
            NotificationManagerCompat.from(this).cancel(NOTIFY_ID)
        }
        refreshUi()
    }

    private fun gapMs(): Long = Prefs.gapMs(this)

    private fun jitterMs(): Long =
        if (!Prefs.jitterEnabled(this)) 0L
        else (gapMs() * 0.3f).toLong().coerceIn(MIN_JITTER, MAX_JITTER)

    private fun onRound(e: LoopEngine, round: Int) {
        if (e.state == LoopEngine.State.IDLE) {
            ball?.updateKeepScreenOn(false)
            NotificationManagerCompat.from(this).cancel(NOTIFY_ID)
        } else {
            showNotification(round)
        }
        refreshUi()
    }

    private fun refreshUi() {
        ball?.applyState(engine?.state ?: LoopEngine.State.IDLE)
        val p = panel ?: return
        if (!p.isShowing) return
        val script = Prefs.resolveSelected(this)
        p.refresh(
            scriptName = script?.name ?: "-",
            state = engine?.state ?: LoopEngine.State.IDLE,
            round = engine?.round ?: 0,
            loops = Prefs.loopCount(this),
            gapMs = gapMs(),
            jitterMs = jitterMs(),
            jitterOn = Prefs.jitterEnabled(this),
            scriptCount = Prefs.readScripts(this).size
        )
    }

    private fun showNotification(round: Int) {
        val name = Prefs.resolveSelected(this)?.name ?: "-"
        val total = Prefs.loopCount(this)
        val progress = if (total > 0) "$round/$total" else "$round"
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle(name)
            .setContentText(getString(R.string.notify_running, name, progress))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_app, getString(R.string.panel_pause), controlIntent(ACT_PAUSE))
            .addAction(R.drawable.ic_app, getString(R.string.panel_stop), controlIntent(ACT_STOP))
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFY_ID, builder.build()) }
    }

    private fun controlIntent(action: String): PendingIntent {
        val i = Intent(this, ControlReceiver::class.java).apply {
            this.action = ACTION_CONTROL
            putExtra(EXTRA_ACTION, action)
        }
        return PendingIntent.getBroadcast(
            this, action.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, getString(R.string.notify_channel_name), NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    companion object {
        private const val TAG = "AutoHandService"
        private const val CHANNEL = "run_state"
        private const val NOTIFY_ID = 1001
        private const val MIN_JITTER = 300L
        private const val MAX_JITTER = 5000L
        const val ACTION_CONTROL = "com.quqi.autohand.action.CONTROL"
        const val EXTRA_ACTION = "action"
        const val ACT_PAUSE = "pause"
        const val ACT_STOP = "stop"

        var instance: AutoHandService? = null
            private set

        fun isServiceEnabled(context: Context): Boolean {
            val expected = context.packageName + "/" + AutoHandService::class.java.name
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
