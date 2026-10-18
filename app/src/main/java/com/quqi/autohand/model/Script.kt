package com.quqi.autohand.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Script(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val steps: MutableList<GestureStep> = mutableListOf(),
    /** 打开后 steps 当成一个「手势合集」，每轮只随机抽一段执行。 */
    var randomPick: Boolean = false
) {
    fun summary(): String = when {
        steps.isEmpty() -> "还没有手势"
        randomPick && steps.size > 1 -> "${steps.size} 段手势 · 每轮随机抽一段"
        else -> "${steps.size} 段手势 · ${steps.joinToString(" / ") { it.describe() }}"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        if (randomPick) put("r", true)
        put("steps", JSONArray().apply { steps.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Script {
            val steps = mutableListOf<GestureStep>()
            o.optJSONArray("steps")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { GestureStep.fromJson(it)?.let(steps::add) }
                }
            }
            return Script(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name", "未命名"),
                steps = steps,
                randomPick = o.optBoolean("r", false)
            )
        }
    }
}
