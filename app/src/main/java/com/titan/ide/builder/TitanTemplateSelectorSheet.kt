package com.titan.ide.builder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val CyberSlateBg = Color(0xFF1B202E)
private val CyberSlateCard = Color(0xFF22283A)
private val CyberSlateBorder = Color(0xFF2E384D)
private val NeonCyan = Color(0xFF00E5FF)
private val NeonPurple = Color(0xFFA142F4)
private val NeonGreen = Color(0xFF00E676)
private val NeonAmber = Color(0xFFFFB74D)

/**
 * TitanTemplateSelectorSheet - Modal dialog for selecting pre-defined scaffold structures
 * Developer: Developer Tahir (@Tahirsiddik)
 */
@Composable
fun TitanTemplateSelectorSheet(
    onDismiss: () -> Unit,
    onTemplateSelected: (template: ProjectScaffoldTemplate, prompt: String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedTemplate by remember { mutableStateOf<ProjectScaffoldTemplate>(TitanProjectTemplatesList.TEMPLATES.first()) }
    var promptInput by remember { mutableStateOf(selectedTemplate.defaultPromptHint) }

    val categories = remember {
        listOf("All", "Android / Kotlin", "Web Frontend", "Python Backend")
    }

    val filteredTemplates = remember(selectedCategory) {
        if (selectedCategory == "All") {
            TitanProjectTemplatesList.TEMPLATES
        } else {
            TitanProjectTemplatesList.TEMPLATES.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSlateBg),
            border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(NeonCyan, NeonPurple))),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(NeonCyan, NeonPurple))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Widgets, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Project Scaffold Templates",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Select pre-defined architecture before auto-generation",
                                fontSize = 11.sp,
                                color = NeonCyan
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF9EADC8), modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Category Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        val isSelected = cat == selectedCategory
                        Surface(
                            onClick = { selectedCategory = cat },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) NeonCyan else CyberSlateCard,
                            border = BorderStroke(1.dp, if (isSelected) NeonCyan else CyberSlateBorder)
                        ) {
                            Text(
                                text = cat,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else Color(0xFF9EADC8),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scaffold Templates List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredTemplates) { template ->
                        val isSelected = template.id == selectedTemplate.id
                        Surface(
                            onClick = {
                                selectedTemplate = template
                                promptInput = template.defaultPromptHint
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) CyberSlateCard else Color(0xFF161A26),
                            border = BorderStroke(
                                1.5.dp,
                                if (isSelected) NeonCyan else CyberSlateBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(template.icon, fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = template.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = NeonPurple.copy(alpha = 0.2f),
                                        border = BorderStroke(0.5.dp, NeonPurple)
                                    ) {
                                        Text(
                                            text = template.category,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NeonPurple,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = template.description,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF9EADC8),
                                    lineHeight = 16.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                // File Tree Chips
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(13.dp))
                                    Text(
                                        text = template.fileNames.joinToString("  •  "),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = NeonCyan
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom Prompt Input Field
                Text(
                    text = "CUSTOM PROMPT & SPECIFICATION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF9EADC8),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = { Text("Describe custom features or requirements...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CyberSlateCard,
                        unfocusedContainerColor = CyberSlateCard,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CyberSlateBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp, max = 90.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Launch Auto-Generation Button
                Button(
                    onClick = {
                        val finalPrompt = promptInput.ifBlank { selectedTemplate.defaultPromptHint }
                        onTemplateSelected(selectedTemplate, finalPrompt)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🚀 Start Auto-Generation (${selectedTemplate.title})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
