package com.example.ui.components


import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.ArrowBack
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
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
import com.example.data.model.SupportedLanguage
import com.example.data.model.VoicePersona
import com.example.data.model.VoicePersonas
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicePersonaSheet(
    currentPersona: VoicePersona,
    currentSpeed: Float,
    currentLanguage: SupportedLanguage,
    onPersonaSelected: (VoicePersona) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onLanguageSelected: (SupportedLanguage) -> Unit,
    onTestVoiceClick: ((VoicePersona) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedGenderFilter by remember { mutableStateOf("All") } // "All", "Male", "Female"

    // Local TTS Engine for Instant Voice Testing inside sheet
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var playingPersonaId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Initialized successfully
            }
        }
        ttsEngine = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    val filteredPersonas = remember(searchQuery, selectedGenderFilter) {
        VoicePersonas.ALL_PERSONAS.filter { persona ->
            val matchesGender = when (selectedGenderFilter) {
                "Male" -> persona.gender.equals("Male", ignoreCase = true)
                "Female" -> persona.gender.equals("Female", ignoreCase = true)
                else -> true
            }
            val matchesQuery = searchQuery.isBlank() ||
                    persona.name.contains(searchQuery, ignoreCase = true) ||
                    persona.characterTag.contains(searchQuery, ignoreCase = true) ||
                    persona.description.contains(searchQuery, ignoreCase = true)
            matchesGender && matchesQuery
        }
    }

    fun playTestVoice(persona: VoicePersona) {
        playingPersonaId = persona.id
        if (onTestVoiceClick != null) {
            onTestVoiceClick(persona)
        } else {
            com.titan.ide.voice.TitanInstantTtsEngine.speakWithPersona(
                context = context,
                rawText = persona.sampleText,
                voiceId = persona.id,
                onDone = { if (playingPersonaId == persona.id) playingPersonaId = null }
            )
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
                            .background(GeminiCyanAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = GeminiCyanAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Voice Library",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeminiTextPrimary
                        )
                        Text(
                            text = "${currentPersona.name} • ${currentPersona.characterTag}",
                            fontSize = 11.sp,
                            color = GeminiCyanAccent
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


            Spacer(modifier = Modifier.height(12.dp))

            // Speech Rate Multiplier Slider
            Surface(
                color = GeminiSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GeminiThinkingBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = GeminiCyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Speech Speed",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = GeminiTextPrimary
                            )
                        }

                        Surface(
                            color = GeminiCyanAccent,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = String.format("%.1fx", currentSpeed),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = GeminiDarkObsidian,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Slider(
                        value = currentSpeed,
                        onValueChange = onSpeedChanged,
                        valueRange = 0.8f..1.8f,
                        steps = 9,
                        colors = SliderDefaults.colors(
                            thumbColor = GeminiCyanAccent,
                            activeTrackColor = GeminiCyanAccent,
                            inactiveTrackColor = GeminiThinkingBorder
                        ),
                        modifier = Modifier.height(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search & Filter Row
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search voices...", fontSize = 13.sp, color = GeminiTextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GeminiTextSecondary, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = GeminiTextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GeminiCyanAccent,
                    unfocusedBorderColor = GeminiThinkingBorder,
                    focusedContainerColor = GeminiSurface,
                    unfocusedContainerColor = GeminiSurface,
                    focusedTextColor = GeminiTextPrimary,
                    unfocusedTextColor = GeminiTextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Gender Filter Chips (All 30, Male 15, Female 15)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "All" to "All 30",
                    "Male" to "Male 15",
                    "Female" to "Female 15"
                ).forEach { (filterKey, labelText) ->
                    val isFilterSelected = selectedGenderFilter == filterKey
                    FilterChip(
                        selected = isFilterSelected,
                        onClick = { selectedGenderFilter = filterKey },
                        label = { Text(labelText, fontSize = 12.sp, fontWeight = if (isFilterSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (filterKey == "Female") GeminiSparklePurple else GeminiCyanAccent,
                            selectedLabelColor = if (filterKey == "Female") Color.White else GeminiDarkObsidian,
                            containerColor = GeminiSurface,
                            labelColor = GeminiTextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 30 Studio Character Cards List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredPersonas, key = { it.id }) { persona ->
                    val isSelected = persona.id == currentPersona.id
                    val isFemale = persona.gender.equals("Female", ignoreCase = true)
                    val accentColor = if (isFemale) GeminiSparklePurple else GeminiCyanAccent

                    Card(
                        onClick = { onPersonaSelected(persona) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) GeminiSurfaceVariant else GeminiSurface
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 0.5.dp,
                            color = if (isSelected) accentColor else GeminiThinkingBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar Badge
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = persona.name.take(1),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = accentColor
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Name, Mood Tag, Gender, Description
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = persona.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = GeminiTextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = accentColor.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = persona.gender,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = persona.characterTag,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accentColor,
                                    maxLines = 1
                                )

                                
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Test Voice Output Button
                            OutlinedButton(
                                onClick = { playTestVoice(persona) },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Test Voice Output",
                                    tint = accentColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Test", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentColor)
                            }

                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
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
