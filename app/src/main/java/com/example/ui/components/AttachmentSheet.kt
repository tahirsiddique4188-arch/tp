package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.titan.ide.network.TitanDirectEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSheet(
    onUploadFileClick: () -> Unit,
    onGalleryClick: () -> Unit,
    baseUrl: String,
    apiKey: String,
    onUpdateBaseUrl: (String) -> Unit,
    onUpdateApiKey: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val enginePrefs = remember { context.getSharedPreferences("titan_engine_keys", Context.MODE_PRIVATE) }

    var geminiPsid by remember { mutableStateOf(com.titan.ide.network.TitanPsidSettingsManager.getGeminiPsid(context)) }
    var geminiPsidts by remember { mutableStateOf(com.titan.ide.network.TitanPsidSettingsManager.getGeminiPsidts(context)) }
    var geminiPsidcc by remember { mutableStateOf(com.titan.ide.network.TitanPsidSettingsManager.getGeminiPsidcc(context)) }
    var flowPsid by remember { mutableStateOf(com.titan.ide.network.TitanPsidSettingsManager.getFlowPsid(context)) }
    var flowPsidts by remember { mutableStateOf(com.titan.ide.network.TitanPsidSettingsManager.getFlowPsidts(context)) }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showGoogleLoginWebView by remember { mutableStateOf(false) }

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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp)
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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Cookie Vault",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
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

                // Action Grid: Upload File & Gallery
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Upload File Card
                    Surface(
                        onClick = {
                            onUploadFileClick()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline),
                        tonalElevation = 1.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Upload File",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Gallery Card
                    Surface(
                        onClick = {
                            onGalleryClick()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline),
                        tonalElevation = 1.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF8B5CF6).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Gallery",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Cookie Vault Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Engine Vault",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "ISOLATED",
                                    color = Color(0xFF10B981),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Master Paste & Auto-Detect Cookies Button
                        Surface(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
                                if (clipText.isNotBlank()) {
                                    val parsed = TitanDirectEngine.parseCookieHeader(clipText)
                                    var count = 0
                                    parsed["__Secure-1PSID"]?.let { geminiPsid = it; count++ }
                                    parsed["__Secure-1PSIDTS"]?.let { geminiPsidts = it; count++ }
                                    parsed["__Secure-1PSIDCC"]?.let { geminiPsidcc = it; count++ }
                                    parsed["flow_psid"]?.let { flowPsid = it; count++ }
                                    parsed["flow_psidts"]?.let { flowPsidts = it; count++ }

                                    if (count == 0 && clipText.length > 25) {
                                        geminiPsid = clipText
                                        count++
                                    }
                                    if (count > 0) {
                                        Toast.makeText(context, "✅ Extracted $count credentials from clipboard!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Pasted into active cookie field!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Paste Cookies",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 1-Tap In-App Google Login Auto-Cookie Harvester
                        Surface(
                            onClick = { showGoogleLoginWebView = true },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Auto Login",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "1-Tap Sign in to Gemini (Auto-Capture PSID)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 1. Google Gemini Pool
                        Text("Gemini Session (100% PSID)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = geminiPsid,
                            onValueChange = { input ->
                                if (input.contains("__Secure-1PSIDTS=") || input.contains(";")) {
                                    val parsed = TitanDirectEngine.parseCookieHeader(input)
                                    parsed["__Secure-1PSID"]?.let { geminiPsid = it }
                                    parsed["__Secure-1PSIDTS"]?.let { geminiPsidts = it }
                                    parsed["__Secure-1PSIDCC"]?.let { geminiPsidcc = it }
                                } else {
                                    geminiPsid = input
                                }
                            },
                            singleLine = true,
                            label = { Text("__Secure-1PSID", fontSize = 11.sp) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
                                    if (text.isNotBlank()) {
                                        if (text.contains(";")) {
                                            val parsed = TitanDirectEngine.parseCookieHeader(text)
                                            parsed["__Secure-1PSID"]?.let { geminiPsid = it }
                                            parsed["__Secure-1PSIDTS"]?.let { geminiPsidts = it }
                                            parsed["__Secure-1PSIDCC"]?.let { geminiPsidcc = it }
                                        } else {
                                            geminiPsid = text
                                        }
                                        Toast.makeText(context, "Pasted __Secure-1PSID! 📋", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = geminiPsidts,
                            onValueChange = { geminiPsidts = it },
                            singleLine = true,
                            label = { Text("__Secure-1PSIDTS", fontSize = 11.sp) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
                                    if (text.isNotBlank()) {
                                        geminiPsidts = text
                                        Toast.makeText(context, "Pasted __Secure-1PSIDTS! 📋", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = geminiPsidcc,
                            onValueChange = { geminiPsidcc = it },
                            singleLine = true,
                            label = { Text("__Secure-1PSIDCC (Optional)", fontSize = 11.sp) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
                                    if (text.isNotBlank()) {
                                        geminiPsidcc = text
                                        Toast.makeText(context, "Pasted __Secure-1PSIDCC! 📋", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Spacer(modifier = Modifier.height(14.dp))

                        // Google Flow Cinema Pool
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Google Flow Cinema Session", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                            TextButton(
                                onClick = {
                                    flowPsid = geminiPsid
                                    flowPsidts = geminiPsidts
                                    Toast.makeText(context, "Copied from Gemini Session!", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("⚡ Copy from Gemini", fontSize = 11.sp, color = Color(0xFF8B5CF6), fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = flowPsid,
                            onValueChange = { flowPsid = it },
                            singleLine = true,
                            label = { Text("flow_psid (__Secure-1PSID)", fontSize = 11.sp) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
                                    if (text.isNotBlank()) {
                                        flowPsid = text
                                        Toast.makeText(context, "Pasted Flow Cookie! 📋", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = Color(0xFF8B5CF6), modifier = Modifier.size(18.dp))
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = flowPsidts,
                            onValueChange = { flowPsidts = it },
                            singleLine = true,
                            label = { Text("flow_psidts (__Secure-1PSIDTS)", fontSize = 11.sp) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
                                    if (text.isNotBlank()) {
                                        flowPsidts = text
                                        Toast.makeText(context, "Pasted flow_psidts! 📋", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = Color(0xFF8B5CF6), modifier = Modifier.size(18.dp))
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Save & Clear Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    com.titan.ide.network.TitanPsidSettingsManager.clearAll(context)
                                    geminiPsid = ""
                                    geminiPsidts = ""
                                    geminiPsidcc = ""
                                    flowPsid = ""
                                    flowPsidts = ""
                                    statusMessage = "Cleared credentials."
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear", fontSize = 12.5.sp)
                            }

                            Button(
                                onClick = {
                                    TitanDirectEngine.saveEngineCookies("gemini", geminiPsid, geminiPsidts, geminiPsidcc, context)
                                    TitanDirectEngine.saveEngineCookies("flow", flowPsid, flowPsidts, "", context)

                                    statusMessage = "✅ Credentials saved securely on-device!"
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        statusMessage?.let { status ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = status,
                                    fontSize = 12.sp,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

        if (showGoogleLoginWebView) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showGoogleLoginWebView = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Sign in to Gemini — Auto PSID Sync", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            TextButton(onClick = {
                                val raw = android.webkit.CookieManager.getInstance().getCookie("https://gemini.google.com").orEmpty()
                                val parsed = TitanDirectEngine.parseCookieHeader(raw)
                                val p = parsed["__Secure-1PSID"].orEmpty()
                                val ts = parsed["__Secure-1PSIDTS"].orEmpty()
                                val cc = parsed["__Secure-1PSIDCC"].orEmpty()
                                if (p.isNotBlank()) {
                                    geminiPsid = p
                                    if (ts.isNotBlank()) geminiPsidts = ts
                                    if (cc.isNotBlank()) geminiPsidcc = cc
                                    TitanDirectEngine.saveEngineCookies("gemini", geminiPsid, geminiPsidts, geminiPsidcc, context)
                                    statusMessage = "✅ PSID Auto-Captured from Google Login!"
                                    Toast.makeText(context, "✅ PSID Auto-Captured!", Toast.LENGTH_SHORT).show()
                                }
                                showGoogleLoginWebView = false
                            }) {
                                Text("Done & Sync", fontWeight = FontWeight.Bold)
                            }
                        }
                        androidx.compose.ui.viewinterop.AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                android.webkit.WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    val cm = android.webkit.CookieManager.getInstance()
                                    cm.setAcceptCookie(true)
                                    cm.setAcceptThirdPartyCookies(this, true)
                                    webViewClient = object : android.webkit.WebViewClient() {
                                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            if (url != null && url.contains("gemini.google.com/app")) {
                                                val raw = cm.getCookie("https://gemini.google.com").orEmpty()
                                                val parsed = TitanDirectEngine.parseCookieHeader(raw)
                                                val p = parsed["__Secure-1PSID"].orEmpty()
                                                val ts = parsed["__Secure-1PSIDTS"].orEmpty()
                                                val cc = parsed["__Secure-1PSIDCC"].orEmpty()
                                                if (p.isNotBlank() && ts.isNotBlank()) {
                                                    geminiPsid = p
                                                    geminiPsidts = ts
                                                    if (cc.isNotBlank()) geminiPsidcc = cc
                                                    TitanDirectEngine.saveEngineCookies("gemini", geminiPsid, geminiPsidts, geminiPsidcc, ctx)
                                                    statusMessage = "✅ PSID Auto-Captured from Google Login!"
                                                    Toast.makeText(ctx, "✅ PSID Auto-Captured!", Toast.LENGTH_SHORT).show()
                                                    showGoogleLoginWebView = false
                                                }
                                            }
                                        }
                                    }
                                    loadUrl("https://gemini.google.com/app")
                                }
                            }
                        )
                    }
                }
            }
        }
}
