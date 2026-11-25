package com.titan.ide.media.video

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.util.ImageUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.titan.ide.network.TitanNetworkConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.net.URLEncoder

@Composable
fun TitanVideoPlayerCard(
    initialTopic: String = "Futuristic AI Metropolis 2030",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var topicInput by remember { mutableStateOf(initialTopic) }
    var selectedVideoStyle by remember { mutableStateOf("character_story") } // "character_story" or "masterclass_tech"
    var selectedDuration by remember { mutableStateOf("60s") } // "60s", "120s", "300s"
    var selectedFormat by remember { mutableStateOf("9:16") }
    var selectedFrames by remember { mutableIntStateOf(8) }
    var selectedVideoModel by remember { mutableStateOf("veo-3.1") }
    var progressStatus by remember { mutableStateOf("🎬 Rendering Video...") }
    var startFrameDataUrl by remember { mutableStateOf<String?>(null) }
    var endFrameDataUrl by remember { mutableStateOf<String?>(null) }

    val startFramePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val b64 = ImageUtils.uriToBase64DataUrl(context, it, maxDimension = 720)
                withContext(Dispatchers.Main) { startFrameDataUrl = b64 }
            }
        }
    }

    val endFramePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val b64 = ImageUtils.uriToBase64DataUrl(context, it, maxDimension = 720)
                withContext(Dispatchers.Main) { endFrameDataUrl = b64 }
            }
        }
    }

    var isGenerating by remember { mutableStateOf(false) }
    var videoUrl by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playProgress by remember { mutableFloatStateOf(0f) }

    val exoPlayer = remember(videoUrl) {
        if (!videoUrl.isNullOrBlank()) {
            ExoPlayer.Builder(context).build().apply {
                val safeUrl = videoUrl.orEmpty(); val mediaUri = if (safeUrl.startsWith("/")) Uri.fromFile(File(safeUrl)) else Uri.parse(safeUrl)
                setMediaItem(MediaItem.fromUri(mediaUri))
                repeatMode = Player.REPEAT_MODE_ALL
                prepare()
                playWhenReady = true
            }
        } else null
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.release()
        }
    }

    LaunchedEffect(isPlaying) {
        exoPlayer?.playWhenReady = isPlaying
    }

    // Audio/Video Progress Ticker
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(300)
            playProgress = (playProgress + 0.05f).let { if (it > 1.0f) 0f else it }
        }
    }

    fun generateLongVideo() {
        if (topicInput.isBlank()) return
        isGenerating = true
        progressStatus = "🎬 Processing started..."
        scope.launch(Dispatchers.IO) {
            try {
                val finalUrl = com.titan.ide.media.network.TitanMediaGateway.generateVeo2Video(
                    context = context,
                    topic = topicInput.trim(),
                    duration = selectedDuration,
                    format = selectedFormat,
                    framesCount = selectedFrames,
                    startFrame = startFrameDataUrl,
                    endFrame = endFrameDataUrl,
                    videoModel = selectedVideoModel,
                    videoStyle = selectedVideoStyle,
                    onProgress = { msg ->
                        scope.launch(Dispatchers.Main) { progressStatus = msg }
                    }
                )

                withContext(Dispatchers.Main) {
                    isGenerating = false
                    if (finalUrl.isNotBlank()) {
                        videoUrl = finalUrl
                        isPlaying = true
                        Toast.makeText(context, "🎬 Video successfully generated!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "⚠️ Video synthesis failed. Check connection.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isGenerating = false
                    Toast.makeText(context, "⚠️ Video error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun downloadVideoToMovies() {
        val url = videoUrl ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val filename = "TitanVideo_${System.currentTimeMillis()}.mp4"
                if (url.startsWith("/")) {
                    val srcFile = File(url)
                    if (srcFile.exists()) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            val cv = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                            }
                            val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                                ?: throw IllegalStateException("MediaStore insert failed")
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                srcFile.inputStream().use { it.copyTo(out) }
                            }
                        } else {
                            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            if (!moviesDir.exists()) moviesDir.mkdirs()
                            val targetFile = File(moviesDir, filename)
                            srcFile.copyTo(targetFile, overwrite = true)
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "📥 Saved to Phone Downloads/$filename", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                }
                val req = Request.Builder().url(url).get().build()
                com.example.util.TitanSecurityShield.buildSecureOkHttpClient(120).newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                    val stream = resp.body?.byteStream() ?: throw IllegalStateException("Empty video stream")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val cv = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                        val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                            ?: throw IllegalStateException("MediaStore insert failed")
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            stream.copyTo(out)
                        }
                    } else {
                        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        if (!moviesDir.exists()) moviesDir.mkdirs()
                        val targetFile = File(moviesDir, filename)
                        FileOutputStream(targetFile).use { out -> stream.copyTo(out) }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "📥 Saved to Phone Downloads/$filename", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Video URL ready: $url", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun exportToYouTubeShorts() {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Google Veo 2 Shorts Story")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "🎬 *Google Veo 2 Continuous Video Studio*\n\nTopic: $topicInput\nDuration: $selectedDuration\n\nGenerated via Titan IDE by Developer Tahir (@Tahirsiddik)"
                )
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export to YouTube Shorts / Social"))
        } catch (e: Exception) {
            Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Bar
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
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(18.dp))
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text("Google Flow & Veo 2 Cinema Studio", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "FLOW VEO",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B5CF6),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Video Style & Type Selection (Character Story vs Masterclass / Infographic)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedVideoStyle == "character_story",
                    onClick = { selectedVideoStyle = "character_story" },
                    label = { Text("🎭 Character Story", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFEC4899),
                        selectedLabelColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.height(32.dp)
                )

                FilterChip(
                    selected = selectedVideoStyle == "masterclass_tech",
                    onClick = { selectedVideoStyle = "masterclass_tech" },
                    label = { Text("📊 Masterclass / Tech", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0EA5E9),
                        selectedLabelColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.height(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Video Format (Shorts vs Long) & Duration Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedFormat == "9:16",
                    onClick = { selectedFormat = "9:16" },
                    leadingIcon = {
                        Icon(Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    label = { Text("Portrait (9:16)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF8B5CF6),
                        selectedLabelColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.height(32.dp)
                )

                FilterChip(
                    selected = selectedFormat == "16:9",
                    onClick = { selectedFormat = "16:9" },
                    leadingIcon = {
                        Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    label = { Text("Landscape (16:9)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF8B5CF6),
                        selectedLabelColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.height(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Duration Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Duration:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("15s (Short)", "30s (Reel)", "60s", "120s (Long)", "300s (Cinema)").forEach { durOption ->
                    val durKey = durOption.substringBefore(" ")
                    FilterChip(
                        selected = selectedDuration == durKey,
                        onClick = { selectedDuration = durKey },
                        label = { Text(durOption, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF38BDF8),
                            selectedLabelColor = Color.Black,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Custom Frame Count Selector Row (Use any number of frames)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Frames:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(4, 8, 12, 16, 24, 36).forEach { fc ->
                    FilterChip(
                        selected = selectedFrames == fc,
                        onClick = { selectedFrames = fc },
                        label = { Text("${fc}F", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.height(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Model:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                com.example.data.model.GeminiModelsList.VIDEO_MODELS.forEach { vm ->
                    FilterChip(
                        selected = selectedVideoModel == vm.id,
                        onClick = { selectedVideoModel = vm.id },
                        label = { Text(vm.name, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF8B5CF6),
                            selectedLabelColor = Color.White,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.height(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Start & End Frame Image Selection Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { startFramePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(32.dp)
                ) {
                    Text(if (startFrameDataUrl != null) "Start Frame Set" else "+ Start Frame", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary)
                }
                OutlinedButton(
                    onClick = { endFramePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(32.dp)
                ) {
                    Text(if (endFrameDataUrl != null) "End Frame Set" else "+ End Frame", fontSize = 10.5.sp, color = Color(0xFF8B5CF6))
                }
                if (startFrameDataUrl != null || endFrameDataUrl != null) {
                    TextButton(
                        onClick = { startFrameDataUrl = null; endFrameDataUrl = null },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Clear", fontSize = 10.sp, color = Color(0xFFEF4444))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Topic Input
            OutlinedTextField(
                value = topicInput,
                onValueChange = { topicInput = it },
                placeholder = { Text("Enter video topic, script or prompt...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8B5CF6),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Generate Button
            Button(
                onClick = { generateLongVideo() },
                enabled = !isGenerating && topicInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (selectedVideoStyle == "masterclass_tech") "Building Masterclass Video..." else "Building Story Video...", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (selectedVideoStyle == "masterclass_tech") "Build Masterclass / Tech Video" else "Build Character Story Video", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            if (isGenerating) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (selectedFormat == "9:16") 240.dp else 170.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF12151C)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFFFB923C), strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(progressStatus, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            // Video Player Card Area
            videoUrl?.let { url ->
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (selectedFormat == "9:16") 280.dp else 180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (exoPlayer != null && (url.contains(".mp4") || url.contains("/media/") || url.startsWith("http"))) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                }
                            },
                            update = { view ->
                                view.player = exoPlayer
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = url,
                            contentDescription = "Continuous Video Frame",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Overlay Controls
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                    )

                    IconButton(
                        onClick = { isPlaying = !isPlaying },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.7f))
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color(0xFFFB923C),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Bottom Ticker Bar
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = topicInput.take(30),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = "$selectedDuration • Google Veo 2",
                            fontSize = 10.sp,
                            color = Color(0xFFFB923C),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Dual Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { downloadVideoToMovies() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFB923C)),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("📥 Download (.mp4)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Button(
                        onClick = { exportToYouTubeShorts() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("🚀 Export Shorts", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
