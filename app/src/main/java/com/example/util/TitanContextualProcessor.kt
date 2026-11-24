package com.example.util

import java.util.Locale

/**
 * TitanContextualProcessor - Intelligent Contextual Speech & Text Processor
 * 
 * Provides:
 * 1. Automatic contextual punctuation (interrogative '?', sentence enders '।' / '.', commas)
 * 2. Proper capitalization of sentences, proper nouns, and tech terminology
 * 3. Correction & normalization of spoken technical jargon (e.g. "এপিআই" -> "API", "এপেকে" -> "APK")
 * 4. Multilingual support (Bengali, English, Hindi, etc.)
 *
 * Developer: Developer Tahir (@Tahirsiddik)
 */
object TitanContextualProcessor {

    // Common Bengali interrogative markers
    private val BENGALI_QUESTION_WORDS = setOf(
        "কি", "কী", "কেন", "কীভাবে", "কিভাবে", "কেমনে", "কোথায়", "কোথায়",
        "কখন", "কত", "কার", "কাকে", "কেমন", "কে", "কোন", "কোনটি", "নাকি"
    )

    // Common English question words & auxiliary starters
    private val ENGLISH_QUESTION_WORDS = setOf(
        "what", "why", "how", "when", "where", "who", "which", "whose", "whom",
        "can", "could", "is", "are", "do", "does", "did", "will", "would", "should", "may"
    )

    // Technical jargon phonetic mappings (Bengali speech -> Proper Tech Acronym/Name)
    private val JARGON_REPLACEMENTS = listOf(
        Regex("(?i)\\b(এপিআই|এপি আই|api)\\b") to "API",
        Regex("(?i)\\b(এপেকে|এপিকে|apk)\\b") to "APK",
        Regex("(?i)\\b(ইউআই|ui)\\b") to "UI",
        Regex("(?i)\\b(ইউএক্স|ux)\\b") to "UX",
        Regex("(?i)\\b(এইচটিএমএল|html)\\b") to "HTML",
        Regex("(?i)\\b(সিএসএস|css)\\b") to "CSS",
        Regex("(?i)\\b(জাভাস্ক্রিপ্ট|জাভাস্ক্রিপট|javascript|js)\\b") to "JavaScript",
        Regex("(?i)\\b(পাইথন|python)\\b") to "Python",
        Regex("(?i)\\b(কোটলিন|কটোলিন|kotlin)\\b") to "Kotlin",
        Regex("(?i)\\b(টাইটান|titan)\\b") to "Titan",
        Regex("(?i)\\b(জেমিনি|gemini)\\b") to "Gemini",
        Regex("(?i)\\b(গিটহাব|github)\\b") to "GitHub",
        Regex("(?i)\\b(অ্যান্ড্রয়েড|এন্ড্রয়েড|android)\\b") to "Android",
        Regex("(?i)\\b(জেটপ্যাক কম্পোজ|jetpack compose)\\b") to "Jetpack Compose",
        Regex("(?i)\\b(ফ্লুটার|flutter)\\b") to "Flutter",
        Regex("(?i)\\b(রিয়্যাক্ট|react)\\b") to "React",
        Regex("(?i)\\b(নোডজেএস|node js|nodejs)\\b") to "Node.js",
        Regex("(?i)\\b(এসডিকে|sdk)\\b") to "SDK",
        Regex("(?i)\\b(আইডিই|ide)\\b") to "IDE",
        Regex("(?i)\\b(গুগল|google)\\b") to "Google"
    )

    /**
     * Contextually formats raw speech recognition output into polished, grammatically punctuated text.
     * 
     * @param rawText Raw recognized speech string
     * @param isFinal Whether this is the final transcription result or a partial stream
     * @param targetLocale Target language locale (optional, auto-detected if null)
     */
    fun processContextual(rawText: String, isFinal: Boolean = true, targetLocale: Locale? = null): String {
        if (rawText.isBlank()) return ""

        var text = rawText.trim()

        // 1. Normalize technical jargon & acronyms
        for ((pattern, replacement) in JARGON_REPLACEMENTS) {
            text = text.replace(pattern, replacement)
        }

        // 2. Remove accidental double spaces
        text = text.replace(Regex("\\s+"), " ")

        val isBengali = text.any { it in '\u0980'..'\u09FF' }

        // 3. Sentence-level capitalization for Latin/English scripts
        if (!isBengali) {
            text = capitalizeSentences(text)
        }

        // If it's a partial stream chunk, return early with normalized words
        if (!isFinal) {
            return text
        }

        // 4. Contextual Punctuation Analysis
        val alreadyPunctuated = text.endsWith("?") || text.endsWith("!") || 
                                text.endsWith(".") || text.endsWith("।")

        if (!alreadyPunctuated && text.length > 2) {
            val words = text.lowercase(Locale.ROOT).split(" ").filter { it.isNotBlank() }
            val firstWord = words.firstOrNull() ?: ""

            val isQuestion = if (isBengali) {
                // In Bengali, question words can be at beginning, middle or end (e.g. "তুমি কেমন আছো", "এটা কি")
                words.any { BENGALI_QUESTION_WORDS.contains(it) } || text.contains("?")
            } else {
                ENGLISH_QUESTION_WORDS.contains(firstWord) || text.contains("?")
            }

            text = if (isQuestion) {
                "$text?"
            } else if (isBengali) {
                "$text।"
            } else {
                "$text."
            }
        }

        return text
    }

    private fun capitalizeSentences(input: String): String {
        val sentences = input.split(Regex("(?<=[.!?])\\s+"))
        return sentences.joinToString(" ") { sentence ->
            if (sentence.isNotEmpty()) {
                sentence.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            } else {
                sentence
            }
        }
    }
}