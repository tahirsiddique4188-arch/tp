package com.example.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

data class TitanThemePalette(
    val id: String,
    val name: String,
    val description: String,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val secondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val outline: Color,
    val swatches: List<Color>,
    val isDark: Boolean = false
) {
    val colorScheme: ColorScheme
        get() = if (isDark) {
            darkColorScheme(
                primary = primary,
                onPrimary = background,
                primaryContainer = surfaceVariant,
                onPrimaryContainer = textPrimary,
                secondary = secondary,
                onSecondary = textPrimary,
                background = background,
                onBackground = textPrimary,
                surface = surface,
                onSurface = textPrimary,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = textSecondary,
                outline = outline
            )
        } else {
            lightColorScheme(
                primary = primary,
                onPrimary = Color.White,
                primaryContainer = surfaceVariant,
                onPrimaryContainer = textPrimary,
                secondary = secondary,
                onSecondary = Color.White,
                background = background,
                onBackground = textPrimary,
                surface = surface,
                onSurface = textPrimary,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = textSecondary,
                outline = outline
            )
        }
}

val LocalTitanTheme = staticCompositionLocalOf { TitanThemeManager.TITAN_PREMIUM_WHITE }

object TitanThemeManager {
    private const val PREFS_NAME = "titan_theme_preferences"
    private const val KEY_THEME_ID = "selected_theme_id"
    private const val KEY_USER_NAME = "user_profile_name"

    // 1. MASTER DEFAULT: Titan Premium White (Clean Executive Luxury Light Aesthetic)
    val TITAN_PREMIUM_WHITE = TitanThemePalette(
        id = "titan_premium_white",
        name = "Titan Premium White",
        description = "Executive Pure White Studio, Obsidian Typography #0F172A & Cobalt Blue Accent",
        background = Color(0xFFF8FAFC),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF1F5F9),
        primary = Color(0xFF2563EB), // Cobalt Royal Blue
        secondary = Color(0xFF4F46E5), // Indigo Accent
        textPrimary = Color(0xFF0F172A), // Crisp Obsidian Black
        textSecondary = Color(0xFF64748B), // Slate Grey
        outline = Color(0xFFE2E8F0), // Subtle hairline divider
        swatches = listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC), Color(0xFF2563EB), Color(0xFF0F172A)),
        isDark = false
    )

    // 2. Soft Light Mode
    val SOFT_LIGHT_MODE = TitanThemePalette(
        id = "soft_light_mode",
        name = "Minimalist Light",
        description = "Minimalist Crisp Light #F8F9FC, Pure White Cards, Soft Cyan & Violet",
        background = Color(0xFFF8F9FC),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFEEF2F6),
        primary = Color(0xFF0284C7),
        secondary = Color(0xFF7C3AED),
        textPrimary = Color(0xFF0F172A),
        textSecondary = Color(0xFF64748B),
        outline = Color(0xFFE2E8F0),
        swatches = listOf(Color(0xFFF8F9FC), Color(0xFFFFFFFF), Color(0xFF0284C7), Color(0xFF7C3AED)),
        isDark = false
    )

    // 3. Pastel Cloud Light Mode
    val PASTEL_SUNSET_LIGHT = TitanThemePalette(
        id = "pastel_sunset_light",
        name = "Pastel Cloud",
        description = "Airy Day Palette with Soft Sky Blue, Rose Pink & Cloud White",
        background = Color(0xFFF8FAFC),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF1F5F9),
        primary = Color(0xFF0284C7),
        secondary = Color(0xFFE11D48),
        textPrimary = Color(0xFF0F172A),
        textSecondary = Color(0xFF64748B),
        outline = Color(0xFFE2E8F0),
        swatches = listOf(Color(0xFF38BDF8), Color(0xFFFFFFFF), Color(0xFFF472B6), Color(0xFFFB923C)),
        isDark = false
    )

    // 4. Soft Slate Dark Mode
    val SOFT_SLATE_DARK = TitanThemePalette(
        id = "soft_slate_dark",
        name = "Soft Slate Dark",
        description = "Modern Soft Slate #12151C, Surface Cards #1B202E with Cool Accents",
        background = Color(0xFF12151C),
        surface = Color(0xFF1B202E),
        surfaceVariant = Color(0xFF242A3D),
        primary = Color(0xFF38BDF8),
        secondary = Color(0xFFB388FF),
        textPrimary = Color(0xFFE8ECF2),
        textSecondary = Color(0xFF94A3B8),
        outline = Color(0x20FFFFFF),
        swatches = listOf(Color(0xFF12151C), Color(0xFF1B202E), Color(0xFF38BDF8), Color(0xFFB388FF)),
        isDark = true
    )

    // 5. Midnight Blue
    val MIDNIGHT_BLUE = TitanThemePalette(
        id = "midnight_blue",
        name = "Midnight Blue",
        description = "Deep Navy #0B132B with Soft Teal #48CAE4",
        background = Color(0xFF0B132B),
        surface = Color(0xFF1C2541),
        surfaceVariant = Color(0xFF273456),
        primary = Color(0xFF48CAE4),
        secondary = Color(0xFF5BC0BE),
        textPrimary = Color(0xFFE0FBFC),
        textSecondary = Color(0xFF8D99AE),
        outline = Color(0xFF3A506B),
        swatches = listOf(Color(0xFF0B132B), Color(0xFF48CAE4), Color(0xFF5BC0BE)),
        isDark = true
    )

    val ALL_THEMES = listOf(
        TITAN_PREMIUM_WHITE,
        SOFT_LIGHT_MODE,
        PASTEL_SUNSET_LIGHT,
        SOFT_SLATE_DARK,
        MIDNIGHT_BLUE
    )

    private val _currentTheme = MutableStateFlow(TITAN_PREMIUM_WHITE)
    val currentTheme: StateFlow<TitanThemePalette> = _currentTheme.asStateFlow()

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnforced = prefs.getBoolean("v2_white_theme_enforced", false)
        if (!isEnforced) {
            prefs.edit()
                .putString(KEY_THEME_ID, TITAN_PREMIUM_WHITE.id)
                .putBoolean("v2_white_theme_enforced", true)
                .apply()
            _currentTheme.value = TITAN_PREMIUM_WHITE
        } else {
            val savedThemeId = prefs.getString(KEY_THEME_ID, TITAN_PREMIUM_WHITE.id) ?: TITAN_PREMIUM_WHITE.id
            val found = ALL_THEMES.find { it.id == savedThemeId } ?: TITAN_PREMIUM_WHITE
            _currentTheme.value = found
        }

        val savedName = prefs.getString(KEY_USER_NAME, "") ?: ""
        _userName.value = savedName
    }

    fun setTheme(themeId: String, context: Context? = null) {
        val theme = ALL_THEMES.find { it.id == themeId } ?: TITAN_PREMIUM_WHITE
        _currentTheme.value = theme
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(KEY_THEME_ID, theme.id)
            ?.apply()
    }

    fun setUserName(name: String, context: Context? = null) {
        val cleanName = name.trim()
        _userName.value = cleanName
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(KEY_USER_NAME, cleanName)
            ?.apply()
    }

    fun getDynamicGreeting(customName: String? = null): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val baseGreeting = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }

        val nameToUse = (customName ?: _userName.value).trim()
        return if (nameToUse.isNotEmpty()) {
            "$baseGreeting, $nameToUse"
        } else {
            baseGreeting
        }
    }
}
