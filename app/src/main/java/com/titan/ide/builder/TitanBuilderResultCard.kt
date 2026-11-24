package com.titan.ide.builder

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.titan.ide.ui.components.TitanBuilderCard

/**
 * TitanBuilderResultCard - Interactive UI Card for Multi-Language Autonomous Builder
 * Developer: Developer Tahir (@Tahirsiddik)
 */
@Composable
fun TitanBuilderResultCard(
    userPrompt: String,
    onLivePreview: (String) -> Unit = {},
    onOpenInSoraEditor: (html: String, css: String, js: String) -> Unit = { _, _, _ -> },
    onModifyRequest: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    TitanBuilderCard(
        userPrompt = userPrompt,
        onLivePreview = onLivePreview,
        onOpenInSoraEditor = onOpenInSoraEditor,
        onModifyRequest = onModifyRequest,
        modifier = modifier
    )
}
