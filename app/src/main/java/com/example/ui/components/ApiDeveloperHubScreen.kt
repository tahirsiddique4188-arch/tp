package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.TitanApiKey
import com.example.util.TitanApiKeyManager
import com.example.util.TitanLocalServerManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiDeveloperHubScreen(
    onDismiss: () -> Unit,
    onScaffoldApi: (prompt: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val isServerRunning by TitanLocalServerManager.isServerRunning.collectAsState()
    val apiKeys by TitanApiKeyManager.apiKeys.collectAsState()

    var showNewKeyDialog by remember { mutableStateOf(false) }
    var newKeyLabelInput by remember { mutableStateOf("") }
    var selectedSnippetTab by remember { mutableIntStateOf(0) }

    val primaryKey = remember(apiKeys) {
        apiKeys.firstOrNull { it.isActive }?.key ?: "titan-sk-demo-key"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Titan Developer Hub",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isServerRunning) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, if (isServerRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.error)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isServerRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.error)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isServerRunning) "PORT 8080" else "OFFLINE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isServerRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Local Server Dispatcher Card
                    item {
                        ServerGatewayCard(
                            isServerRunning = isServerRunning,
                            port = TitanLocalServerManager.serverPort,
                            localEndpoint = TitanLocalServerManager.localChatCompletionsUrl,
                            cloudEndpoint = TitanLocalServerManager.CLOUD_CHAT_COMPLETIONS_URL,
                            onToggleServer = {
                                TitanLocalServerManager.toggleServer()
                                Toast.makeText(
                                    context,
                                    if (!isServerRunning) "Titan Server Started on 127.0.0.1:8080" else "Titan Server Stopped",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onCopyUrl = { url, label ->
                                clipboardManager.setText(AnnotatedString(url))
                                Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    // 2. Personal API Keys Card
                    item {
                        PersonalApiKeysCard(
                            keys = apiKeys,
                            onGenerateClick = { showNewKeyDialog = true },
                            onCopyKey = { key ->
                                clipboardManager.setText(AnnotatedString(key))
                                Toast.makeText(context, "API Key copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            onRevokeKey = { keyId ->
                                TitanApiKeyManager.revokeKey(keyId, context)
                                Toast.makeText(context, "Key revoked", Toast.LENGTH_SHORT).show()
                            },
                            onReactivateKey = { keyId ->
                                TitanApiKeyManager.reactivateKey(keyId, context)
                                Toast.makeText(context, "Key reactivated", Toast.LENGTH_SHORT).show()
                            },
                            onDeleteKey = { keyId ->
                                TitanApiKeyManager.deleteKey(keyId, context)
                                Toast.makeText(context, "Key deleted", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    // 3. Endpoint Documentation & Code Snippets Card
                    item {
                        EndpointDocsCard(
                            selectedTab = selectedSnippetTab,
                            onTabSelected = { selectedSnippetTab = it },
                            activeKey = primaryKey,
                            onCopyCode = { code, lang ->
                                clipboardManager.setText(AnnotatedString(code))
                                Toast.makeText(context, "$lang code copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    // 4. 1-Click Custom API Scaffolder Card
                    item {
                        ApiScaffolderCard(
                            onSelectPreset = { prompt ->
                                onDismiss()
                                onScaffoldApi(prompt)
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal to generate new custom key
    if (showNewKeyDialog) {
        AlertDialog(
            onDismissRequest = { showNewKeyDialog = false },
            title = {
                Text("Generate Personal API Key", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Give this key a descriptive label (e.g. 'Termux Bot', 'VSCode Proxy', 'Web Hook'):",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newKeyLabelInput,
                        onValueChange = { newKeyLabelInput = it },
                        placeholder = { Text("e.g. My Python Script") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val created = TitanApiKeyManager.generateKey(
                            label = newKeyLabelInput.ifBlank { "Personal API Key" },
                            context = context
                        )
                        showNewKeyDialog = false
                        newKeyLabelInput = ""
                        clipboardManager.setText(AnnotatedString(created.key))
                        Toast.makeText(context, "Key generated and copied to clipboard!", Toast.LENGTH_LONG).show()
                    }
                ) {
                    Text("Generate & Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ServerGatewayCard(
    isServerRunning: Boolean,
    port: Int,
    localEndpoint: String,
    cloudEndpoint: String,
    onToggleServer: () -> Unit,
    onCopyUrl: (String, String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Local REST API Server",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (isServerRunning) "Serving HTTP/2 on 127.0.0.1:$port" else "Server is stopped",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onToggleServer,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isServerRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isServerRunning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = if (isServerRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isServerRunning) "Stop" else "Start", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Endpoint Row 1: Local REST API
            EndpointRow(
                label = "Local Base URL (No Internet Needed)",
                url = localEndpoint,
                onCopy = { onCopyUrl(localEndpoint, "Local Endpoint") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Endpoint Row 2: Direct Cloud Gateway
            EndpointRow(
                label = "Cloud Gateway Proxy URL",
                url = cloudEndpoint,
                onCopy = { onCopyUrl(cloudEndpoint, "Cloud Endpoint") }
            )
        }
    }
}

@Composable
private fun EndpointRow(
    label: String,
    url: String,
    onCopy: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                SelectionContainer {
                    Text(
                        text = url,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy URL",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun PersonalApiKeysCard(
    keys: List<TitanApiKey>,
    onGenerateClick: () -> Unit,
    onCopyKey: (String) -> Unit,
    onRevokeKey: (String) -> Unit,
    onReactivateKey: (String) -> Unit,
    onDeleteKey: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFB923C).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = Color(0xFFFB923C),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Personal API Keys",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Encrypted local validation • Zero latency",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onGenerateClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Generate Key", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (keys.isEmpty()) {
                Text(
                    text = "No keys yet. Click 'Generate Key' above to create one.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.forEach { apiKey ->
                        ApiKeyItemRow(
                            apiKey = apiKey,
                            onCopyKey = { onCopyKey(apiKey.key) },
                            onToggleRevoke = {
                                if (apiKey.isActive) onRevokeKey(apiKey.id) else onReactivateKey(apiKey.id)
                            },
                            onDelete = { onDeleteKey(apiKey.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeyItemRow(
    apiKey: TitanApiKey,
    onCopyKey: () -> Unit,
    onToggleRevoke: () -> Unit,
    onDelete: () -> Unit
) {
    var isMasked by remember { mutableStateOf(true) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            0.8.dp,
            if (apiKey.isActive) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = apiKey.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (apiKey.isActive) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (apiKey.isActive) "Active" else "Revoked",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (apiKey.isActive) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "Usage: ${apiKey.usageCount}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Key display row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isMasked) {
                            apiKey.key.take(12) + "••••••••••••"
                        } else {
                            apiKey.key
                        },
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(
                    onClick = { isMasked = !isMasked },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isMasked) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle mask",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onCopyKey,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Key",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action row: Revoke & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onToggleRevoke,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (apiKey.isActive) "Revoke" else "Reactivate",
                        fontSize = 11.sp,
                        color = if (apiKey.isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Key",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EndpointDocsCard(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    activeKey: String,
    onCopyCode: (String, String) -> Unit
) {
    val tabs = listOf("cURL", "Python (OpenAI)", "JavaScript", "Python (Requests)")

    val curlCode = """
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $activeKey" \
  -d '{
    "model": "gemini-4-hydrogen",
    "messages": [
      {"role": "user", "content": "Hello from external client!"}
    ],
    "stream": false
  }'
    """.trimIndent()

    val pythonOpenAiCode = """
from openai import OpenAI

# Connect to Titan Mobile IDE Local Server
client = OpenAI(
    base_url="http://127.0.0.1:8080/v1",
    api_key="$activeKey"
)

response = client.chat.completions.create(
    model="gemini-4-hydrogen",
    messages=[
        {"role": "user", "content": "Explain async/await in Python"}
    ],
    stream=True
)

for chunk in response:
    content = chunk.choices[0].delta.content or ""
    print(content, end="", flush=True)
    """.trimIndent()

    val jsCode = """
const response = await fetch("http://127.0.0.1:8080/v1/chat/completions", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "Authorization": "Bearer $activeKey"
  },
  body: JSON.stringify({
    model: "gemini-4-hydrogen",
    messages: [
      { role: "user", content: "Build a responsive CSS grid layout" }
    ],
    stream: false
  })
});

const data = await response.json();
console.log(data.choices[0].message.content);
    """.trimIndent()

    val pythonRequestsCode = """
import requests

url = "http://127.0.0.1:8080/v1/chat/completions"
headers = {
    "Authorization": "Bearer $activeKey",
    "Content-Type": "application/json"
}
payload = {
    "model": "gemini-4-hydrogen",
    "messages": [{"role": "user", "content": "Hello from Termux on Android!"}],
    "stream": False
}

r = requests.post(url, json=payload, headers=headers)
print(r.json()["choices"][0]["message"]["content"])
    """.trimIndent()

    val (currentCode, currentLang) = when (selectedTab) {
        0 -> curlCode to "cURL"
        1 -> pythonOpenAiCode to "Python (OpenAI SDK)"
        2 -> jsCode to "JavaScript"
        else -> pythonRequestsCode to "Python (Requests)"
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Endpoint Documentation & Snippets",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Connect scripts, Termux, and apps to Titan IDE",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = { onCopyCode(currentCode, currentLang) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Snippet",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Snippet language tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { onTabSelected(index) },
                        text = {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Code snippet container
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                SelectionContainer {
                    Text(
                        text = currentCode,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiScaffolderCard(
    onSelectPreset: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "1-Click API Scaffolder",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Scaffold production-grade single-file backend APIs",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scaffolding Option 1: FastAPI Python
            ScaffolderPresetItem(
                title = "FastAPI Production Server (Python)",
                subtitle = "Complete with CORS, Pydantic models, CRUD routes, and local run instructions",
                badge = "Python 3",
                onClick = {
                    onSelectPreset(
                        """[Build Custom REST API]: Scaffold a complete, single-file production-ready FastAPI (Python 3) REST API with:
1. Full CORS middleware and Pydantic v2 data models.
2. Complete CRUD endpoints with in-memory state and validation.
3. Integration with our local Titan Gateway at 'http://127.0.0.1:8080/v1/chat/completions'.
4. Detailed Termux & Python run instructions (uvicorn main:app --reload --port 8000). Zero placeholders."""
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Scaffolding Option 2: Express Node.js
            ScaffolderPresetItem(
                title = "Express.js REST API (Node.js)",
                subtitle = "Complete with express.json(), CORS, modular route handlers, and error middleware",
                badge = "Node.js",
                onClick = {
                    onSelectPreset(
                        """[Build Custom REST API]: Scaffold a complete, single-file production-ready Express.js (Node.js) REST API with:
1. Express JSON body parser, CORS, and request logging.
2. RESTful resource endpoints (GET, POST, PUT, DELETE) with status codes.
3. OpenAI SDK integration pointing to Titan Gateway at 'http://127.0.0.1:8080/v1'.
4. Local run instructions (node server.js). Zero placeholders."""
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Scaffolding Option 3: Flask Microservice
            ScaffolderPresetItem(
                title = "Flask Microservice & Webhooks (Python)",
                subtitle = "Lightweight Python microservice with request parsing, JWT auth, and webhook handler",
                badge = "Flask",
                onClick = {
                    onSelectPreset(
                        """[Build Custom REST API]: Scaffold a production-ready Flask Python microservice with JSON request handlers, error handlers, and webhook dispatcher connected to Titan IDE Gateway. Zero placeholders."""
                    )
                }
            )
        }
    }
}

@Composable
private fun ScaffolderPresetItem(
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
