package com.example.ui.components


import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.ArrowBack
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.db.UserMemoryVaultEntity
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryVaultSheet(
    memories: List<UserMemoryVaultEntity>,
    onAddMemory: (String, String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onClearAllMemories: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var newKeyInput by remember { mutableStateOf("") }
    var newValueInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val filteredMemories = remember(memories, searchQuery) {
        if (searchQuery.isBlank()) memories
        else memories.filter {
            it.key.contains(searchQuery, ignoreCase = true) || it.value.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = GeminiDarkObsidian,
            contentColor = GeminiTextPrimary
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = GeminiCyanAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Memory Vault",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeminiTextPrimary
                        )
                        Text(
                            text = "On-device brain",
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

            // Developer Creator Identity Info Card
            Surface(
                color = GeminiSurfaceVariant,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, GeminiSparklePurple),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = GeminiSparklePurple,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Developer Tahir",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = GeminiTextPrimary
                        )
                        Text(
                            text = "Telegram: @Tahirsiddik",
                            fontSize = 11.sp,
                            color = GeminiTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar & Clear All Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search memories...", fontSize = 12.sp, color = GeminiTextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GeminiTextSecondary, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GeminiCyanAccent,
                        unfocusedBorderColor = GeminiThinkingBorder,
                        focusedTextColor = GeminiTextPrimary,
                        unfocusedTextColor = GeminiTextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )

                if (memories.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showClearConfirmDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Add Memory Fact Input Form
            Text(
                text = "Add Fact",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = GeminiTextSecondary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newKeyInput,
                    onValueChange = { newKeyInput = it },
                    placeholder = { Text("Key (e.g. user_name)", fontSize = 12.sp, color = GeminiTextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GeminiCyanAccent,
                        unfocusedBorderColor = GeminiThinkingBorder,
                        focusedTextColor = GeminiTextPrimary,
                        unfocusedTextColor = GeminiTextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = newValueInput,
                    onValueChange = { newValueInput = it },
                    placeholder = { Text("Value (e.g. Tahir)", fontSize = 12.sp, color = GeminiTextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GeminiCyanAccent,
                        unfocusedBorderColor = GeminiThinkingBorder,
                        focusedTextColor = GeminiTextPrimary,
                        unfocusedTextColor = GeminiTextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        if (newKeyInput.isNotBlank() && newValueInput.isNotBlank()) {
                            onAddMemory(newKeyInput.trim(), newValueInput.trim())
                            Toast.makeText(context, "Memory saved", Toast.LENGTH_SHORT).show()
                            newKeyInput = ""
                            newValueInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GeminiCyanAccent)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Save",
                        tint = GeminiDarkObsidian
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stored Memory Facts List
            Text(
                text = "Memories (${filteredMemories.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = GeminiTextSecondary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (filteredMemories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No memory facts found", color = GeminiTextSecondary, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredMemories, key = { it.key }) { memory ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = GeminiSurface),
                            border = BorderStroke(0.5.dp, GeminiThinkingBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = memory.key,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = GeminiCyanAccent
                                    )
                                    Text(
                                        text = memory.value,
                                        fontSize = 13.sp,
                                        color = GeminiTextPrimary
                                    )
                                }

                                IconButton(onClick = { onDeleteMemory(memory.key) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = GeminiTextSecondary,
                                        modifier = Modifier.size(18.dp)
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

    // Clear All Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear All Memories?") },
            text = { Text("Delete all saved memories?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllMemories()
                        showClearConfirmDialog = false
                        Toast.makeText(context, "All memories cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Clear All", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
