package com.example.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

data class TitanApiKey(
    val id: String,
    val key: String,
    val label: String,
    val createdAt: Long = System.currentTimeMillis(),
    var usageCount: Long = 0,
    val isActive: Boolean = true
)

object TitanApiKeyManager {
    private const val PREFS_NAME = "titan_api_keys_prefs"
    private const val KEY_API_KEYS = "api_keys_list"

    private var appContext: Context? = null
    private val secureRandom = SecureRandom()

    private val _apiKeys = MutableStateFlow<List<TitanApiKey>>(emptyList())
    val apiKeys: StateFlow<List<TitanApiKey>> = _apiKeys.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_API_KEYS, "[]") ?: "[]"
        val loaded = deserializeKeys(json)
        if (loaded.isEmpty()) {
            val defaultKey = createKeyObject("Personal Development Key")
            val initialList = listOf(defaultKey)
            _apiKeys.value = initialList
            persist(initialList, context)
        } else {
            _apiKeys.value = loaded
        }
    }

    private fun generateRandomKeyToken(): String {
        val alphabet = "0123456789abcdef"
        val part1 = (1..8).map { alphabet[secureRandom.nextInt(alphabet.length)] }.joinToString("")
        val part2 = (1..8).map { alphabet[secureRandom.nextInt(alphabet.length)] }.joinToString("")
        return "titan-sk-$part1-$part2"
    }

    private fun createKeyObject(label: String): TitanApiKey {
        return TitanApiKey(
            id = "key_" + System.currentTimeMillis() + "_" + (100..999).random(),
            key = generateRandomKeyToken(),
            label = label.ifBlank { "Personal API Key" },
            createdAt = System.currentTimeMillis(),
            usageCount = 0,
            isActive = true
        )
    }

    @Synchronized
    fun generateKey(label: String = "Personal API Key", context: Context? = null): TitanApiKey {
        val ctx = context ?: appContext
        val newKey = createKeyObject(label)
        val updated = _apiKeys.value.toMutableList().apply { add(0, newKey) }
        _apiKeys.value = updated
        ctx?.let { persist(updated, it) }
        return newKey
    }

    @Synchronized
    fun validateKey(rawToken: String): Boolean {
        val cleanToken = rawToken.trim()
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .trim()

        if (cleanToken.isEmpty()) return false

        val currentList = _apiKeys.value
        val matchedKey = currentList.find { it.key == cleanToken && it.isActive }

        return if (matchedKey != null) {
            val updated = currentList.map {
                if (it.id == matchedKey.id) it.copy(usageCount = it.usageCount + 1) else it
            }
            _apiKeys.value = updated
            appContext?.let { persist(updated, it) }
            true
        } else {
            false
        }
    }

    @Synchronized
    fun revokeKey(keyId: String, context: Context? = null) {
        val ctx = context ?: appContext
        val updated = _apiKeys.value.map {
            if (it.id == keyId) it.copy(isActive = false) else it
        }
        _apiKeys.value = updated
        ctx?.let { persist(updated, it) }
    }

    @Synchronized
    fun reactivateKey(keyId: String, context: Context? = null) {
        val ctx = context ?: appContext
        val updated = _apiKeys.value.map {
            if (it.id == keyId) it.copy(isActive = true) else it
        }
        _apiKeys.value = updated
        ctx?.let { persist(updated, it) }
    }

    @Synchronized
    fun deleteKey(keyId: String, context: Context? = null) {
        val ctx = context ?: appContext
        val updated = _apiKeys.value.filter { it.id != keyId }
        _apiKeys.value = updated
        ctx?.let { persist(updated, it) }
    }

    fun getPrimaryActiveKey(): String {
        return _apiKeys.value.firstOrNull { it.isActive }?.key ?: "titan-sk-demo-key"
    }

    private fun persist(list: List<TitanApiKey>, context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_API_KEYS, serializeKeys(list)).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun serializeKeys(list: List<TitanApiKey>): String {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("key", item.key)
            obj.put("label", item.label)
            obj.put("createdAt", item.createdAt)
            obj.put("usageCount", item.usageCount)
            obj.put("isActive", item.isActive)
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeKeys(json: String): List<TitanApiKey> {
        val list = mutableListOf<TitanApiKey>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TitanApiKey(
                        id = obj.optString("id", "id_$i"),
                        key = obj.optString("key", ""),
                        label = obj.optString("label", "API Key"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        usageCount = obj.optLong("usageCount", 0),
                        isActive = obj.optBoolean("isActive", true)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
