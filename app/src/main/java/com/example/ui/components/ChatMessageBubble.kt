package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ChatMessage
import com.example.data.model.SenderRole
import com.example.ui.theme.*
import com.example.util.CodeBlockParser
import com.example.util.MessagePart

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onSpeakClick: (String) -> Unit,
    onLiveRunClick: (String) -> Unit = {},
    onOpenCodeEditor: (html: String, css: String, js: String) -> Unit = { _, _, _ -> },
    onSendMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isGeneratingInlineVideo by remember(message.id) { mutableStateOf(false) }
    var inlineVideoProgress by remember(message.id) { mutableStateOf("") }
    var inlineVideoPath by remember(message.id) { mutableStateOf<String?>(null) }
    val isUser = message.sender == SenderRole.USER

    // Parse message into text segments and code cards for Assistant
    val parsedParts = remember(message.content, message.isStreaming) {
        if (!isUser && !message.isError && !message.isStreaming && message.content != "Thinking...") {
            CodeBlockParser.parseMessage(message.content)
        } else {
            listOf(MessagePart.Text(message.content))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            // Gemini Header with Sparkle Icon and Model badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "Titan IDE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = message.modelUsed,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Google Search Grounding Badge
                if (message.isSearchGrounded) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFF4285F4).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, Color(0xFF4285F4))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TravelExplore,
                                contentDescription = null,
                                tint = Color(0xFF4285F4),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Grounded",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4285F4)
                            )
                        }
                    }
                }
            }
        }

        // Thinking Steps Accordion (if present)
        message.thinkingProcess?.let { thinking ->
            if (thinking.isNotBlank()) {
                ThinkingAccordion(
                    thinkingContent = thinking,
                    isStreaming = message.isStreaming,
                    modifier = Modifier
                        .widthIn(max = 350.dp)
                        .padding(bottom = 8.dp)
                )
            }
        }

        // Attached Thumbnail Preview in message
        if (message.attachedImageUri != null || message.attachedFileName != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .widthIn(max = 260.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.attachedImageUri != null) {
                        AsyncImage(
                            model = com.example.util.ImageUtils.resolveCoilModel(message.attachedImageUri),
                            contentDescription = "Attached image",
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else if (message.attachedFileName != null) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = message.attachedFileName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Message Content Bubble
        Surface(
            shape = if (isUser) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else if (message.isError) Color(0xFFFEE2E2) else MaterialTheme.colorScheme.surface,
            border = if (message.isError) BorderStroke(1.dp, Color(0xFFEF4444))
                     else if (isUser) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                     else BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline),
            tonalElevation = if (isUser) 0.dp else 2.dp,
            modifier = Modifier.widthIn(max = 350.dp)
        ) {
            SelectionContainer {
                Column(modifier = Modifier.padding(14.dp)) {
                if (message.isError) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Notice",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF5350)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Render Thinking placeholder with live colorful animated sparkle while initial tokens arrive
                if (message.isStreaming && (message.content.isBlank() || message.content == "Thinking...")) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        AnimatedThinkingSpark(isStreaming = true)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Thinking",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ThinkingWaveDots()
                    }
                } else {
                    // Render Parsed Parts (Text & Smart Code Cards) with live blinking cursor
                    parsedParts.forEachIndexed { index, part ->
                        val isLastPart = index == parsedParts.size - 1
                        when (part) {
                            is MessagePart.Text -> {
                                if (part.content.isNotBlank()) {
                                    val (cleanText, embeddedImages) = remember(part.content) {
                                        extractEmbeddedImages(part.content)
                                    }

                                    // 1. Direct Visual In-App Image Rendering (Never show raw links)
                                    if (embeddedImages.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            embeddedImages.forEach { imageItem ->
                                                Card(
                                                    shape = RoundedCornerShape(14.dp),
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                    border = BorderStroke(1.dp, GeminiCyanAccent.copy(alpha = 0.4f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(200.dp)
                                                        ) {
                                                            AsyncImage(
                                                                model = coil.request.ImageRequest.Builder(context)
                                                                    .data(com.example.util.ImageUtils.resolveCoilModel(imageItem.url))
                                                                    .crossfade(true)
                                                                    .build(),
                                                                contentDescription = imageItem.alt,
                                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )

                                                            // Image Badge Overlay
                                                            Surface(
                                                                color = Color.Black.copy(alpha = 0.65f),
                                                                shape = RoundedCornerShape(bottomEnd = 8.dp),
                                                                modifier = Modifier.align(Alignment.TopStart)
                                                            ) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.AutoAwesome,
                                                                        contentDescription = null,
                                                                        tint = GeminiCyanAccent,
                                                                        modifier = Modifier.size(11.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text(
                                                                        text = imageItem.alt.take(24),
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = Color.White
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        // Action row below image with 1-Tap Save
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = "Google Imagen 3 Ultra HD",
                                                                fontSize = 10.5.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = GeminiCyanAccent
                                                            )

                                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                FilledTonalButton(
                                                                    onClick = {
                                                                        try {
                                                                            val request = android.app.DownloadManager.Request(android.net.Uri.parse(imageItem.url)).apply {
                                                                                setTitle("Google Imagen 3 Image")
                                                                                setDescription("Downloading Ultra HD visual asset...")
                                                                                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                                                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "Titan_Imagen3_${System.currentTimeMillis()}.png")
                                                                            }
                                                                            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                                                            manager.enqueue(request)
                                                                            android.widget.Toast.makeText(context, "Downloading to Phone Downloads folder...", android.widget.Toast.LENGTH_SHORT).show()
                                                                        } catch (e: Exception) {
                                                                            android.widget.Toast.makeText(context, "Download failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                                                        }
                                                                    },
                                                                    shape = RoundedCornerShape(8.dp),
                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                    modifier = Modifier.height(26.dp)
                                                                ) {
                                                                    Icon(Icons.Default.Download, contentDescription = "Save", modifier = Modifier.size(12.dp))
                                                                    Spacer(modifier = Modifier.width(3.dp))
                                                                    Text("1-Tap Save", fontSize = 10.sp)
                                                                }

                                                                FilledTonalButton(
                                                                    onClick = {
                                                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                                            type = "text/plain"
                                                                            putExtra(Intent.EXTRA_TEXT, imageItem.url)
                                                                        }
                                                                        context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                                                                    },
                                                                    shape = RoundedCornerShape(8.dp),
                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                    modifier = Modifier.height(26.dp)
                                                                ) {
                                                                    Text("Share", fontSize = 10.sp)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 2. Render Text Content (Cleaned of raw markdown image URLs)
                                    val bubbleTextColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    if (cleanText.isNotBlank()) {
                                        if (message.isStreaming && isLastPart) {
                                            Row(
                                                verticalAlignment = Alignment.Bottom,
                                                modifier = Modifier.padding(vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = cleanText,
                                                    color = bubbleTextColor,
                                                    fontSize = 14.sp,
                                                    lineHeight = 21.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                BlinkingCursor(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.align(Alignment.CenterVertically)
                                                )
                                            }
                                        } else {
                                            val formattedText = remember(cleanText) { parseMarkdownAnnotatedString(cleanText) }
                                            Text(
                                                text = formattedText,
                                                color = bubbleTextColor,
                                                fontSize = 14.sp,
                                                lineHeight = 21.sp,
                                                fontWeight = FontWeight.Normal,
                                                modifier = Modifier.padding(vertical = 2.dp)
                                            )
                                        }
                                    }

                                    // 3. Broadcast Dual Action Buttons (TitanBroadcastCard)
                                    if (!isUser && !message.isStreaming && isBroadcastScript(part.content)) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        TitanBroadcastCard(
                                            scriptContent = part.content,
                                            onSpeakClick = onSpeakClick
                                        )
                                    }

                                    // 4. Multi-Agent Dev Team War Room Card
                                    if (!isUser && !message.isStreaming && isDevTeamRequest(part.content)) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        com.titan.ide.ui.agents.TitanDevTeamWarRoomCard(
                                            projectPrompt = part.content,
                                            onLaunchProject = { html, _, _ ->
                                                onLiveRunClick(html)
                                            }
                                        )
                                    }

                                    // 5. Autonomous App Builder Engine (Antigravity Multi-Language Engine)
                                    if (!isUser && !message.isStreaming && isAutonomousBuilderRequest(part.content)) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        com.titan.ide.builder.TitanBuilderResultCard(
                                            userPrompt = part.content,
                                            onLivePreview = { url ->
                                                onLiveRunClick(url)
                                            },
                                            onOpenInSoraEditor = { h, c, j ->
                                                onOpenCodeEditor(h, c, j)
                                            },
                                            onModifyRequest = { modifyPrompt ->
                                                onSendMessage(modifyPrompt)
                                            }
                                        )
                                    }
                                }
                            }
                            is MessagePart.Code -> {
                                SmartCodeCard(
                                    language = part.language,
                                    code = part.code,
                                    isRunnableWeb = part.isRunnableWeb,
                                    onLiveRunClick = onLiveRunClick
                                )
                                if (message.isStreaming && isLastPart) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Live coding",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        BlinkingCursor(color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }

                // 1-Click Multi-File Project ZIP Export Button
                val codeBlocks = parsedParts.filterIsInstance<MessagePart.Code>()
                if (!isUser && codeBlocks.isNotEmpty() && !message.isStreaming) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        onClick = {
                            CodeBlockParser.exportProjectAsZip(context, codeBlocks)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Export as Project .ZIP (${codeBlocks.size} ${if (codeBlocks.size == 1) "file" else "files"})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Google Search Grounding Sources / Citations
                if (message.searchSources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TravelExplore,
                                contentDescription = null,
                                tint = Color(0xFF4285F4),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sources from Google Search",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4285F4)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            message.searchSources.forEach { source ->
                                Surface(
                                    onClick = {
                                        try {
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(source.url)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(browserIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = source.title,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.OpenInNew,
                                            contentDescription = "Open source link",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Message Action Buttons (Universal Copy for all messages, TTS Speak for Gemini)
                if (!message.isStreaming && message.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText(if (isUser) "User Message" else "Titan Response", message.content)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied to clipboard 📋", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Copy",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!isUser && !message.isError) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                onClick = { onSpeakClick(message.content) },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = "Speak aloud",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Speak",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Surface(
                                onClick = {
                                    Toast.makeText(context, "Saving audio...", Toast.LENGTH_SHORT).show()
                                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                        val savedName = saveChatAudioToPhone(context, message.content)
                                        withContext(Dispatchers.Main) {
                                            if (savedName != null) {
                                                Toast.makeText(context, "Saved to Music/TitanIDE/$savedName", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Could not save audio right now", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Save audio",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Audio",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Surface(
                                onClick = {
                                    if (!isGeneratingInlineVideo) {
                                        isGeneratingInlineVideo = true
                                        inlineVideoProgress = "Starting video..."
                                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                            try {
                                                val mp4File = com.titan.ide.media.video.TitanKineticVideoEngine.generateKineticTextVideo(
                                                    context = context,
                                                    rawText = message.content,
                                                    onProgress = { status ->
                                                        inlineVideoProgress = status
                                                    }
                                                )
                                                val mp4Path = mp4File.absolutePath
                                                withContext(Dispatchers.Main) {
                                                    isGeneratingInlineVideo = false
                                                    if (mp4Path.isNotBlank() && java.io.File(mp4Path).exists()) {
                                                        inlineVideoPath = mp4Path
                                                        Toast.makeText(context, "Video ready!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Video generation failed, please retry.", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    isGeneratingInlineVideo = false
                                                    Toast.makeText(context, "Video error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = "Generate Video",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isGeneratingInlineVideo) "Making..." else "Video",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    if (isGeneratingInlineVideo) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = inlineVideoProgress.ifBlank { "Generating video..." },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        inlineVideoPath?.let { vPath ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.Black,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(380.dp)
                                ) {
                                    AndroidView(
                                        factory = { ctx ->
                                            android.widget.VideoView(ctx).apply {
                                                val mc = android.widget.MediaController(ctx)
                                                mc.setAnchorView(this)
                                                setMediaController(mc)
                                                setVideoPath(vPath)
                                                setOnPreparedListener { mp ->
                                                    mp.isLooping = false
                                                    start()
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = {
                                        val saved = com.titan.ide.media.video.TitanKineticVideoEngine.saveVideoToGallery(context, vPath)
                                        if (saved != null) {
                                            Toast.makeText(context, "Saved to Movies/TitanIDE/$saved", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Could not save video", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save Video to Gallery", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
fun BlinkingCursor(
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "BlinkingCursorTransition")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CursorAlpha"
    )

    Box(
        modifier = modifier
            .width(2.5.dp)
            .height(18.dp)
            .alpha(alpha)
            .background(color, RoundedCornerShape(1.dp))
    )
}

data class ParsedImageItem(val alt: String, val url: String)

/**
 * Extracts markdown image links (![alt](url)) and direct image URLs from message text,
 * returning the cleaned text and the extracted images for direct visual rendering.
 */
private fun extractEmbeddedImages(text: String): Pair<String, List<ParsedImageItem>> {
    val images = mutableListOf<ParsedImageItem>()
    var cleanedText = text

    // 1. Markdown syntax: ![alt](url)
    val mdImageRegex = Regex("""!\[(.*?)\]\((https?://[^\s)]+)\)""")
    mdImageRegex.findAll(text).forEach { match ->
        val alt = match.groupValues[1].ifEmpty { "Visual Render" }
        val url = match.groupValues[2]
        images.add(ParsedImageItem(alt, url))
    }
    cleanedText = mdImageRegex.replace(cleanedText, "")

    // 2. Titan Gateway / direct media URLs
    val directUrlRegex = Regex("""(https?://[^\s)]+/media/(?:imagen3|veo2)[^\s)]+)""")
    directUrlRegex.findAll(cleanedText).forEach { match ->
        val url = match.groupValues[1]
        if (images.none { it.url == url }) {
            images.add(ParsedImageItem("Titan Gateway Media", url))
        }
    }
    cleanedText = directUrlRegex.replace(cleanedText, "")

    return Pair(cleanedText.trim(), images)
}

/**
 * Detects whether the text is a multi-speaker broadcast script, podcast, or cinematic scene
 */
private fun isBroadcastScript(text: String): Boolean {
    val lower = text.lowercase()
    if (lower.contains("titan broadcast") || lower.contains("[broadcast]") || lower.contains("[podcast]") || lower.contains("[screenplay]")) {
        return true
    }
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val dialogCount = lines.count { line ->
        (line.startsWith("**") && line.contains(":**")) ||
        (line.startsWith("[") && line.contains("]:")) ||
        line.startsWith("Host:", ignoreCase = true) ||
        line.startsWith("Narrator:", ignoreCase = true) ||
        line.startsWith("Guest:", ignoreCase = true) ||
        line.startsWith("Speaker", ignoreCase = true)
    }
    return dialogCount >= 2 && lines.size >= 4
}

/**
 * Detects whether the user is asking for a Multi-Agent Dev Team / Swarm collaboration
 */
private fun isDevTeamRequest(text: String): Boolean {
    val lower = text.lowercase()
    return lower.contains("dev team") || lower.contains("swarm") || lower.contains("war room") || lower.contains("architect + coder") || lower.contains("[dev team]")
}

/**
 * Detects whether the text is an Autonomous App Builder task (Antigravity Pipeline)
 */
private fun isAutonomousBuilderRequest(text: String): Boolean {
    val lower = text.lowercase()
    return lower.contains("autonomous builder") ||
           lower.contains("antigravity") ||
           lower.contains("builder engine") ||
           lower.contains("[autonomous builder") ||
           lower.contains("auto builder") ||
           lower.contains("build app") ||
           lower.contains("build website") ||
           lower.contains("create app") ||
           lower.contains("create website")
}



/**
 * Renders clean Markdown (**bold**, *italic*, `code`, bullet lists) without raw asterisks.
 */
private fun parseMarkdownAnnotatedString(raw: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = raw.lines()
        lines.forEachIndexed { lineIdx, origLine ->
            var line = origLine
            val trimmed = line.trimStart()
            if (trimmed.startsWith("### ")) {
                line = trimmed.removePrefix("### ")
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp))
                append(line.replace("**", ""))
                pop()
            } else if (trimmed.startsWith("## ")) {
                line = trimmed.removePrefix("## ")
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp))
                append(line.replace("**", ""))
                pop()
            } else {
                if (trimmed.startsWith("* ") || trimmed.startsWith("- ")) {
                    line = "• " + trimmed.substring(2)
                }
                var i = 0
                while (i < line.length) {
                    if (i + 1 < line.length && line[i] == '*' && line[i + 1] == '*') {
                        val end = line.indexOf("**", i + 2)
                        if (end > i + 2) {
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            append(line.substring(i + 2, end))
                            pop()
                            i = end + 2
                            continue
                        }
                    }
                    if (line[i] == '`') {
                        val end = line.indexOf('`', i + 1)
                        if (end > i + 1) {
                            pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22808080)))
                            append(line.substring(i + 1, end))
                            pop()
                            i = end + 1
                            continue
                        }
                    }
                    if (line[i] == '*' && (i == 0 || line[i - 1] != '*')) {
                        val end = line.indexOf('*', i + 1)
                        if (end > i + 1 && !line.substring(i + 1, end).contains('\n')) {
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                            append(line.substring(i + 1, end))
                            pop()
                            i = end + 1
                            continue
                        }
                    }
                    append(line[i])
                    i++
                }
            }
            if (lineIdx < lines.lastIndex) append("\n")
        }
    }
}

/**
 * Synthesizes an AI chat reply to MP3 audio and saves it to Music/TitanIDE/ via MediaStore.
 */
private suspend fun saveChatAudioToPhone(context: Context, rawText: String): String? {
    return try {
        val clean = rawText
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("[*_#`~]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(1800)
        if (clean.isBlank()) return null

        val lang = when {
            clean.any { it in '\u0980'..'\u09FF' } -> "bn"
            clean.any { it in '\u0900'..'\u097F' } -> "hi"
            else -> "en"
        }
        val mp3Bytes = com.titan.ide.network.TitanDirectEngine.synthesizeVoiceDirect(
            context = context,
            text = clean,
            lang = lang
        ) ?: return null
        if (mp3Bytes.isEmpty()) return null

        val fileName = "Titan_Audio_${System.currentTimeMillis()}.mp3"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_MUSIC + "/TitanIDE")
                put(android.provider.MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(mp3Bytes) }
            values.clear()
            values.put(android.provider.MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            fileName
        } else {
            val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), "TitanIDE")
            dir.mkdirs()
            val outFile = java.io.File(dir, fileName)
            outFile.writeBytes(mp3Bytes)
            fileName
        }
    } catch (_: Exception) {
        null
    }
}
