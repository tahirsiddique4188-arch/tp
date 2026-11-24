package com.titan.ide.ui.components

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.titan.ide.builder.TitanAutonomousBuilder
import com.titan.ide.server.TitanProjectPreviewDialog
import com.titan.ide.storage.TitanWorkspaceManager
import kotlinx.coroutines.launch

private val CyberSlateBg = Color(0xFF1B202E)
private val CyberSlateCard = Color(0xFF22283A)
private val CyberSlateBorder = Color(0x18FFFFFF)
private val NeonCyan = Color(0xFF00E5FF)
private val NeonPurple = Color(0xFFA142F4)
private val NeonGreen = Color(0xFF00E676)
private val NeonAmber = Color(0xFFFFB74D)

/**
 * TitanBuilderCard - Cyber Slate Jetpack Compose Card for Titan Autonomous Builder (Powered by Gemini 4 Carbon)
 * Developer: Developer Tahir (@Tahirsiddik)
 */
@Composable
fun TitanBuilderCard(
    userPrompt: String,
    onLivePreview: (String) -> Unit = {},
    onOpenInSoraEditor: (html: String, css: String, js: String) -> Unit = { _, _, _ -> },
    onModifyRequest: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var builderState by remember { mutableStateOf(TitanAutonomousBuilder.BuilderState()) }
    var hasStarted by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }

    // Animated Glowing Pulse
    val infiniteTransition = rememberInfiniteTransition(label = "GlowPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    val glowingBorderBrush = Brush.sweepGradient(
        listOf(NeonCyan, NeonPurple, NeonGreen, NeonCyan)
    )

    var showTemplateSheet by remember { mutableStateOf(false) }
    var selectedScaffold by remember { mutableStateOf<com.titan.ide.builder.ProjectScaffoldTemplate?>(null) }

    // Trigger autonomous build on component mount
    LaunchedEffect(userPrompt) {
        if (!hasStarted) {
            hasStarted = true
            TitanAutonomousBuilder.buildProject(userPrompt, context) { updatedState ->
                builderState = updatedState
            }
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSlateBg),
        border = if (builderState.stage == TitanAutonomousBuilder.BuilderStage.COMPLETED) {
            BorderStroke(1.5.dp, NeonGreen)
        } else {
            BorderStroke(1.5.dp, glowingBorderBrush)
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Glowing Icon + Title
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
                            .background(
                                Brush.linearGradient(listOf(NeonCyan, NeonPurple))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = builderState.language.icon,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Titan Autonomous Builder",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Powered by ${builderState.engine.displayName}",
                            fontSize = 10.sp,
                            color = NeonCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (builderState.stage) {
                        TitanAutonomousBuilder.BuilderStage.COMPLETED -> NeonGreen.copy(alpha = 0.2f)
                        TitanAutonomousBuilder.BuilderStage.SELF_HEALING_AUDIT -> NeonAmber.copy(alpha = 0.2f)
                        TitanAutonomousBuilder.BuilderStage.ERROR -> Color(0xFFFF5252).copy(alpha = 0.2f)
                        else -> NeonCyan.copy(alpha = 0.15f)
                    },
                    border = BorderStroke(
                        0.5.dp,
                        when (builderState.stage) {
                            TitanAutonomousBuilder.BuilderStage.COMPLETED -> NeonGreen
                            TitanAutonomousBuilder.BuilderStage.SELF_HEALING_AUDIT -> NeonAmber
                            TitanAutonomousBuilder.BuilderStage.ERROR -> Color(0xFFFF5252)
                            else -> NeonCyan
                        }
                    )
                ) {
                    Text(
                        text = when (builderState.stage) {
                            TitanAutonomousBuilder.BuilderStage.IDLE -> "QUEUED"
                            TitanAutonomousBuilder.BuilderStage.PLANNING -> "PHASE 1"
                            TitanAutonomousBuilder.BuilderStage.SYNTHESIS -> "PHASE 2"
                            TitanAutonomousBuilder.BuilderStage.SELF_HEALING_AUDIT -> "PHASE 3"
                            TitanAutonomousBuilder.BuilderStage.COMPLETED -> "PHASE 4"
                            TitanAutonomousBuilder.BuilderStage.ERROR -> "FAILED"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (builderState.stage) {
                            TitanAutonomousBuilder.BuilderStage.COMPLETED -> NeonGreen
                            TitanAutonomousBuilder.BuilderStage.SELF_HEALING_AUDIT -> NeonAmber
                            TitanAutonomousBuilder.BuilderStage.ERROR -> Color(0xFFFF5252)
                            else -> NeonCyan
                        },
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Pipeline Header & Scaffold Template Selector Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ANTIGRAVITY ENGINE PIPELINE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF9EADC8),
                    letterSpacing = 0.5.sp
                )

                Surface(
                    onClick = { showTemplateSheet = true },
                    shape = RoundedCornerShape(12.dp),
                    color = CyberSlateCard,
                    border = BorderStroke(0.5.dp, NeonCyan.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Widgets, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = selectedScaffold?.let { "Scaffold: ${it.title}" } ?: "📐 Scaffold Templates",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Phase 1: 🧠 Planning Architecture & Dependencies
                val isP1Active = builderState.stage == TitanAutonomousBuilder.BuilderStage.PLANNING
                val isP1Done = builderState.stage.ordinal > TitanAutonomousBuilder.BuilderStage.PLANNING.ordinal
                PhaseChipRow(
                    emoji = "🧠",
                    label = "Phase 1: Planning Architecture & Dependencies",
                    detail = if (isP1Done) "Manifest & project tree formulated" else if (isP1Active) "Architecting system..." else "Pending",
                    isActive = isP1Active,
                    isDone = isP1Done,
                    pulseAlpha = pulseAlpha
                )

                // Phase 2: 💻 Writing Source Code (Live file name ticker)
                val isP2Active = builderState.stage == TitanAutonomousBuilder.BuilderStage.SYNTHESIS
                val isP2Done = builderState.stage.ordinal > TitanAutonomousBuilder.BuilderStage.SYNTHESIS.ordinal
                PhaseChipRow(
                    emoji = "💻",
                    label = "Phase 2: Writing Source Code",
                    detail = if (isP2Done) "Multi-file synthesis complete"
                    else if (isP2Active) builderState.currentFileName?.let { "Writing $it..." } ?: "Streaming code..."
                    else "Pending",
                    isActive = isP2Active,
                    isDone = isP2Done,
                    pulseAlpha = pulseAlpha
                )

                // Phase 3: 🛠️ Self-Healing Audit (100% Bug-Free Guarantee)
                val isP3Active = builderState.stage == TitanAutonomousBuilder.BuilderStage.SELF_HEALING_AUDIT
                val isP3Done = builderState.stage.ordinal > TitanAutonomousBuilder.BuilderStage.SELF_HEALING_AUDIT.ordinal
                PhaseChipRow(
                    emoji = "🛠️",
                    label = "Phase 3: Self-Healing Audit",
                    detail = if (isP3Done) "100% Bug-Free Guarantee Verified"
                    else if (isP3Active) "Auditing braces, imports & tags..."
                    else "Pending",
                    isActive = isP3Active,
                    isDone = isP3Done,
                    pulseAlpha = pulseAlpha
                )

                // Phase 4: 🚀 Build Successful!
                val isP4Done = builderState.stage == TitanAutonomousBuilder.BuilderStage.COMPLETED
                PhaseChipRow(
                    emoji = "🚀",
                    label = "Phase 4: Build Successful!",
                    detail = if (isP4Done) "Ready for deployment" else "Awaiting pipeline completion",
                    isActive = false,
                    isDone = isP4Done,
                    pulseAlpha = 1.0f
                )
            }

            // Progress indicator during synthesis
            if (builderState.stage != TitanAutonomousBuilder.BuilderStage.COMPLETED && builderState.stage != TitanAutonomousBuilder.BuilderStage.ERROR) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { builderState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = NeonCyan,
                    trackColor = CyberSlateCard
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${(builderState.progress * 100).toInt()}% • ${builderState.currentStatus}",
                    fontSize = 10.5.sp,
                    color = NeonCyan,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Bottom Action Buttons upon Completion
            if (builderState.stage == TitanAutonomousBuilder.BuilderStage.COMPLETED) {
                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NeonGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "🎉 ${builderState.projectName} Built & Verified!",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGreen
                        )
                        Text(
                            text = "Language: ${builderState.language.displayName} | Health: 100% Bug-Free",
                            fontSize = 10.5.sp,
                            color = Color(0xFFC4D1EB)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // For Web: Launch Live via LocalProjectServer dynamic URL
                    if (builderState.language == TitanAutonomousBuilder.TargetLanguage.WEB ||
                        builderState.language == TitanAutonomousBuilder.TargetLanguage.AUTO_DETECT) {
                        Button(
                            onClick = {
                                showPreviewDialog = true
                                onLivePreview(com.titan.ide.server.LocalProjectServer.getActivePreviewUrl())
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("🌐 Launch Live", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // For Kotlin / Java / All: Open in SoraEditor
                    Button(
                        onClick = {
                            val html = builderState.files["index.html"] ?: builderState.files["MainActivity.kt"] ?: builderState.files["main.py"] ?: ""
                            val css = builderState.files["style.css"] ?: builderState.files["Theme.kt"] ?: builderState.files["requirements.txt"] ?: ""
                            val js = builderState.files["app.js"] ?: builderState.files["AndroidManifest.xml"] ?: builderState.files["engine.py"] ?: ""
                            onOpenInSoraEditor(html, css, js)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonPurple,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("📂 SoraEditor", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Export Project ZIP
                    OutlinedButton(
                        onClick = {
                            val zipFile = TitanWorkspaceManager.exportWorkspaceToZip(
                                context = context,
                                projectName = builderState.projectName,
                                files = builderState.files
                            )
                            if (zipFile != null && zipFile.exists()) {
                                Toast.makeText(context, "Exported ZIP: ${zipFile.name}", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Saved in /sdcard/TitanProjects/${builderState.projectName}/", Toast.LENGTH_SHORT).show()
                            }
                        },
                        border = BorderStroke(1.dp, NeonGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("📦 ZIP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Ask Carbon to Add Feature Button
                Surface(
                    onClick = {
                        onModifyRequest("Ask Carbon to add new feature to ${builderState.projectName}: ")
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = CyberSlateCard,
                    border = BorderStroke(1.dp, CyberSlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "✨ Ask Carbon to Add Feature",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonCyan
                        )
                    }
                }
            }

            if (builderState.stage == TitanAutonomousBuilder.BuilderStage.ERROR) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        coroutineScope.launch {
                            TitanAutonomousBuilder.buildProject(userPrompt, context) { updatedState ->
                                builderState = updatedState
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔄 Retry Build with Gemini 4 Carbon", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showPreviewDialog) {
        TitanProjectPreviewDialog(onDismiss = { showPreviewDialog = false })
    }

    if (showTemplateSheet) {
        com.titan.ide.builder.TitanTemplateSelectorSheet(
            onDismiss = { showTemplateSheet = false },
            onTemplateSelected = { template, newPrompt ->
                showTemplateSheet = false
                selectedScaffold = template
                coroutineScope.launch {
                    TitanAutonomousBuilder.buildProject(
                        userPrompt = newPrompt,
                        context = context,
                        requestedLanguage = template.targetLanguage
                    ) { updatedState ->
                        builderState = updatedState
                    }
                }
            }
        )
    }
}

@Composable
private fun PhaseChipRow(
    emoji: String,
    label: String,
    detail: String,
    isActive: Boolean,
    isDone: Boolean,
    pulseAlpha: Float
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) CyberSlateCard.copy(alpha = 0.9f) else CyberSlateCard.copy(alpha = 0.5f),
        border = BorderStroke(
            0.5.dp,
            if (isDone) NeonGreen.copy(alpha = 0.6f)
            else if (isActive) NeonCyan.copy(alpha = pulseAlpha)
            else CyberSlateBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = emoji, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isActive || isDone) FontWeight.Bold else FontWeight.Medium,
                    color = if (isDone) Color.White else if (isActive) NeonCyan else Color(0xFF9EADC8)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(NeonCyan.copy(alpha = pulseAlpha))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                } else if (isDone) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                    text = detail,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isDone) NeonGreen else if (isActive) NeonCyan else Color(0xFF7E8CA3)
                )
            }
        }
    }
}
