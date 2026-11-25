package com.titan.ide.brain

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * TitanBrainMemoryEngine
 * Long-Term Semantic Memory & Adaptive Context Engine for Titan IDE.
 * Features:
 *  - Automatically remembers user instructions, names, favorite voices, preferred languages, and project details.
 *  - Injects contextual memory into Chat, Live Voice, and Video Generation prompts.
 *  - Supports Romanized Hindi/Urdu, Bengali, and English triggers (e.g. "Mara name tahir Hai ya memory ma save karlo").
 *  - Clean UTF-8 without BOM.
 */
object TitanBrainMemoryEngine {

    private const val TAG = "TitanBrainMemory"
    private const val PREFS_NAME = "titan_long_term_brain"
    private const val KEY_FACTS = "brain_facts_json"
    private const val KEY_CACHED_NAME = "brain_cached_user_name"
    private const val MAX_FACTS = 100
    private const val KEY_CROSS_CHAT_LOG = "brain_cross_chat_recent_json"
    private const val MAX_CROSS_CHAT_ITEMS = 14

    data class MemoryFact(
        val fact: String,
        val category: String,
        val timestamp: Long
    )

    fun saveFact(context: Context, fact: String, category: String = "general") {
        val clean = fact.trim()
        if (clean.length < 2) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY_FACTS, "[]") ?: "[]") } catch (e: Exception) { JSONArray() }

        // Deduplicate
        for (i in 0 until arr.length()) {
            val existing = arr.optJSONObject(i)?.optString("fact", "") ?: ""
            if (existing.equals(clean, ignoreCase = true)) return
        }

        val updated = JSONArray()
        updated.put(JSONObject().apply {
            put("fact", clean)
            put("category", category)
            put("ts", System.currentTimeMillis())
        })
        for (i in 0 until arr.length().coerceAtMost(MAX_FACTS - 1)) {
            updated.put(arr.get(i))
        }
        prefs.edit().putString(KEY_FACTS, updated.toString()).apply()
        Log.d(TAG, "Brain remembered: [$category] $clean")

        // Sync with TitanMemoryManager
        try {
            com.example.data.local.TitanMemoryManager.getInstance(context).saveFact(category, clean)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mirror fact to TitanMemoryManager: ${e.message}")
        }
    }

    fun getAllFacts(context: Context): List<MemoryFact> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY_FACTS, "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        val list = mutableListOf<MemoryFact>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(
                MemoryFact(
                    fact = obj.optString("fact", ""),
                    category = obj.optString("category", "general"),
                    timestamp = obj.optLong("ts", 0L)
                )
            )
        }
        return list
    }

    fun getUserName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_CACHED_NAME, null)
        if (!cached.isNullOrBlank()) return cached

        val facts = getAllFacts(context)
        for (f in facts) {
            if (f.category == "identity" && f.fact.contains("name is", ignoreCase = true)) {
                val name = f.fact.substringAfter("name is").trim()
                if (name.isNotBlank()) {
                    prefs.edit().putString(KEY_CACHED_NAME, name).apply()
                    return name
                }
            }
        }
        val fallback = try {
            com.example.data.local.TitanMemoryManager.getInstance(context).userName
        } catch (e: Exception) {
            "Tahir"
        }
        return if (!fallback.isNullOrBlank()) fallback else "Tahir"
    }

    fun setUserName(context: Context, name: String) {
        val clean = name.trim()
        if (clean.isNotBlank()) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_CACHED_NAME, clean).apply()
            saveFact(context, "User's name is $clean", "identity")
            try {
                com.example.data.local.TitanMemoryManager.getInstance(context).userName = clean
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync user name to TitanMemoryManager: ${e.message}")
            }
        }
    }

    fun isBengali(text: String): Boolean {
        return text.any { it in '\u0980'..'\u09FF' }
    }

    fun isHindiOrHinglish(text: String): Boolean {
        if (text.any { it in '\u0900'..'\u097F' }) return true
        val lower = text.lowercase()
        val hinglishWords = listOf(
            "mara", "mera", "meri", "mere", "hai", "hain", "karo", "karlo", "karna", "kardo",
            "yaad", "naam", "name", "bhai", "kya", "kaise", "batao", "mujhe", "apna", "aapka",
            "rakhna", "rakho", "bolo", "bol", "sun", "suno", "sunao", "dikhaye", "dikhao"
        )
        return hinglishWords.any { Regex("\\b$it\\b").containsMatchIn(lower) }
    }

    fun buildMemoryConfirmation(userMessage: String, extractedName: String?): String {
        return if (!extractedName.isNullOrBlank()) {
            "Saved your name ($extractedName) to memory."
        } else {
            "Saved to memory."
        }
    }

    fun recordCrossChatTurn(context: Context, userMessage: String) {
        val clean = userMessage.replace(Regex("\\s+"), " ").trim().take(140)
        if (clean.length < 4) return
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val arr = try { JSONArray(prefs.getString(KEY_CROSS_CHAT_LOG, "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
            if (arr.length() > 0 && arr.optString(0).equals(clean, ignoreCase = true)) return
            val updated = JSONArray()
            updated.put(clean)
            for (i in 0 until arr.length().coerceAtMost(MAX_CROSS_CHAT_ITEMS - 1)) {
                val item = arr.optString(i, "")
                if (item.isNotBlank() && !item.equals(clean, ignoreCase = true)) {
                    updated.put(item)
                }
            }
            prefs.edit().putString(KEY_CROSS_CHAT_LOG, updated.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getMemoryContextBlock(context: Context, maxFacts: Int = 20): String {
        val name = getUserName(context)
        val facts = getAllFacts(context).take(maxFacts)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val recentArr = try { JSONArray(prefs.getString(KEY_CROSS_CHAT_LOG, "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
        val recentList = mutableListOf<String>()
        for (i in 0 until recentArr.length().coerceAtMost(10)) {
            val s = recentArr.optString(i, "").trim()
            if (s.isNotBlank()) recentList.add(s)
        }
        return buildString {
            append("[PERSISTENT USER MEMORY ACROSS ALL CHATS]\n")
            if (!name.isNullOrBlank()) append("- User Name: $name\n")
            if (facts.isNotEmpty()) {
                append("- Saved Long-Term Facts:\n")
                facts.forEach { append("  * ${it.fact}\n") }
            }
            if (recentList.isNotEmpty()) {
                append("- Recent Context From Previous Chats (Remember seamlessly even in a new chat):\n")
                recentList.forEach { append("  * $it\n") }
            }
            append("Always use this memory naturally so the user never has to repeat themselves across new chats.\n")
        }
    }

    /**
     * Automatically extracts user preferences, rules, name, project info from every message!
     * Handles Romanized Hindi/Urdu ("Mara name tahir Hai ya memory ma save karlo"), Bengali, and English.
     */
    fun autoLearnFromUserMessage(context: Context, userMessage: String): Pair<Boolean, String?> {
        val clean = userMessage.trim()
        if (clean.length !in 3..400) return false to null
        val lower = clean.lowercase()

        // 1. Multilingual Name Triggers & Extraction
        val namePatterns = listOf(
            Regex("""(?i)(?:mara\s+name|mera\s+name|mara\s+nam|mera\s+nam|mera\s+naam|mara\s+naam|amar\s+nam|amar\s+name|amr\s+nam|my\s+name\s+is|call\s+me)\s*(?:is|hai|hobe|hoy|:)?\s*([a-zA-Z\u0980-\u09FF\u0900-\u097F]+)"""),
            Regex("""(?i)([a-zA-Z\u0980-\u09FF\u0900-\u097F]+)\s+(?:mera\s+name\s+hai|mara\s+name\s+hai|mera\s+naam\s+hai|mara\s+naam\s+hai|amar\s+nam)""")
        )

        for (pattern in namePatterns) {
            val match = pattern.find(clean)
            if (match != null) {
                val candidateName = match.groupValues[1]
                    .replace(Regex("(?i)^(is|hai|ya|me|ma|memory|hobe|hoy)$"), "")
                    .trim()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

                if (candidateName.length in 2..30 &&
                    !candidateName.equals("hai", ignoreCase = true) &&
                    !candidateName.equals("memory", ignoreCase = true) &&
                    !candidateName.equals("kardo", ignoreCase = true)
                ) {
                    setUserName(context, candidateName)
                    try {
                        com.example.data.local.TitanMemoryManager.getInstance(context).userName = candidateName
                        com.example.data.local.TitanMemoryManager.getInstance(context).saveFact("User Name", candidateName)
                    } catch (e: Exception) {}
                    Log.d(TAG, "Extracted user name: $candidateName")
                    return true to candidateName
                }
            }
        }

        // 2. Explicit Memory & Instruction Triggers
        val isExplicitMemorySave = lower.contains("memory ma save") ||
                lower.contains("memory me save") ||
                lower.contains("memory te save") ||
                lower.contains("save karlo") ||
                lower.contains("save kar lo") ||
                lower.contains("save in memory") ||
                lower.contains("save to memory") ||
                lower.contains("remember that") ||
                lower.contains("remember this") ||
                lower.contains("don't forget") ||
                lower.contains("mone rakhbe") ||
                lower.contains("mone rekho") ||
                lower.contains("mona rakhba") ||
                lower.contains("yaad rakhna") ||
                lower.contains("yaad rakh") ||
                lower.contains("yaad rakho")

        if (isExplicitMemorySave) {
            val cleanFact = clean
                .replace(Regex("(?i)(ya\\s+memory\\s+ma\\s+save\\s+karlo|memory\\s+me\\s+save\\s+karlo|save\\s+in\\s+memory|save\\s+to\\s+memory|save\\s+karlo|yaad\\s+rakhna|yaad\\s+rakh|mone\\s+rekho|mona\\s+rakhba)"), "")
                .trim()
            if (cleanFact.isNotBlank() && cleanFact.length > 3) {
                saveFact(context, cleanFact, "instruction")
                try {
                    com.example.data.local.TitanMemoryManager.getInstance(context).saveFact("Instruction", cleanFact)
                } catch (e: Exception) {}
                return true to null
            } else {
                saveFact(context, clean, "instruction")
                return true to null
            }
        }

        // 3. User Preferences (Language, Voice, Style, Orb)
        val prefTriggers = listOf(
            "i prefer", "i like", "always use", "never use", "ami chai", "amar pochondo",
            "সবসময়", "কখনোই না", "मुझे पसंद है", "हमेशा", "कभी मत", "amar favorite"
        )
        if (prefTriggers.any { lower.contains(it) }) {
            saveFact(context, clean, "preference")
            return true to null
        }

        // 4. Project & Coding details
        val projectTriggers = listOf(
            "my project is", "i am building", "amar project", "mera project", "मेरा प्रोजेक्ट",
            "we are working on", "titan ide"
        )
        if (projectTriggers.any { lower.contains(it) }) {
            saveFact(context, clean, "project")
            return true to null
        }

        return false to null
    }

    fun clearAllMemory(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
