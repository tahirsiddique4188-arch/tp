package com.example.ui.components


import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.ArrowBack
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.io.File

data class RefactorPreset(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val promptText: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectRefactorSheet(
    projectFolder: File?,
    onApplyAiRefactorPrompt: (prompt: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var customPromptInput by remember { mutableStateOf("") }

    val presets = remember {
        listOf(
            RefactorPreset(
                title = "Cyberpunk Dark Theme",
                description = "Apply neon glowing cyan, purple accent styling & dark backdrop",
                icon = Icons.Default.ColorLens,
                promptText = "Refactor the theme of this project to Cyberpunk Dark with glowing neon cyan (#00F0FF) & purple accents."
            ),
            RefactorPreset(
                title = "Fix Bugs & Errors",
                description = "Audit syntax, DOM errors & broken event handlers",
                icon = Icons.Default.BugReport,
                promptText = "Audit and fix all JavaScript syntax bugs, unhandled null exceptions, and broken DOM event listeners."
            ),
            RefactorPreset(
                title = "Add Audio & FX",
                description = "Synthesize Web Audio API sound effects for interactions",
                icon = Icons.Default.MusicNote,
                promptText = "Add synthesized Web Audio API sound effects (click, win, gameover sounds) on user actions."
            ),
            RefactorPreset(
                title = "Mobile Responsive",
                description = "Touch controls, viewport scaling & mobile fluid CSS",
                icon = Icons.Default.PhoneAndroid,
                promptText = "Make this web app 100% mobile responsive with touch controls, viewport scaling, and fluid layout."
            )
        )
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
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
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
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(GeminiSparklePurple.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = GeminiSparklePurple,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "AI Surgical Refactor",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Active: ${projectFolder?.name ?: "Current Workspace"}",
                            fontSize = 11.sp,
                            color = GeminiTextSecondary
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Close",
                        tint = GeminiTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Presets Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(presets) { preset ->
                    Surface(
                        onClick = {
                            val folder = projectFolder
                            val contextCode = if (folder != null && folder.exists()) {
                                val html = File(folder, "index.html").let { if (it.exists()) it.readText() else "" }
                                val css = File(folder, "style.css").let { if (it.exists()) it.readText() else "" }
                                "\n\nProject files:\n`index.html`:\n```html\n$html\n```\n`style.css`:\n```css\n$css\n```"
                            } else ""

                            onApplyAiRefactorPrompt("${preset.promptText}$contextCode")
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Icon(preset.icon, contentDescription = null, tint = GeminiCyanAccent, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(preset.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(preset.description, fontSize = 11.sp, color = GeminiTextSecondary, maxLines = 2)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Custom Prompt Input
            OutlinedTextField(
                value = customPromptInput,
                onValueChange = { customPromptInput = it },
                placeholder = { Text("Custom instruction (e.g., Add scoring system & high scores)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val custom = customPromptInput.trim()
                    if (custom.isNotEmpty()) {
                        val folder = projectFolder
                        val contextCode = if (folder != null && folder.exists()) {
                            val html = File(folder, "index.html").let { if (it.exists()) it.readText() else "" }
                            "\n\nProject index.html:\n```html\n$html\n```"
                        } else ""

                        onApplyAiRefactorPrompt("$custom$contextCode")
                        onDismiss()
                    } else {
                        Toast.makeText(context, "Please select a preset or type a prompt", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GeminiSparklePurple, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Refactor Project with Titan AI", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    
        }
    }
}
