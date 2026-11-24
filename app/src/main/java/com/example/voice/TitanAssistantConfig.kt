package com.example.voice

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

data class AppLanguageOption(
    val code: String,
    val displayName: String,
    val nativeLabel: String,
    val bcp47Tag: String,
    val promptDirective: String
)

/**
 * TitanAssistantConfig
 * - Manages Global Auto + All Major Indian Languages
 * - Manages Custom Assistant Name / Wake Word (e.g. Titan, Myra, Jarvis, Riya)
 * - Manages First-Launch Onboarding State
 */
object TitanAssistantConfig {

    private const val PREFS_NAME = "titan_assistant_prefs"
    private const val KEY_ASSISTANT_NAME = "assistant_custom_name"
    private const val KEY_ASSISTANT_MODE = "assistant_mode_active"
    private const val KEY_IDLE_TIMEOUT_MINUTES = "assistant_idle_timeout_min"
    private const val KEY_SELECTED_ORB = "assistant_selected_orb"
    private const val KEY_APP_LANGUAGE = "assistant_app_language"
    private const val KEY_ONBOARDING_DONE = "assistant_onboarding_done"

    const val DEFAULT_NAME = "Titan"
    const val DEFAULT_IDLE_TIMEOUT_MINUTES = 3
    const val DEFAULT_ORB_ID = "titan_neural_3d"
    const val LANG_AUTO = "auto"

    val SUPPORTED_LANGUAGES: List<AppLanguageOption> = listOf(
        AppLanguageOption("auto", "Auto", "Any Language", "auto", ""),
        AppLanguageOption("bn", "Bengali", "বাংলা", "bn-IN", "Always reply in natural Bengali (বাংলা)."),
        AppLanguageOption("hi", "Hindi", "हिन्दी", "hi-IN", "Always reply in natural Hindi (हिन्दी)."),
        AppLanguageOption("en", "English", "English", "en-IN", "Always reply in natural English."),
        AppLanguageOption("ta", "Tamil", "தமிழ்", "ta-IN", "Always reply in natural Tamil (தமிழ்)."),
        AppLanguageOption("te", "Telugu", "తెలుగు", "te-IN", "Always reply in natural Telugu (తెలుగు)."),
        AppLanguageOption("mr", "Marathi", "मराठी", "mr-IN", "Always reply in natural Marathi (मराठी)."),
        AppLanguageOption("gu", "Gujarati", "ગુજરાતી", "gu-IN", "Always reply in natural Gujarati (ગુજરાતી)."),
        AppLanguageOption("kn", "Kannada", "ಕನ್ನಡ", "kn-IN", "Always reply in natural Kannada (ಕನ್ನಡ)."),
        AppLanguageOption("ml", "Malayalam", "മലയാളം", "ml-IN", "Always reply in natural Malayalam (മലയാളം)."),
        AppLanguageOption("pa", "Punjabi", "ਪੰਜਾਬੀ", "pa-IN", "Always reply in natural Punjabi (ਪੰਜਾਬੀ)."),
        AppLanguageOption("or", "Odia", "ଓଡ଼ିଆ", "or-IN", "Always reply in natural Odia (ଓଡ଼ିଆ)."),
        AppLanguageOption("ur", "Urdu", "اردو", "ur-IN", "Always reply in natural Urdu (اردو)."),
        AppLanguageOption("as", "Assamese", "অসমীয়া", "as-IN", "Always reply in natural Assamese (অসমীয়া).")
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isOnboardingCompleted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ONBOARDING_DONE, false)
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, completed).apply()
    }

    fun getSelectedLanguageCode(context: Context): String {
        return getPrefs(context).getString(KEY_APP_LANGUAGE, LANG_AUTO)?.ifBlank { LANG_AUTO } ?: LANG_AUTO
    }

    fun setSelectedLanguageCode(context: Context, code: String) {
        val valid = SUPPORTED_LANGUAGES.firstOrNull { it.code == code }?.code ?: LANG_AUTO
        getPrefs(context).edit().putString(KEY_APP_LANGUAGE, valid).apply()
    }

    fun getSelectedLanguage(context: Context): AppLanguageOption {
        val code = getSelectedLanguageCode(context)
        return SUPPORTED_LANGUAGES.firstOrNull { it.code == code } ?: SUPPORTED_LANGUAGES.first()
    }

    fun getEffectiveSttLanguageTag(context: Context): String {
        val selected = getSelectedLanguage(context)
        if (selected.code != LANG_AUTO) return selected.bcp47Tag
        val sysTag = Locale.getDefault().toLanguageTag()
        return if (sysTag.startsWith("bn") || sysTag.startsWith("hi") || sysTag.startsWith("en") ||
            sysTag.startsWith("ta") || sysTag.startsWith("te") || sysTag.startsWith("mr") ||
            sysTag.startsWith("gu") || sysTag.startsWith("kn") || sysTag.startsWith("ml") ||
            sysTag.startsWith("pa") || sysTag.startsWith("or") || sysTag.startsWith("ur") || sysTag.startsWith("as")
        ) sysTag else "bn-IN"
    }

    fun getSelectedOrbId(context: Context): String {
        return getPrefs(context).getString(KEY_SELECTED_ORB, DEFAULT_ORB_ID) ?: DEFAULT_ORB_ID
    }

    fun setSelectedOrbId(context: Context, orbId: String) {
        getPrefs(context).edit().putString(KEY_SELECTED_ORB, orbId).apply()
    }

    fun getAssistantName(context: Context): String {
        return getPrefs(context).getString(KEY_ASSISTANT_NAME, DEFAULT_NAME)?.ifBlank { DEFAULT_NAME } ?: DEFAULT_NAME
    }

    fun setAssistantName(context: Context, name: String) {
        val clean = name.trim().ifBlank { DEFAULT_NAME }
        getPrefs(context).edit().putString(KEY_ASSISTANT_NAME, clean).apply()
        // Sync to titan_prefs so wake word & legacy readers always stay aligned
        context.applicationContext.getSharedPreferences("titan_prefs", Context.MODE_PRIVATE)
            .edit().putString("custom_wake_word", clean).apply()
    }

    fun isAssistantModeActive(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ASSISTANT_MODE, true)
    }

    fun setAssistantModeActive(context: Context, active: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ASSISTANT_MODE, active).apply()
    }

    fun getIdleTimeoutMs(context: Context): Long {
        val minutes = getPrefs(context).getInt(KEY_IDLE_TIMEOUT_MINUTES, DEFAULT_IDLE_TIMEOUT_MINUTES).coerceIn(2, 5)
        return minutes * 60 * 1000L
    }

    fun setIdleTimeoutMinutes(context: Context, minutes: Int) {
        getPrefs(context).edit().putInt(KEY_IDLE_TIMEOUT_MINUTES, minutes.coerceIn(2, 5)).apply()
    }

    /**
     * Builds dynamic wake-word list matching the user's custom assistant name.
     */
    fun getWakeWordList(context: Context): List<String> {
        val raw = getAssistantName(context).trim().lowercase(Locale.ROOT)
        val stripped = raw
            .removePrefix("hey ").removePrefix("hello ").removePrefix("ok ").removePrefix("oi ")
            .trim().ifBlank { raw }

        val list = mutableListOf<String>()
        list.add(raw)
        list.add("hey $stripped")
        list.add("hello $stripped")
        list.add("oi $stripped")
        list.add("ok $stripped")
        list.add("হেই $stripped")
        list.add("হ্যালো $stripped")
        list.add("ওই $stripped")
        list.add("हे $stripped")
        list.add(stripped)

        if (stripped == "titan") {
            list.addAll(listOf("টাইটান", "হেই টাইটান", "ওই টাইটান", "হ্যালো টাইটান", "टाइटান", "टाइटन", "taitan", "tytan"))
        }
        return list.filter { it.length >= 2 }.distinct()
    }

    fun getAwakenGreeting(context: Context): String {
        val lang = getSelectedLanguage(context)
        return when (lang.code) {
            "bn" -> "হ্যাঁ বলো, আমি শুনছি।"
            "hi" -> "हाँ बोलिए, मैं सुन रहा हूँ।"
            else -> "Yes, I'm listening."
        }
    }

    fun getSleepSignOff(context: Context): String {
        val name = getAssistantName(context)
        return "Standing by — just call $name whenever you need me."
    }
}
