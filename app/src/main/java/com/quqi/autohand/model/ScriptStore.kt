package com.quqi.autohand.model

import org.json.JSONArray

object ScriptStore {

    fun encode(list: List<Script>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    fun decode(raw: String?): MutableList<Script> {
        if (raw.isNullOrBlank()) return mutableListOf()
        val out = mutableListOf<Script>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { out.add(Script.fromJson(it)) }
            }
        } catch (_: Exception) {
            // 存不进就当作空库，不要让主界面崩溃
        }
        return out
    }
}
