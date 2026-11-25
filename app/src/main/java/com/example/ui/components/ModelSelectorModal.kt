package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.GeminiModelsList
import com.example.data.model.ModelCategory
import com.example.data.model.ModelInfo
import com.example.data.model.ModelProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorModal(
    currentModel: ModelInfo,
    availableModels: List<ModelInfo> = GeminiModelsList.ALL_MODELS,
    onModelSelected: (ModelInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedProvider by remember { mutableStateOf(currentModel.provider) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<ModelCategory?>(null) }

    val activeModelsList = remember(selectedProvider, availableModels) {
        availableModels.filter { it.provider == selectedProvider }.ifEmpty { availableModels }
    }

    val filteredModels = remember(activeModelsList, searchQuery, selectedCategoryFilter, selectedProvider) {
        activeModelsList.filter { model ->
            val matchesQuery = searchQuery.isEmpty() ||
                    model.name.contains(searchQuery, ignoreCase = true) ||
                    model.id.contains(searchQuery, ignoreCase = true) ||
                    model.description.contains(searchQuery, ignoreCase = true)

            val matchesCategory = selectedCategoryFilter == null || model.category == selectedCategoryFilter

            matchesQuery && matchesCategory
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val activeIcon = when (selectedProvider) {
                            ModelProvider.FLOW -> Icons.Default.Videocam
                            ModelProvider.IMAGEN -> Icons.Default.AutoAwesome
                            ModelProvider.VOICE -> Icons.Default.RecordVoiceOver
                            ModelProvider.GEMINI -> Icons.Default.AutoAwesome
                        }
                        val activeColor = when (selectedProvider) {
                            ModelProvider.FLOW -> Color(0xFF8B5CF6)
                            ModelProvider.IMAGEN -> Color(0xFF06B6D4)
                            ModelProvider.VOICE -> Color(0xFF10B981)
                            ModelProvider.GEMINI -> MaterialTheme.colorScheme.primary
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(activeColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = activeIcon,
                                contentDescription = null,
                                tint = activeColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "Model Catalog",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${availableModels.size} Models",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Pure Google Ecosystem Switcher Tabs (Gemini, Flow, Imagen, Voice)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Gemini Tab (20 Models)
                        ProviderTabButton(
                            title = "Gemini",
                            count = GeminiModelsList.GEMINI_MODELS.size,
                            isSelected = selectedProvider == ModelProvider.GEMINI,
                            activeColor = MaterialTheme.colorScheme.primary,
                            icon = Icons.Default.AutoAwesome
                        ) {
                            selectedProvider = ModelProvider.GEMINI
                            searchQuery = ""
                            selectedCategoryFilter = null
                        }

                        // Google Flow Tab (10 Models)
                        ProviderTabButton(
                            title = "Flow",
                            count = GeminiModelsList.FLOW_MODELS.size,
                            isSelected = selectedProvider == ModelProvider.FLOW,
                            activeColor = Color(0xFF8B5CF6),
                            icon = Icons.Default.Videocam
                        ) {
                            selectedProvider = ModelProvider.FLOW
                            searchQuery = ""
                            selectedCategoryFilter = null
                        }

                        // Imagen Studio Tab (7 Models)
                        ProviderTabButton(
                            title = "Imagen",
                            count = GeminiModelsList.IMAGE_STUDIO_MODELS.size,
                            isSelected = selectedProvider == ModelProvider.IMAGEN,
                            activeColor = Color(0xFF06B6D4),
                            icon = Icons.Default.AutoAwesome
                        ) {
                            selectedProvider = ModelProvider.IMAGEN
                            searchQuery = ""
                            selectedCategoryFilter = null
                        }

                        // Live Voice & TTS Tab (14 Models)
                        ProviderTabButton(
                            title = "Voice",
                            count = GeminiModelsList.VOICE_MODELS.size,
                            isSelected = selectedProvider == ModelProvider.VOICE,
                            activeColor = Color(0xFF10B981),
                            icon = Icons.Default.RecordVoiceOver
                        ) {
                            selectedProvider = ModelProvider.VOICE
                            searchQuery = ""
                            selectedCategoryFilter = null
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search models...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Models List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val grouped = filteredModels.groupBy { it.category }

                    grouped.forEach { (category, models) ->
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            ) {
                                val categoryIcon: ImageVector = when (category) {
                                    ModelCategory.FLOW -> Icons.Default.Videocam
                                    ModelCategory.IMAGE_STUDIO -> Icons.Default.Image
                                    ModelCategory.VOICE_AUDIO -> Icons.Default.RecordVoiceOver
                                    ModelCategory.DEEP_RESEARCH -> Icons.Default.Science
                                    ModelCategory.THINKING -> Icons.Default.Psychology
                                    ModelCategory.CHAT_CODING -> Icons.Default.AutoAwesome
                                }
                                Icon(
                                    imageVector = categoryIcon,
                                    contentDescription = null,
                                    tint = Color(category.badgeColor),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${category.displayName.uppercase()} (${models.size})",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(category.badgeColor),
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        items(models, key = { it.id }) { model ->
                            val isSelected = model.id == currentModel.id
                            val activeColor = when (model.provider) {
                                ModelProvider.FLOW -> Color(0xFF8B5CF6)
                                ModelProvider.IMAGEN -> Color(0xFF06B6D4)
                                ModelProvider.VOICE -> Color(0xFF10B981)
                                ModelProvider.GEMINI -> MaterialTheme.colorScheme.primary
                            }

                            Surface(
                                onClick = {
                                    onModelSelected(model)
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                                border = if (isSelected) {
                                    BorderStroke(1.5.dp, activeColor)
                                } else {
                                    BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline)
                                },
                                tonalElevation = if (isSelected) 3.dp else 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = model.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.5.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            model.badge?.let { badge ->
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    color = Color(model.category.badgeColor).copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = badge,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(model.category.badgeColor),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text(
                                            text = model.description,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }

                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(activeColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderTabButton(
    title: String,
    count: Int,
    isSelected: Boolean,
    activeColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) activeColor.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
