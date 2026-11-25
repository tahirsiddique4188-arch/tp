package com.example.data.model

enum class ModelProvider(val displayName: String, val iconColor: Long) {
    GEMINI("Google Gemini", 0xFF2563EB),
    FLOW("Google Flow (Veo)", 0xFF8B5CF6),
    IMAGEN("Imagen Studio", 0xFF06B6D4),
    VOICE("Gemini Live Voice", 0xFF10B981)
}

data class ModelInfo(
    val id: String,
    val name: String,
    val category: ModelCategory,
    val description: String,
    val isThinkingModel: Boolean = false,
    val badge: String? = null
) {
    val provider: ModelProvider get() = when (category) {
        ModelCategory.FLOW -> ModelProvider.FLOW
        ModelCategory.IMAGE_STUDIO -> ModelProvider.IMAGEN
        ModelCategory.VOICE_AUDIO -> ModelProvider.VOICE
        else -> ModelProvider.GEMINI
    }
}

enum class ModelCategory(val displayName: String, val badgeColor: Long) {
    CHAT_CODING("Gemini Coding & Research", 0xFF2563EB),
    FLOW("Google Flow & Video", 0xFF8B5CF6),
    IMAGE_STUDIO("Imagen & Nano Banana", 0xFF06B6D4),
    VOICE_AUDIO("Live Voice & TTS", 0xFF10B981),
    THINKING("Thinking Models", 0xFF7C3AED),
    DEEP_RESEARCH("Deep Research", 0xFF0284C7)
}

object GeminiModelsList {
    val DEFAULT_MODEL: ModelInfo get() = CHAT_CODING_MODELS.first()

    // =========================================================================
    // 1. Google Flow & Video Generation Models (10 models) — Dedicated to Video Studio
    // =========================================================================
    val VIDEO_MODELS = listOf(
        ModelInfo(
            id = "veo-3.1",
            name = "Google Flow Veo 3.1",
            category = ModelCategory.FLOW,
            description = "",
            badge = "Native Audio"
        ),
        ModelInfo(
            id = "veo-3.1-fast",
            name = "Google Flow Veo 3.1 Fast",
            category = ModelCategory.FLOW,
            description = "",
            badge = "Turbo Video"
        ),
        ModelInfo(
            id = "veo-3.1-lite",
            name = "Google Flow Veo 3.1 Lite",
            category = ModelCategory.FLOW,
            description = "",
            badge = "Lite Shorts"
        ),
        ModelInfo(
            id = "veo-3.1-quality",
            name = "Google Flow Veo 3.1 Quality",
            category = ModelCategory.FLOW,
            description = "",
            badge = "Ultra Quality"
        ),
        ModelInfo(
            id = "veo-3",
            name = "Google Flow Veo 3 Cinema",
            category = ModelCategory.FLOW,
            description = "",
            badge = "Cinema 21:9"
        ),
        ModelInfo(
            id = "veo-2",
            name = "Google Flow / Veo 2",
            category = ModelCategory.FLOW,
            description = "",
            badge = "Motion Studio"
        ),
        ModelInfo(
            id = "flow-cinema",
            name = "Google Flow Multi-Frame Cinema",
            category = ModelCategory.FLOW,
            description = "",
            badge = "Multi-Frame"
        ),
        ModelInfo(
            id = "gemini-omni-1.2-flash",
            name = "Gemini Omni Flash 1.2 (Video)",
            category = ModelCategory.FLOW,
            description = "",
            badge = "Audio-Reactive"
        ),
        ModelInfo(
            id = "gemini-omni-1.1-flash",
            name = "Gemini Omni Flash 1.1 (Video)",
            category = ModelCategory.FLOW,
            description = "",
            badge = "Style Transfer"
        ),
        ModelInfo(
            id = "titan-native-video",
            name = "Titan Continuous Movie Studio",
            category = ModelCategory.FLOW,
            description = "",
            badge = "Movie Studio"
        )
    )

    // =========================================================================
    // 2. Imagen & Image Generation Models (7 models) — Dedicated to Image Studio
    // =========================================================================
    val IMAGE_MODELS = listOf(
        ModelInfo(
            id = "imagen-3",
            name = "Google Imagen 3",
            category = ModelCategory.IMAGE_STUDIO,
            description = "",
            badge = "Ultra HD"
        ),
        ModelInfo(
            id = "imagen-3-fast",
            name = "Google Imagen 3 Fast",
            category = ModelCategory.IMAGE_STUDIO,
            description = "",
            badge = "Instant HD"
        ),
        ModelInfo(
            id = "gemini-3.1-flash-image",
            name = "Nano Banana 2",
            category = ModelCategory.IMAGE_STUDIO,
            description = "",
            badge = "Nano Banana 2"
        ),
        ModelInfo(
            id = "gemini-3.1-flash-lite-image",
            name = "Nano Banana 2 Lite",
            category = ModelCategory.IMAGE_STUDIO,
            description = "",
            badge = "Fast Image"
        ),
        ModelInfo(
            id = "gemini-3-pro-image",
            name = "Nano Banana Pro",
            category = ModelCategory.IMAGE_STUDIO,
            description = "",
            badge = "Pro Studio"
        ),
        ModelInfo(
            id = "gemini-2.5-flash-image",
            name = "Nano Banana",
            category = ModelCategory.IMAGE_STUDIO,
            description = "",
            badge = "Image Gen"
        ),
        ModelInfo(
            id = "titan-native-image",
            name = "Titan Imagen 3 Pipeline",
            category = ModelCategory.IMAGE_STUDIO,
            description = "",
            badge = "Titan Pipeline"
        )
    )

    // =========================================================================
    // 3. Gemini Chat, Coding & Deep Research Models (20 models) — Dedicated to Chat & Code
    // =========================================================================
    val CHAT_CODING_MODELS = listOf(
        ModelInfo(
            id = "gemini-4-hydrogen",
            name = "Gemini 4 Hydrogen",
            category = ModelCategory.CHAT_CODING,
            description = "",
            isThinkingModel = true,
            badge = "Hydrogen Core"
        ),
        ModelInfo(
            id = "gemini-4-carbon",
            name = "Gemini 4 Carbon",
            category = ModelCategory.CHAT_CODING,
            description = "",
            isThinkingModel = true,
            badge = "Carbon Core"
        ),
        ModelInfo(
            id = "gemini-4-argon",
            name = "Gemini 4 Argon",
            category = ModelCategory.DEEP_RESEARCH,
            description = "",
            isThinkingModel = true,
            badge = "Argon Core"
        ),
        ModelInfo(
            id = "gemini-3.8-flash",
            name = "Gemini 3.8 Flash",
            category = ModelCategory.CHAT_CODING,
            description = "",
            badge = "Turbo Speed"
        ),
        ModelInfo(
            id = "gemini-3.8-live-extended-thinking",
            name = "Gemini 3.8 Extended Thinking",
            category = ModelCategory.THINKING,
            description = "",
            isThinkingModel = true,
            badge = "Extended COT"
        ),
        ModelInfo(
            id = "gemini-3.6-flash",
            name = "Gemini 3.6 Flash",
            category = ModelCategory.CHAT_CODING,
            description = "",
            badge = "High Throughput"
        ),
        ModelInfo(
            id = "gemini-3.5-flash-lite",
            name = "Gemini 3.5 Flash-Lite",
            category = ModelCategory.CHAT_CODING,
            description = "",
            badge = "Flash Lite"
        ),
        ModelInfo(
            id = "gemini-3.1-pro-preview",
            name = "Gemini 3.1 Pro Preview",
            category = ModelCategory.CHAT_CODING,
            description = "",
            isThinkingModel = true,
            badge = "Pro Preview"
        ),
        ModelInfo(
            id = "gemini-3.1-flash-lite",
            name = "Gemini 3.1 Flash-Lite",
            category = ModelCategory.CHAT_CODING,
            description = "",
            badge = "Mobile Lite"
        ),
        ModelInfo(
            id = "gemini-3-flash-preview",
            name = "Gemini 3 Flash Preview",
            category = ModelCategory.CHAT_CODING,
            description = "",
            badge = "Flash Preview"
        ),
        ModelInfo(
            id = "gemini-3-flash",
            name = "Gemini 3 Flash",
            category = ModelCategory.CHAT_CODING,
            description = "",
            badge = "Flash V3"
        ),
        ModelInfo(
            id = "gemini-2.5-pro",
            name = "Gemini 2.5 Pro",
            category = ModelCategory.CHAT_CODING,
            description = "",
            isThinkingModel = true,
            badge = "Pro V2.5"
        ),
        ModelInfo(
            id = "gemini-2.5-flash",
            name = "Gemini 2.5 Flash",
            category = ModelCategory.CHAT_CODING,
            description = "",
            badge = "Flash V2.5"
        ),
        ModelInfo(
            id = "gemini-2.5-flash-lite",
            name = "Gemini 2.5 Flash-Lite",
            category = ModelCategory.CHAT_CODING,
            description = "",
            badge = "Lite V2.5"
        ),
        ModelInfo(
            id = "deep-research-preview-04-2026",
            name = "Deep Research Preview",
            category = ModelCategory.DEEP_RESEARCH,
            description = "",
            isThinkingModel = true,
            badge = "Deep Research"
        ),
        ModelInfo(
            id = "deep-research-max-preview-04-2026",
            name = "Deep Research Max Preview",
            category = ModelCategory.DEEP_RESEARCH,
            description = "",
            isThinkingModel = true,
            badge = "Research Max"
        )
    )

    // =========================================================================
    // 4. Gemini Live Voice, TTS, Transcribe & Podcast Models (15 models) — Dedicated to Voice
    // =========================================================================
    val VOICE_AUDIO_MODELS = listOf(
        ModelInfo(
            id = "gemini-4-hydrogen",
            name = "Gemini 4 Hydrogen (Live Voice Core)",
            category = ModelCategory.VOICE_AUDIO,
            description = "",
            isThinkingModel = true,
            badge = "Hydrogen Live"
        ),
        ModelInfo(
            id = "gemini-3.8-live",
            name = "Gemini 3.8 Live",
            category = ModelCategory.VOICE_AUDIO,
            description = "",
            badge = "Voice Agent"
        ),
        ModelInfo(
            id = "gemini-3.8-flash-tts",
            name = "Gemini 3.8 Flash TTS",
            category = ModelCategory.VOICE_AUDIO,
            description = "",
            badge = "Studio TTS"
        ),
        ModelInfo(
            id = "gemini-3.8-flash-lite-tts",
            name = "Gemini 3.8 Flash-Lite TTS",
            category = ModelCategory.VOICE_AUDIO,
            description = "",
            badge = "101 Languages"
        ),
        ModelInfo(
            id = "gemini-3.5-live-translate",
            name = "Gemini 3.5 Live Translate",
            category = ModelCategory.VOICE_AUDIO,
            description = "",
            badge = "Live Translate"
        ),
        ModelInfo(
            id = "gemini-3.5-transcribe",
            name = "Gemini 3.5 Transcribe",
            category = ModelCategory.VOICE_AUDIO,
            description = "",
            badge = "Transcribe"
        ),
        ModelInfo(
            id = "gemini-3.5-transcribe-live",
            name = "Gemini 3.5 Transcribe Live",
            category = ModelCategory.VOICE_AUDIO,
            description = "",
            badge = "Live Stream STT"
        ),
        ModelInfo(
            id = "gemini-3.1-flash-live-preview",
            name = "Gemini 3.1 Flash Live Preview",
            category = ModelCategory.VOICE_AUDIO,
            description = "",
            badge = "Live Preview"
        ),
        ModelInfo(
            id = "gemini-3.1-flash-tts-preview",
            name = "Gemini 3.1 Flash TTS Preview",
            category = ModelCategory.VOICE_AUDIO,
            description = "",
            badge = "TTS Preview"
        ),
        ModelInfo(
            id = "gemini-2.5-flash-native-audio-preview-12-2025",
            name = "Gemini 2.5 Flash Native Audio",
            category = ModelCategory.VOICE_AUDIO,
            description = "",
            badge = "Native Audio"
        ),
        ModelInfo(
            id = "gemini-2.5-flash-preview-tts",
            name = "Gemini 2.5 Flash TTS",
            category = ModelCategory.VOICE_AUDIO,
            description = "",
            badge = "Flash TTS"
        ),
        ModelInfo(
            id = "gemini-live-omni",
            name = "Gemini Live Omni Controller",
            category = ModelCategory.VOICE_AUDIO,
            description = "",
            badge = "Omni Controller"
        )
    )

    // Master Catalog of 100% Pure Google Ecosystem Models
    val ALL_MODELS: List<ModelInfo> = CHAT_CODING_MODELS + VIDEO_MODELS + IMAGE_MODELS + VOICE_AUDIO_MODELS

    val GEMINI_MODELS: List<ModelInfo> get() = CHAT_CODING_MODELS
    val FLOW_MODELS: List<ModelInfo> get() = VIDEO_MODELS
    val IMAGE_STUDIO_MODELS: List<ModelInfo> get() = IMAGE_MODELS
    val VOICE_MODELS: List<ModelInfo> get() = VOICE_AUDIO_MODELS
}
