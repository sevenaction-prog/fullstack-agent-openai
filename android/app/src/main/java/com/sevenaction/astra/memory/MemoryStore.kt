package com.sevenaction.astra.memory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

data class MemoryItem(
    val id: String,
    val text: String,
    val createdAt: String
)

class MemoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("astra_memory", Context.MODE_PRIVATE)

    fun list(): List<MemoryItem> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(MemoryItem(o.getString("id"), o.getString("text"), o.getString("createdAt")))
            }
        }.reversed()
    }

    fun add(text: String): MemoryItem {
        val item = MemoryItem(UUID.randomUUID().toString(), text.trim(), Instant.now().toString())
        val current = JSONArray(prefs.getString("items", "[]") ?: "[]")
        current.put(JSONObject().apply {
            put("id", item.id)
            put("text", item.text)
            put("createdAt", item.createdAt)
        })
        prefs.edit().putString("items", current.toString()).apply()
        return item
    }

    fun delete(id: String) {
        val current = JSONArray(prefs.getString("items", "[]") ?: "[]")
        val next = JSONArray()
        for (i in 0 until current.length()) {
            val o = current.getJSONObject(i)
            if (o.getString("id") != id) next.put(o)
        }
        prefs.edit().putString("items", next.toString()).apply()
    }

    fun context(maxItems: Int = 12): String =
        list().take(maxItems).joinToString("\n") { item -> "- " + item.text }
}
