package com.quqi.autohand.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Script(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val steps: MutableList<GestureStep> = mutableListOf()
) {
    fun summary(): String = if (steps.isEmpty()) "还没有手势" else "${steps.size} 段手势 · ${steps.joinToString(" / ") { it.describe() }}"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
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
                steps = steps
            )
        }
    }
}
