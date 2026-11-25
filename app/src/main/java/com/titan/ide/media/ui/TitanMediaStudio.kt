package com.titan.ide.media.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.util.Base64
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.util.TitanSecurityShield
import com.titan.ide.media.network.TitanMediaGateway
import com.titan.ide.media.video.TitanVideoPlayerCard
import com.titan.ide.voice.TitanNativeVoiceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * TitanMediaStudio - Clean White Google AI Studio & Antigravity Creative Studio
 * Tabs: [🎨 Image (Imagen 3)], [🎬 Video (Veo 2)], [🎙️ Podcast (Broadcast AI)], [✨ Flow Web]
 *
 * Developer: Developer Tahir (@Tahirsiddik)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitanMediaStudio(
    initialTab: Int = 0,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var promptInput by remember { mutableStateOf("") }

    // Colors
    val googleBlue = Color(0xFF1A73E8)
    val textPrimary = Color(0xFF1F1F1F)
    val textSecondary = Color(0xFF5F6368)
    val cardBorder = Color(0xFFE0E0E0)
    val surfaceBubble = Color(0xFFF1F3F4)
    val emeraldGreen = Color(0xFF10B981)

    // Image Studio State
    var isGeneratingImage by remember { mutableStateOf(false) }
    var lastImagePrompt by remember { mutableStateOf("") }
    var generatedImageUrl by remember { mutableStateOf<String?>(null) }
    var showFullscreenImageDialog by remember { mutableStateOf(false) }
    var imageAspectRatio by remember { mutableStateOf("9:16") }

    // Video Studio State
    var lastVideoPrompt by remember { mutableStateOf("") }
    var activeVideoTopic by remember { mutableStateOf("Futuristic Flying Car in Cyberpunk City") }

    // Podcast Studio State
    var isGeneratingPodcast by remember { mutableStateOf(false) }
    var lastPodcastTopic by remember { mutableStateOf("") }
    var podcastScript by remember { mutableStateOf<String?>(null) }
    var isPlayingPodcastAudio by remember { mutableStateOf(false) }
    val allPersonas = remember { com.example.data.model.VoicePersonas.ALL_PERSONAS }
    var host1Persona by remember {
        mutableStateOf(allPersonas.firstOrNull { it.gender.equals("Male", true) } ?: allPersonas.first())
    }
    var host2Persona by remember {
        mutableStateOf(allPersonas.firstOrNull { it.gender.equals("Female", true) } ?: allPersonas.last())
    }
    var showHost1Dropdown by remember { mutableStateOf(false) }
    var showHost2Dropdown by remember { mutableStateOf(false) }
    var activePodcastSpeaker by remember { mutableStateOf("") }

    // Save Image to Phone Gallery
    fun saveImageToGallery(imageUrl: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = if (imageUrl.startsWith("data:image")) {
                    val b64 = imageUrl.substringAfter("base64,", "")
                    Base64.decode(b64, Base64.DEFAULT)
                } else {
                    val req = Request.Builder().url(imageUrl).get().build()
                    TitanSecurityShield.buildSecureOkHttpClient(60).newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                        resp.body?.bytes() ?: throw IllegalStateException("Empty image bytes")
                    }
                }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IllegalStateException("Unable to decode image")

                val filename = "Titan_Imagen3_${System.currentTimeMillis()}.jpg"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = context.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        contentValues
                    )
                    uri?.let {
                        context.contentResolver.openOutputStream(it)?.use { os ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os)
                        }
                    }
                } else {
                    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!imagesDir.exists()) imagesDir.mkdirs()
                    val imageFile = File(imagesDir, filename)
                    FileOutputStream(imageFile).use { os ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "📥 Saved image to Phone Downloads!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Save error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Voice Dictation
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                promptInput = spokenText
            }
        }
    }

    fun triggerVoiceInput() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your creative prompt...")
            }
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Voice input: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleSendAction() {
        val query = promptInput.trim()
        if (query.isBlank()) return
        promptInput = ""

        when (selectedTab) {
            0 -> {
                // Generate Image
                lastImagePrompt = query
                isGeneratingImage = true
                scope.launch(Dispatchers.IO) {
                    try {
                        val url = TitanMediaGateway.generateImagen3Artwork(
                            context = context,
                            prompt = query,
                            aspectRatio = imageAspectRatio,
                            stylePreset = "Photorealistic"
                        )
                        withContext(Dispatchers.Main) {
                            isGeneratingImage = false
                            if (url.isNotBlank()) {
                                generatedImageUrl = url
                                Toast.makeText(context, "🎨 Ultra HD Artwork Synthesized!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Generation error. Please retry.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            isGeneratingImage = false
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            1 -> {
                // Generate Video
                lastVideoPrompt = query
                activeVideoTopic = query
            }
            2 -> {

                // Generate Podcast
                lastPodcastTopic = query
                isGeneratingPodcast = true
                scope.launch(Dispatchers.IO) {
                    try {
                        val script = TitanMediaGateway.generateBroadcastPodcast(context, query, host1Persona.name, host2Persona.name)
                        withContext(Dispatchers.Main) {
                            isGeneratingPodcast = false
                            if (script.isNotBlank()) {
                                podcastScript = script
                                Toast.makeText(context, "🎙️ Dual-Host Podcast Episode Ready!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Podcast generation error.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            isGeneratingPodcast = false
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFFFFFF))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 1. Top Navigation Bar
            Surface(
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFF1F3F4)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(surfaceBubble)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = textPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Titan Creative Studio",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFFE8F0FE),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "AI Media",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = googleBlue,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Imagen 3 • Veo 2 • AI Podcast",
                                fontSize = 11.5.sp,
                                color = textSecondary
                            )
                        }
                    }
                }
            }

            // 2. Segmented Pill Tab Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFFFFF))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf(
                    "Image",
                    "Video",
                    "Podcast"
                )
                tabs.forEachIndexed { index, tabTitle ->
                    val isSelected = selectedTab == index
                    Surface(
                        onClick = {
                            selectedTab = index
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) Color(0xFFE8F0FE) else surfaceBubble,
                        border = BorderStroke(1.dp, if (isSelected) googleBlue else Color.Transparent)
                    ) {
                        Text(
                            text = tabTitle,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) googleBlue else textPrimary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // 3. Main Chat/Creation Feed Area (Scrollable)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // TAB 0: IMAGE STUDIO (IMAGEN 3)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = imageAspectRatio == "9:16",
                                    onClick = { imageAspectRatio = "9:16" },
                                    label = { Text("Portrait (9:16)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = googleBlue,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = imageAspectRatio == "16:9",
                                    onClick = { imageAspectRatio = "16:9" },
                                    label = { Text("Landscape (16:9)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = googleBlue,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }

                            if (lastImagePrompt.isBlank() && !isGeneratingImage && generatedImageUrl == null) {
                                CreativeWelcomeCard(
                                    icon = Icons.Default.Image,
                                    title = "Imagen 3 Studio",
                                    subtitle = "Enter prompt to generate",
                                    suggestions = listOf(
                                        "Futuristic flying car over cyberpunk city, 8k",
                                        "Mountain sunrise with crystal river and pine trees",
                                        "Cute 3D Pixar baby robot reading glowing book",
                                        "Hyper-realistic portrait of a royal Bengal tiger"
                                    ),
                                    onSelectSuggestion = { suggestion ->
                                        promptInput = suggestion
                                        handleSendAction()
                                    }
                                )
                            } else {
                                // User Prompt Bubble
                                if (lastImagePrompt.isNotBlank()) {
                                    UserPromptBubble(text = lastImagePrompt)
                                }

                                // Generating Card
                                if (isGeneratingImage) {
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, cardBorder),
                                        modifier = Modifier.fillMaxWidth().height(220.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator(
                                                    color = googleBlue,
                                                    strokeWidth = 3.dp,
                                                    modifier = Modifier.size(38.dp)
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    text = "Synthesizing Ultra HD Artwork...",
                                                    fontWeight = FontWeight.Bold,
                                                    color = textPrimary,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = "Google Imagen 3 Direct Engine",
                                                    color = textSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                // Generated Image Result Card
                                generatedImageUrl?.let { imgUrl ->
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, cardBorder),
                                        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp))
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(280.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable { showFullscreenImageDialog = true }
                                            ) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(com.example.util.ImageUtils.resolveCoilModel(imgUrl))
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = "Imagen 3 Output",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )

                                                Surface(
                                                    color = Color.Black.copy(alpha = 0.6f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(8.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("1-Tap Zoom", fontSize = 11.sp, color = Color.White)
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "🎨 Google Imagen 3",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = emeraldGreen
                                                )

                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = {
                                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            clipboard.setPrimaryClip(ClipData.newPlainText("Prompt", lastImagePrompt))
                                                            Toast.makeText(context, "Prompt copied!", Toast.LENGTH_SHORT).show()
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = surfaceBubble),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                    ) {
                                                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = textPrimary, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Copy", fontSize = 12.sp, color = textPrimary)
                                                    }

                                                    Button(
                                                        onClick = { saveImageToGallery(imgUrl) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = emeraldGreen),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                                    ) {
                                                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Save to Gallery", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            // TAB 1: VIDEO STUDIO (VEO 2)
                            if (lastVideoPrompt.isNotBlank()) {
                                UserPromptBubble(text = lastVideoPrompt)
                            }

                            TitanVideoPlayerCard(initialTopic = activeVideoTopic)
                        }
                        2 -> {
                            // TAB 2: PODCAST STUDIO (DUAL-HOST AI)
                            // TAB 2: BROADCAST PODCAST STUDIO — 2-CHARACTER VOICE PICKER
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = surfaceBubble,
                                border = BorderStroke(1.dp, cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Select 2 Podcast Voice Characters",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            OutlinedButton(
                                                onClick = { showHost1Dropdown = true },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "Host 1: ${host1Persona.name}",
                                                    fontSize = 12.sp,
                                                    maxLines = 1
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showHost1Dropdown,
                                                onDismissRequest = { showHost1Dropdown = false }
                                            ) {
                                                allPersonas.forEach { p ->
                                                    DropdownMenuItem(
                                                        text = { Text("${p.name} (${p.gender})") },
                                                        onClick = {
                                                            host1Persona = p
                                                            showHost1Dropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            OutlinedButton(
                                                onClick = { showHost2Dropdown = true },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "Host 2: ${host2Persona.name}",
                                                    fontSize = 12.sp,
                                                    maxLines = 1
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showHost2Dropdown,
                                                onDismissRequest = { showHost2Dropdown = false }
                                            ) {
                                                allPersonas.forEach { p ->
                                                    DropdownMenuItem(
                                                        text = { Text("${p.name} (${p.gender})") },
                                                        onClick = {
                                                            host2Persona = p
                                                            showHost2Dropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (lastPodcastTopic.isBlank() && !isGeneratingPodcast && podcastScript == null) {
                                CreativeWelcomeCard(
                                    icon = Icons.Default.RecordVoiceOver,
                                    title = "Generate AI Broadcast Podcasts",
                                    subtitle = "Type any topic or debate question. Titan creates an engaging 2-speaker podcast episode with real-time neural voice playback.",
                                    suggestions = listOf(
                                        "Artificial Intelligence taking over software engineering in 2030",
                                        "Quantum Computing and the mysteries of time travel",
                                        "Building autonomous tech startups with AI agents",
                                        "The psychology of human motivation and modern happiness"
                                    ),
                                    onSelectSuggestion = { suggestion ->
                                        promptInput = suggestion
                                        handleSendAction()
                                    }
                                )
                            } else {
                                if (lastPodcastTopic.isNotBlank()) {
                                    UserPromptBubble(text = lastPodcastTopic)
                                }

                                if (isGeneratingPodcast) {
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, cardBorder),
                                        modifier = Modifier.fillMaxWidth().height(180.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator(
                                                    color = emeraldGreen,
                                                    strokeWidth = 3.dp,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    text = "Writing Dual-Host Broadcast Script...",
                                                    fontWeight = FontWeight.Bold,
                                                    color = textPrimary,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                podcastScript?.let { script ->
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, cardBorder),
                                        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp))
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = emeraldGreen, modifier = Modifier.size(20.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Podcast Episode Ready", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimary)
                                                }

                                                IconButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        clipboard.setPrimaryClip(ClipData.newPlainText("Podcast", script))
                                                        Toast.makeText(context, "Script copied!", Toast.LENGTH_SHORT).show()
                                                    }
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = textSecondary, modifier = Modifier.size(18.dp))
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Surface(
                                                color = surfaceBubble,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = script,
                                                    fontSize = 13.sp,
                                                    lineHeight = 20.sp,
                                                    color = textPrimary,
                                                    modifier = Modifier.padding(14.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(14.dp))

                                            Button(
                                                onClick = {
                                                    if (isPlayingPodcastAudio) {
                                                        isPlayingPodcastAudio = false
                                                        activePodcastSpeaker = ""
                                                        com.titan.ide.voice.TitanInstantTtsEngine.stopSpeech()
                                                        TitanNativeVoiceEngine.stopSpeech()
                                                    } else {
                                                        isPlayingPodcastAudio = true
                                                        com.titan.ide.voice.TitanInstantTtsEngine.playDualHostPodcast(
                                                            context = context,
                                                            script = script,
                                                            host1Id = host1Persona.id,
                                                            host1Name = host1Persona.name,
                                                            host2Id = host2Persona.id,
                                                            host2Name = host2Persona.name,
                                                            onSpeakerChange = { spk -> activePodcastSpeaker = spk },
                                                            onDone = {
                                                                isPlayingPodcastAudio = false
                                                                activePodcastSpeaker = ""
                                                            }
                                                        )
                                                        Toast.makeText(context, "Playing 2-Character Podcast (${host1Persona.name} & ${host2Persona.name})...", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = emeraldGreen),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth().height(46.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlayingPodcastAudio) Icons.Default.Stop else Icons.Default.VolumeUp,
                                                    contentDescription = null,
                                                    tint = Color.White
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (isPlayingPodcastAudio) "Stop (${activePodcastSpeaker.ifBlank { "Playing" }})" else "Play 2-Voice Podcast",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.5.sp,
                                                    color = Color.White
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

            // 4. Clean Bottom Input Bar (Identical to Main Chat)
            Surface(
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFF1F3F4)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Input Pill
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = surfaceBubble,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = promptInput,
                                onValueChange = { promptInput = it },
                                placeholder = {
                                    val hint = when (selectedTab) {
                                        0 -> "Describe image to generate in Ultra HD..."
                                        1 -> "Describe video scene or topic..."
                                        2 -> "Enter podcast topic or debate question..."
                                        else -> "Describe creative prompt..."
                                    }
                                    Text(
                                        text = hint,
                                        fontSize = 13.5.sp,
                                        color = textSecondary
                                    )
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = textPrimary,
                                    unfocusedTextColor = textPrimary
                                ),
                                maxLines = 4,
                                modifier = Modifier.weight(1f)
                            )

                            // Voice Dictation Mic inside Pill
                            IconButton(
                                onClick = { triggerVoiceInput() },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice Dictate",
                                    tint = googleBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Send Button with ArrowUpward
                    val isBusy = isGeneratingImage || isGeneratingPodcast
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (promptInput.isNotBlank() && !isBusy)
                                    Brush.linearGradient(listOf(googleBlue, Color(0xFF4F46E5)))
                                else
                                    Brush.linearGradient(listOf(Color(0xFFCBD5E1), Color(0xFF94A3B8)))
                            )
                            .clickable(enabled = promptInput.isNotBlank() && !isBusy) {
                                handleSendAction()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Fullscreen Image Dialog
    if (showFullscreenImageDialog && generatedImageUrl != null) {
        Dialog(
            onDismissRequest = { showFullscreenImageDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = com.example.util.ImageUtils.resolveCoilModel(generatedImageUrl),
                    contentDescription = "Fullscreen Artwork",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { generatedImageUrl?.let { saveImageToGallery(it) } },
                        modifier = Modifier.background(emeraldGreen, CircleShape)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                    }

                    IconButton(
                        onClick = { showFullscreenImageDialog = false },
                        modifier = Modifier.background(Color.White.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserPromptBubble(text: String) {
    val textPrimary = Color(0xFF1F1F1F)
    val googleBlue = Color(0xFF1A73E8)
    val surfaceBubble = Color(0xFFF1F3F4)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = surfaceBubble,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = text,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(googleBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CreativeWelcomeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    suggestions: List<String>,
    onSelectSuggestion: (String) -> Unit
) {
    val googleBlue = Color(0xFF1A73E8)
    val textPrimary = Color(0xFF1F1F1F)
    val textSecondary = Color(0xFF5F6368)
    val surfaceBubble = Color(0xFFF1F3F4)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8F0FE)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = googleBlue,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "PROMPT INSPIRATIONS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textSecondary,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        suggestions.forEach { suggestion ->
            Surface(
                onClick = { onSelectSuggestion(suggestion) },
                shape = RoundedCornerShape(14.dp),
                color = surfaceBubble,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = suggestion,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = googleBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
