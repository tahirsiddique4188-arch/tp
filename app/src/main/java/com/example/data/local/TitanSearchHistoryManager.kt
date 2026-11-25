package com.example.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

object TitanSearchHistoryManager {
    private const val PREFS_NAME = "titan_search_history_prefs"
    private const val KEY_HISTORY = "recent_queries"
    private const val MAX_HISTORY = 20

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        _history.value = deserializeHistory(json)
    }

    fun addQuery(query: String, context: Context) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        val current = _history.value.toMutableList()
        current.remove(trimmed) // Remove duplicates
        current.add(0, trimmed) // Add to top

        val limited = if (current.size > MAX_HISTORY) current.take(MAX_HISTORY) else current
        _history.value = limited

        persist(limited, context)
    }

    fun deleteQuery(query: String, context: Context) {
        val current = _history.value.filter { it != query }
        _history.value = current
        persist(current, context)
    }

    fun clearHistory(context: Context) {
        _history.value = emptyList()
        persist(emptyList(), context)
    }

    private fun persist(list: List<String>, context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_HISTORY, serializeHistory(list)).apply()
    }

    private fun serializeHistory(list: List<String>): String {
        val array = JSONArray()
        list.forEach { array.put(it) }
        return array.toString()
    }

    private fun deserializeHistory(json: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
