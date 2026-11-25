package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.UserMemoryVaultEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Titan Long-Term Memory Manager.
 * Stores user facts, identity, language preferences, voice speed, and coding habits
 * persistently using both Room Database and SharedPreferences.
 * Automatically prepends memories to system prompts so Titan naturally remembers everything.
 */
class TitanMemoryManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("titan_user_memory_prefs", Context.MODE_PRIVATE)
    private val db = AppDatabase.getDatabase(context)
    private val memoryDao = db.userMemoryVaultDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val KEY_USER_NAME = "pref_user_name"
        private const val KEY_PREFERRED_LANGUAGE = "pref_language"
        private const val KEY_VOICE_SPEED = "pref_voice_speed"
        private const val KEY_CODING_HABITS = "pref_coding_habits"

        @Volatile
        private var INSTANCE: TitanMemoryManager? = null

        fun getInstance(context: Context): TitanMemoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TitanMemoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    val allMemoriesFlow: Flow<List<UserMemoryVaultEntity>> = memoryDao.getAllMemories()

    // 1. User Profile & Preferences
    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "Developer Tahir") ?: "Developer Tahir"
        set(value) = prefs.edit().putString(KEY_USER_NAME, value.trim()).apply()

    var preferredLanguage: String
        get() = prefs.getString(KEY_PREFERRED_LANGUAGE, "English") ?: "English"
        set(value) = prefs.edit().putString(KEY_PREFERRED_LANGUAGE, value.trim()).apply()

    var voiceSpeed: Float
        get() = prefs.getFloat(KEY_VOICE_SPEED, 1.15f)
        set(value) = prefs.edit().putFloat(KEY_VOICE_SPEED, value).apply()

    var codingHabits: String
        get() = prefs.getString(
            KEY_CODING_HABITS,
            "Jetpack Compose, Clean Architecture, Kotlin Coroutines, Modern Single-File REST APIs, Reactive StateFlow"
        ) ?: "Jetpack Compose, Clean Architecture"
        set(value) = prefs.edit().putString(KEY_CODING_HABITS, value.trim()).apply()

    // 2. Room Fact CRUD
    fun saveFact(key: String, value: String) {
        scope.launch {
            memoryDao.saveMemory(
                UserMemoryVaultEntity(
                    key = key.trim(),
                    value = value.trim()
                )
            )
        }
    }

    fun deleteFact(key: String) {
        scope.launch {
            memoryDao.deleteMemory(key.trim())
        }
    }

    fun clearAllFacts() {
        scope.launch {
            memoryDao.clearAllMemories()
        }
    }

    // 3. Automatic Fact Learning
    fun autoLearnFromPrompt(userPrompt: String) {
        val lower = userPrompt.lowercase(Locale.ROOT).trim()

        val namePatterns = listOf(
            Regex("""(?i)(?:mara\s+name|mera\s+name|mara\s+nam|mera\s+nam|mera\s+naam|mara\s+naam|amar\s+nam|amar\s+name|amr\s+nam|my\s+name\s+is|call\s+me)\s*(?:is|hai|hobe|hoy|:)?\s*([a-zA-Z\u0980-\u09FF\u0900-\u097F]+)"""),
            Regex("""(?i)([a-zA-Z\u0980-\u09FF\u0900-\u097F]+)\s+(?:mera\s+name\s+hai|mara\s+name\s+hai|mera\s+naam\s+hai|mara\s+naam\s+hai|amar\s+nam)""")
        )

        for (pattern in namePatterns) {
            val match = pattern.find(userPrompt)
            if (match != null) {
                val candidateName = match.groupValues[1]
                    .replace(Regex("(?i)^(is|hai|ya|me|ma|memory|hobe|hoy)$"), "")
                    .trim()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

                if (candidateName.length in 2..30 &&
                    !candidateName.equals("hai", ignoreCase = true) &&
                    !candidateName.equals("memory", ignoreCase = true)
                ) {
                    userName = candidateName
                    saveFact("User Name", candidateName)
                    break
                }
            }
        }

        when {
            lower.contains("my preferred stack is") || lower.contains("my tech stack is") || lower.contains("i code in") -> {
                val extracted = userPrompt
                    .replace("my preferred stack is", "", ignoreCase = true)
                    .replace("my tech stack is", "", ignoreCase = true)
                    .replace("i code in", "", ignoreCase = true)
                    .replace("remember that", "", ignoreCase = true)
                    .trim()
                if (extracted.isNotBlank()) {
                    codingHabits = extracted
                    saveFact("Preferred Coding Stack", extracted)
                }
            }
            lower.contains("save in memory") || lower.contains("save to memory") || lower.contains("save karlo") ||
                    lower.contains("ya memory ma save") || lower.contains("memory me save") ||
                    lower.startsWith("remember that") || lower.startsWith("মনে রেখো") || lower.startsWith("yaad rakh") -> {
                val fact = userPrompt
                    .replace(Regex("(?i)(ya\\s+memory\\s+ma\\s+save\\s+karlo|memory\\s+me\\s+save\\s+karlo|save\\s+in\\s+memory|save\\s+to\\s+memory|save\\s+karlo|remember\\s+that|yaad\\s+rakhna|yaad\\s+rakh|mone\\s+rekho)"), "")
                    .trim()
                if (fact.isNotBlank()) {
                    val key = "Fact_${System.currentTimeMillis() % 10000}"
                    saveFact(key, fact)
                }
            }
        }
    }

    // 4. System Prompt Formatting (Prepend to all AI prompts)
    fun buildSystemPromptMemoryPrefix(entities: List<UserMemoryVaultEntity>? = null): String {
        val sb = StringBuilder()
        sb.append("\n\n[TITAN PERSISTENT LONG-TERM MEMORY BANK]\n")
        sb.append("- User Name: $userName\n")
        sb.append("- Preferred Language: $preferredLanguage\n")
        sb.append("- Spoken Voice Speed: ${String.format(Locale.US, "%.2f", voiceSpeed)}x\n")
        sb.append("- Coding Habits & Stack: $codingHabits\n")

        val memoryList = entities ?: emptyList()

        if (memoryList.isNotEmpty()) {
            sb.append("- Stored Memories & Custom Facts:\n")
            memoryList.forEach { item ->
                sb.append("  * ${item.key}: ${item.value}\n")
            }
        }
        sb.append("(Titan naturally remembers and respects these personal preferences and user habits across all interactions.)\n")

        return sb.toString()
    }
}
