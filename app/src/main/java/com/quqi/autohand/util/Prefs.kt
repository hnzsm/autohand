package com.quqi.autohand.util

import android.content.Context
import com.quqi.autohand.model.Script
import com.quqi.autohand.model.ScriptStore
import org.json.JSONArray

/** 全局配置与脚本仓库，都存在 SharedPreferences 里。 */
object Prefs {
    private const val FILE = "auto_hand"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun readScripts(c: Context): MutableList<Script> =
        ScriptStore.decode(sp(c).getString(KEY_SCRIPTS, "[]"))

    fun writeScripts(c: Context, list: List<Script>) {
        sp(c).edit().putString(KEY_SCRIPTS, ScriptStore.encode(list)).apply()
    }

    fun nextScriptName(c: Context): String = "脚本 ${readScripts(c).size + 1}"

    /** 改某个脚本的「每轮随机抽一段」开关。 */
    fun setScriptRandomPick(c: Context, id: String, v: Boolean) {
        val list = readScripts(c)
        val target = list.firstOrNull { it.id == id } ?: return
        if (target.randomPick == v) return
        target.randomPick = v
        writeScripts(c, list)
    }

    fun selectedId(c: Context): String? = sp(c).getString(KEY_SELECTED, null)

    fun setSelected(c: Context, id: String) = sp(c).edit().putString(KEY_SELECTED, id).apply()

    /** 选中脚本被删除后回落到第一个可用脚本。 */
    fun resolveSelected(c: Context): Script? {
        val list = readScripts(c)
        if (list.isEmpty()) return null
        return list.firstOrNull { it.id == selectedId(c) } ?: list.first().also { setSelected(c, it.id) }
    }

    fun ballEnabled(c: Context) = sp(c).getBoolean(KEY_BALL, false)
    fun setBallEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean(KEY_BALL, v).apply()

    fun keepScreenOn(c: Context) = sp(c).getBoolean(KEY_KEEP_ON, true)
    fun setKeepScreenOn(c: Context, v: Boolean) = sp(c).edit().putBoolean(KEY_KEEP_ON, v).apply()

    fun loopCount(c: Context) = sp(c).getInt(KEY_LOOP, 0)
    fun setLoopCount(c: Context, v: Int) = sp(c).edit().putInt(KEY_LOOP, v).apply()

    fun gapMs(c: Context) = sp(c).getLong(KEY_GAP, DEFAULT_GAP_MS)
    fun setGapMs(c: Context, v: Long) = sp(c).edit().putLong(KEY_GAP, v).apply()

    fun jitterMs(c: Context) = sp(c).getLong(KEY_JITTER, DEFAULT_JITTER_MS)
    fun setJitterMs(c: Context, v: Long) = sp(c).edit().putLong(KEY_JITTER, v).apply()

    fun privacyConsented(c: Context) = sp(c).getBoolean(KEY_CONSENT, false)
    fun setPrivacyConsented(c: Context, v: Boolean) = sp(c).edit().putBoolean(KEY_CONSENT, v).apply()

    fun ballX(c: Context) = sp(c).getInt(KEY_BALL_X, 0)
    fun ballY(c: Context) = sp(c).getInt(KEY_BALL_Y, 240)
    fun setBallPosition(c: Context, x: Int, y: Int) =
        sp(c).edit().putInt(KEY_BALL_X, x).putInt(KEY_BALL_Y, y).apply()

    private const val KEY_SCRIPTS = "scripts"
    private const val KEY_SELECTED = "selected_id"
    private const val KEY_BALL = "ball"
    private const val KEY_KEEP_ON = "keep_screen_on"
    private const val KEY_LOOP = "loop_count"
    private const val KEY_GAP = "gap_ms"
    private const val KEY_JITTER = "jitter"
    private const val KEY_CONSENT = "privacy_consented"
    private const val KEY_BALL_X = "ball_x"
    private const val KEY_BALL_Y = "ball_y"

    /** 每轮间隔：默认值、单次步进与上下限（毫秒）。 */
    const val DEFAULT_GAP_MS = 12000L
    const val GAP_STEP_MS = 2000L
    const val GAP_MIN_MS = 0L
    const val GAP_MAX_MS = 60000L

    /** 随机抖动：在间隔之上最多再叠加的随机量（毫秒），0 表示不抖动。 */
    const val DEFAULT_JITTER_MS = 3000L
    const val JITTER_STEP_MS = 200L
    const val JITTER_MIN_MS = 0L
    const val JITTER_MAX_MS = 10000L
}
