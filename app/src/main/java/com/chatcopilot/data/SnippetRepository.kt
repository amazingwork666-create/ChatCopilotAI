package com.chatcopilot.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Snippet(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

class SnippetRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("chatcopilot_snippets", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<MutableList<Snippet>>() {}.type

    fun getAll(): List<Snippet> {
        val json = prefs.getString("snippets", null) ?: return emptyList()
        return runCatching {
            gson.fromJson<MutableList<Snippet>>(json, listType)
        }.getOrDefault(mutableListOf())
    }

    fun save(text: String): Snippet {
        val snippet = Snippet(text = text.trim())
        val list = getAll().toMutableList()
        list.add(0, snippet)
        val trimmed = list.take(100) // keep max 100
        prefs.edit().putString("snippets", gson.toJson(trimmed)).apply()
        return snippet
    }

    fun delete(id: Long) {
        val updated = getAll().filter { it.id != id }
        prefs.edit().putString("snippets", gson.toJson(updated)).apply()
    }

    fun clear() {
        prefs.edit().remove("snippets").apply()
    }
}
