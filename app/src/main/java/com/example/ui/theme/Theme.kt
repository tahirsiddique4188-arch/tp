package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState

@Composable
fun MyApplicationTheme(
    themePalette: TitanThemePalette = TitanThemeManager.currentTheme.collectAsState().value,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalTitanTheme provides themePalette) {
        MaterialTheme(
            colorScheme = themePalette.colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    isDarkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val activePalette = TitanThemeManager.currentTheme.collectAsState().value
    MyApplicationTheme(
        themePalette = activePalette,
        content = content
    )
}
