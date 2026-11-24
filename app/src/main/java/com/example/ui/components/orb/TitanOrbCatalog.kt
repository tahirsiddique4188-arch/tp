package com.example.ui.components.orb

import androidx.compose.ui.graphics.Color

enum class OrbCategory(
    val label: String,
    val bengaliLabel: String,
    val icon: String
) {
    ALL("3D GPU Orbs", "3D GPU Orbs", "3D")
}

enum class OrbEmotionState {
    IDLE,
    LOOKING_AROUND,
    LISTENING,
    THINKING,
    SPEAKING,
    WORKING,
    SLEEPING
}

data class TitanOrbSkin(
    val id: String,
    val name: String,
    val bengaliName: String,
    val category: OrbCategory,
    val description: String,
    val bengaliDescription: String,
    val emoji: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val accentColor: Color,
    val glowColor: Color,
    val styleType: OrbStyleType
)

enum class OrbStyleType {
    NEURAL_3D_SPHERE,
    ENERGY_GHOST,
    HOLO_JELLYFISH
}

object TitanOrbCatalog {

    val TITAN_3D_ORB = TitanOrbSkin(
        id = "titan_neural_3d",
        name = "3D Neural Orb",
        bengaliName = "3D Neural Orb",
        category = OrbCategory.ALL,
        description = "Real-time GPU 3D Sphere",
        bengaliDescription = "Real-time GPU 3D Sphere",
        emoji = "",
        primaryColor = Color(0xFF00F0FF),
        secondaryColor = Color(0xFF3B82F6),
        accentColor = Color(0xFFFFFFFF),
        glowColor = Color(0x8800F0FF),
        styleType = OrbStyleType.NEURAL_3D_SPHERE
    )

    val CUTE_3D_GHOST = TitanOrbSkin(
        id = "cute_ghost_3d",
        name = "3D Cute Ghost",
        bengaliName = "3D Cute Ghost",
        category = OrbCategory.ALL,
        description = "Real-time GPU 3D Ghost",
        bengaliDescription = "Real-time GPU 3D Ghost",
        emoji = "",
        primaryColor = Color(0xFF8BE9FD),
        secondaryColor = Color(0xFFA855F7),
        accentColor = Color(0xFFFF79C6),
        glowColor = Color(0x888BE9FD),
        styleType = OrbStyleType.ENERGY_GHOST
    )

    val HOLO_3D_JELLYFISH = TitanOrbSkin(
        id = "jellyfish_pastel_3d",
        name = "3D Pastel Jellyfish",
        bengaliName = "3D Pastel Jellyfish",
        category = OrbCategory.ALL,
        description = "Soft Pink, Sky Blue, White & Orange 3D",
        bengaliDescription = "Soft Pink, Sky Blue, White & Orange 3D",
        emoji = "",
        primaryColor = Color(0xFFFF9EC7),
        secondaryColor = Color(0xFF7DD3FC),
        accentColor = Color(0xFFFF9A56),
        glowColor = Color(0x88FF9EC7),
        styleType = OrbStyleType.HOLO_JELLYFISH
    )

    val ORB_SKINS: List<TitanOrbSkin> = listOf(
        TITAN_3D_ORB,
        CUTE_3D_GHOST,
        HOLO_3D_JELLYFISH
    )

    fun getOrbById(id: String): TitanOrbSkin {
        val clean = id.trim().lowercase()
        return when {
            clean.contains("jelly") -> HOLO_3D_JELLYFISH
            clean.contains("ghost") -> CUTE_3D_GHOST
            else -> ORB_SKINS.find { it.id.equals(clean, ignoreCase = true) } ?: TITAN_3D_ORB
        }
    }

    fun getOrbsByCategory(category: OrbCategory): List<TitanOrbSkin> = ORB_SKINS
}
