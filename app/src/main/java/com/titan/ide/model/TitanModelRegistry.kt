package com.titan.ide.model

/**
 * TitanModelRegistry - Pure Google Ecosystem Catalog & Carbon Smart Routing
 * Developer: Developer Tahir (@Tahirsiddik)
 */
enum class TitanEngine(
    val id: String, 
    val displayName: String, 
    val badge: String,
    val description: String
) {
    // 🧠 Core Coding & Engineering
    GEMINI_4_HYDROGEN("gemini-4-hydrogen", "Gemini 4 Hydrogen", "HYDROGEN CORE", "Ultra-Fast Reasoning & Real-Time Live Voice Core"),
    GEMINI_4_CARBON("gemini-4-carbon", "Gemini 4 Carbon", "ENGINEERING", "Autonomous Software Engineering & Deep Coding Flagship"),
    GEMINI_2_FLASH_LIVE("gemini-2.0-flash-live", "Gemini Flash Live", "REALTIME", "Low-Latency Realtime Voice & Chat"),
    
    // 🎨 Native Google Media
    GOOGLE_IMAGEN_3("imagen-3", "Google Imagen 3", "ULTRA HD", "Photorealistic AI Image Studio"),
    GOOGLE_VEO_2("veo-2", "Google Veo 2", "CINEMA", "Cinematic AI Motion Video Generator")
}

enum class TaskType {
    REALTIME_VOICE,
    RAPID_CHAT,
    APP_BUILDING,
    ARCHITECTURE_DESIGN,
    CODE_DEBUGGING,
    DEEP_ENGINEERING,
    IMAGE_GENERATION,
    VIDEO_GENERATION
}

object TitanModelRegistry {

    /**
     * Smart Carbon Routing Logic for pure Google ecosystem:
     * - Real-time voice and rapid chat: Default to `GEMINI_4_HYDROGEN`
     * - Image generation: Default to `GOOGLE_IMAGEN_3`
     * - Video generation: Default to `GOOGLE_VEO_2`
     * - App Building, Architecture: Route to `GEMINI_4_CARBON`
     */
    fun routeEngineForTask(taskType: TaskType): TitanEngine {
        return when (taskType) {
            TaskType.REALTIME_VOICE, TaskType.RAPID_CHAT -> TitanEngine.GEMINI_4_HYDROGEN
            TaskType.IMAGE_GENERATION -> TitanEngine.GOOGLE_IMAGEN_3
            TaskType.VIDEO_GENERATION -> TitanEngine.GOOGLE_VEO_2
            TaskType.APP_BUILDING, TaskType.ARCHITECTURE_DESIGN, TaskType.CODE_DEBUGGING, TaskType.DEEP_ENGINEERING -> TitanEngine.GEMINI_4_CARBON
        }
    }

    /**
     * Inspects user prompt to dynamically select the best engine.
     */
    fun routeEngineForPrompt(prompt: String, isVoiceActive: Boolean = false): TitanEngine {
        if (isVoiceActive) return TitanEngine.GEMINI_4_HYDROGEN

        val lower = prompt.lowercase()
        return when {
            lower.contains("image") || lower.contains("chobi") || lower.contains("draw") || lower.contains("picture") || lower.contains("photo") || lower.contains("imagen") -> TitanEngine.GOOGLE_IMAGEN_3
            lower.contains("video") || lower.contains("movie") || lower.contains("reels") || lower.contains("shorts") || lower.contains("veo") -> TitanEngine.GOOGLE_VEO_2
            else -> TitanEngine.GEMINI_4_CARBON
        }
    }

    fun getAllEngines(): List<TitanEngine> {
        return TitanEngine.values().toList()
    }
}
