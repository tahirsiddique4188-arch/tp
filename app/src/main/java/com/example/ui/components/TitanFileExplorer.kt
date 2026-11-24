package com.example.ui.components


import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.ArrowBack
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.util.GitHubCloner
import com.example.util.TitanLocalServerManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitanFileExplorerSheet(
    onLiveRunProject: (projectFolder: File) -> Unit,
    onEditWithAiPrompt: (filePath: String, codeContent: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val projectsDir = remember { GitHubCloner.getProjectsDir(context) }

    var currentDir by remember { mutableStateOf(projectsDir) }
    var selectedFileForEditor by remember { mutableStateOf<File?>(null) }

    // Dialog state
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileNameInput by remember { mutableStateOf("") }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }

    var fileToRename by remember { mutableStateOf<File?>(null) }
    var renameInput by remember { mutableStateOf("") }

    var fileToDelete by remember { mutableStateOf<File?>(null) }

    // Refresh trigger
    var refreshKey by remember { mutableStateOf(0) }
    val filesList = remember(currentDir, refreshKey) {
        currentDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
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
        val activeEditorFile = selectedFileForEditor
        if (activeEditorFile != null) {
            // Full-Screen File Viewer / Editor
            CodeFileEditorView(
                file = activeEditorFile,
                onSave = { updatedContent ->
                    try {
                        selectedFileForEditor?.writeText(updatedContent)
                        Toast.makeText(context, "Saved ${selectedFileForEditor?.name.orEmpty()}", Toast.LENGTH_SHORT).show()
                        refreshKey++
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                onEditWithAi = { code ->
                    val path = selectedFileForEditor?.absolutePath.orEmpty()
                    onDismiss()
                    onEditWithAiPrompt(path, code)
                },
                onLiveRun = {
                    val parentProj = selectedFileForEditor?.let { if (it.isDirectory) it else it.parentFile } ?: projectsDir
                    TitanLocalServerManager.setActiveProjectDir(parentProj)
                    onLiveRunProject(parentProj)
                    onDismiss()
                },
                onClose = { selectedFileForEditor = null }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (currentDir != projectsDir) {
                            IconButton(
                                onClick = {
                                    currentDir.parentFile?.let { currentDir = it }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = GeminiCyanAccent
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GeminiCyanAccent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = GeminiCyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "Titan File Explorer",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = GeminiTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Workspace Toolbar: [New File] [New Folder] [Live Run Project]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showNewFileDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New File", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { showNewFolderDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Folder", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val targetProject = if (currentDir == projectsDir) {
                                filesList.firstOrNull { it.isDirectory } ?: currentDir
                            } else currentDir

                            TitanLocalServerManager.setActiveProjectDir(targetProject)
                            onLiveRunProject(targetProject)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Live Run", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Directory Contents List
                if (filesList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Directory is empty.\nTap 'New File' or 'Folder' above.",
                            color = GeminiTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filesList, key = { it.absolutePath }) { file ->
                            FileExplorerItemRow(
                                file = file,
                                onClick = {
                                    if (file.isDirectory) {
                                        currentDir = file
                                    } else {
                                        selectedFileForEditor = file
                                    }
                                },
                                onRename = {
                                    fileToRename = file
                                    renameInput = file.name
                                },
                                onDelete = {
                                    fileToDelete = file
                                }
                            )
                        }
                    }
                }
            }
        }
    
        }
    }

    // New File Dialog
    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Create New File") },
            text = {
                OutlinedTextField(
                    value = newFileNameInput,
                    onValueChange = { newFileNameInput = it },
                    placeholder = { Text("index.html, styles.css, app.js") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newFileNameInput.trim()
                        if (name.isNotEmpty()) {
                            val newFile = File(currentDir, name)
                            if (newFile.createNewFile()) {
                                if (name.endsWith(".html")) {
                                    newFile.writeText("<!DOCTYPE html>\n<html>\n<head><title>New Page</title></head>\n<body><h1>Hello Titan</h1></body>\n</html>")
                                }
                                Toast.makeText(context, "Created $name", Toast.LENGTH_SHORT).show()
                                refreshKey++
                            }
                        }
                        showNewFileDialog = false
                        newFileNameInput = ""
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) { Text("Cancel") }
            }
        )
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderNameInput,
                    onValueChange = { newFolderNameInput = it },
                    placeholder = { Text("MyNewProject") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newFolderNameInput.trim()
                        if (name.isNotEmpty()) {
                            val newFolder = File(currentDir, name)
                            if (newFolder.mkdirs()) {
                                Toast.makeText(context, "Created folder $name", Toast.LENGTH_SHORT).show()
                                refreshKey++
                            }
                        }
                        showNewFolderDialog = false
                        newFolderNameInput = ""
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Rename Dialog
    if (fileToRename != null) {
        AlertDialog(
            onDismissRequest = { fileToRename = null },
            title = { Text("Rename ${fileToRename?.name.orEmpty()}") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = fileToRename
                        val newName = renameInput.trim()
                        if (target != null && newName.isNotEmpty()) {
                            val dest = File(target.parentFile, newName)
                            if (target.renameTo(dest)) {
                                Toast.makeText(context, "Renamed to $newName", Toast.LENGTH_SHORT).show()
                                refreshKey++
                            }
                        }
                        fileToRename = null
                    }
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { fileToRename = null }) { Text("Cancel") }
            }
        )
    }

    // Delete Confirmation Dialog
    if (fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete ${fileToDelete?.name.orEmpty()}?") },
            text = { Text("Are you sure you want to delete this file/directory permanently?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = fileToDelete
                        if (target != null) {
                            if (target.isDirectory) {
                                target.deleteRecursively()
                            } else {
                                target.delete()
                            }
                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                            refreshKey++
                        }
                        fileToDelete = null
                    }
                ) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FileExplorerItemRow(
    file: File,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val icon = when {
                    file.isDirectory -> Icons.Default.Folder
                    file.name.endsWith(".html", true) -> Icons.Default.Code
                    file.name.endsWith(".css", true) -> Icons.Default.Description
                    file.name.endsWith(".js", true) -> Icons.Default.Terminal
                    file.name.endsWith(".py", true) -> Icons.Default.Terminal
                    file.name.endsWith(".png", true) || file.name.endsWith(".jpg", true) -> Icons.Default.Image
                    else -> Icons.Default.Description
                }

                val tint = when {
                    file.isDirectory -> Color(0xFFFFB74D)
                    file.name.endsWith(".html", true) -> Color(0xFFFF5252)
                    file.name.endsWith(".css", true) -> Color(0xFF4285F4)
                    file.name.endsWith(".js", true) -> Color(0xFFFFD600)
                    else -> GeminiCyanAccent
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = file.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (file.isDirectory) "${file.listFiles()?.size ?: 0} items" else "${file.length() / 1024} KB",
                        fontSize = 11.sp,
                        color = GeminiTextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename", tint = GeminiTextSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CodeFileEditorView(
    file: File,
    onSave: (String) -> Unit,
    onEditWithAi: (String) -> Unit,
    onLiveRun: () -> Unit,
    onClose: () -> Unit
) {
    var codeState by remember(file) {
        mutableStateOf(
            try { file.readText() } catch (e: Exception) { "// Error reading file" }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = GeminiCyanAccent)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = file.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSave(codeState) }) {
                    Icon(Icons.Default.Save, contentDescription = "Save File", tint = GeminiCyanAccent)
                }

                Button(
                    onClick = { onEditWithAi(codeState) },
                    colors = ButtonDefaults.buttonColors(containerColor = GeminiSparklePurple, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AI Edit", fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = onLiveRun,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Run", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Code Editor Text Area with Monospace Font
        OutlinedTextField(
            value = codeState,
            onValueChange = { codeState = it },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground
            ),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}
