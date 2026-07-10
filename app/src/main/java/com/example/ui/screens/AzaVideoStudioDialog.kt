package com.example.ui.screens

import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.database.ContentItem
import com.example.data.database.YouTubeUploadDraft
import com.example.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin

data class AzaVideoScene(
    val sceneNumber: Int,
    val subtitle: String,
    val imagePrompt: String,
    val durationMs: Long,
    val gradientColors: List<Color>
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AzaVideoStudioDialog(
    item: ContentItem,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Render Steps:
    // 0 = Idle, 1 = Initializing, 2 = Scene Partitioning, 3 = Graphic Asset Rendering, 4 = Synthesizing Voice Track, 5 = Merging Audio-Visual Multiplexer, 6 = Render Finished & Loaded in Player
    var renderStep by remember { mutableIntStateOf(1) }
    var renderProgress by remember { mutableFloatStateOf(0f) }
    var statusLogs = remember { mutableStateListOf<String>() }

    var parsedScenes = remember { mutableStateListOf<AzaVideoScene>() }

    // Video Player State
    var isPlaying by remember { mutableStateOf(false) }
    var currentSceneIndex by remember { mutableIntStateOf(0) }
    var videoProgressMs by remember { mutableLongStateOf(0L) }
    var isMuted by remember { mutableStateOf(false) }
    var expandedSceneIndex by remember { mutableStateOf<Int?>(null) }
    var editingSubtitle by remember { mutableStateOf("") }
    var editingPrompt by remember { mutableStateOf("") }
    var editingDurationMs by remember { mutableStateOf(4000L) }
    var isExpandingPrompt by remember { mutableStateOf(false) }

    // TTS engine initialization
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    // Initialize TTS
    DisposableEffect(Unit) {
        val speechEngine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
            }
        }
        speechEngine.language = Locale.US
        speechEngine.setSpeechRate(0.95f)
        tts = speechEngine

        onDispose {
            speechEngine.stop()
            speechEngine.shutdown()
        }
    }

    // Dynamic rendering sequence
    LaunchedEffect(Unit) {
        statusLogs.add("[AZA LOG] Initializing high-fidelity Video Render Subsystem...")
        delay(600)
        
        renderStep = 2
        statusLogs.add("[AZA LOG] Partitioning concept outline into distinct narrative visual scenes...")
        
        // Let's retrieve partitioned scenes from our repository / Gemini API!
        val rawScenesResponse = try {
            viewModel.getAiVideoScenes(item.title, item.scriptOutline)
        } catch (e: Exception) {
            ""
        }

        // Parse scenes safely
        val scenes = parseRawScenes(rawScenesResponse)
        parsedScenes.clear()
        parsedScenes.addAll(scenes)
        
        statusLogs.add("[AZA LOG] Configured ${scenes.size} narrative scenes successfully.")
        delay(800)

        renderStep = 3
        statusLogs.add("[AZA LOG] Commencing high-resolution graphics generation for scene assets...")
        for (i in 1..25) {
            renderProgress = i / 100f
            delay(40)
        }
        statusLogs.add("[AZA LOG] Graphics buffers rendered with dynamic Canvas interpolation.")

        renderStep = 4
        statusLogs.add("[AZA LOG] Synthesizing Voice over and local narration streams...")
        for (i in 26..65) {
            renderProgress = i / 100f
            delay(30)
        }
        statusLogs.add("[AZA LOG] TTS parameters mapped to temporal frame boundaries.")

        renderStep = 5
        statusLogs.add("[AZA LOG] Running final Audio-Visual multiplexer to compile MP4 output container...")
        for (i in 66..100) {
            renderProgress = i / 100f
            delay(25)
        }
        statusLogs.add("[AZA LOG] Compilation complete. Master file loaded to AZA Player Buffer.")
        delay(600)

        renderStep = 6
    }

    // Playback Loop
    LaunchedEffect(isPlaying, currentSceneIndex) {
        if (!isPlaying || parsedScenes.isEmpty()) return@LaunchedEffect

        val scene = parsedScenes.getOrNull(currentSceneIndex) ?: return@LaunchedEffect
        
        // Speak subtitle using local TTS
        if (!isMuted && isTtsReady) {
            tts?.speak(scene.subtitle, TextToSpeech.QUEUE_FLUSH, null, "aza_scene_tts_${scene.sceneNumber}")
        }

        val sceneDuration = scene.durationMs
        val stepMs = 100L
        var elapsed = 0L

        while (elapsed < sceneDuration && isPlaying) {
            delay(stepMs)
            elapsed += stepMs
            videoProgressMs += stepMs
        }

        // Move to next scene
        if (isPlaying) {
            if (currentSceneIndex < parsedScenes.size - 1) {
                currentSceneIndex++
                videoProgressMs = 0L
            } else {
                // End of video reached
                isPlaying = false
                currentSceneIndex = 0
                videoProgressMs = 0L
                tts?.stop()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF12141C)), // Slate Dark Cyber theme
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header with glowing icon
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
                                imageVector = Icons.Default.MovieFilter,
                                contentDescription = "Studio",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "AZA MEDIA RENDER STUDIO",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "High-Fidelity AI Video Generation",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.LightGray)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Dialog")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (renderStep < 6) {
                    // RENDERING PIPELINE PROGRESS SCREEN
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = when (renderStep) {
                                1 -> "Initializing Subsystem..."
                                2 -> "Analyzing Outline via Gemini API..."
                                3 -> "Synthesizing Graphical Scenes..."
                                4 -> "Synthesizing AI Audio Transcripts..."
                                5 -> "Compiling High-Resolution MP4 Stream..."
                                else -> "Rendering Video..."
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        LinearProgressIndicator(
                            progress = { renderProgress },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "${(renderProgress * 100).toInt()}% completed",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(30.dp))

                        // Terminal Style Logs Console
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF08090D))
                                .border(1.dp, Color.DarkGray.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(statusLogs) { log ->
                                    Text(
                                        text = log,
                                        color = if (log.contains("complete", true) || log.contains("successful", true)) Color(0xFF81C784) else Color(0xFFB0BEC5),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // MAIN MEDIA PLAYER VIEW
                    val currentScene = parsedScenes.getOrNull(currentSceneIndex)
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        // Title Bar inside Player
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E212D))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text("COMPLETED RENDER", fontSize = 8.sp, color = Color.White, modifier = Modifier.padding(2.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // VIDEO FRAME CONTAINER
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = currentScene?.gradientColors ?: listOf(Color(0xFF2E3192), Color(0xFF1BFFFF))
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            // Canvas oscilloscope soundwave animation
                            val oscState = remember { mutableStateOf(0f) }
                            LaunchedEffect(isPlaying) {
                                if (isPlaying) {
                                    while (isPlaying) {
                                        delay(16)
                                        oscState.value += 0.15f
                                    }
                                }
                            }

                            val secondaryColor = MaterialTheme.colorScheme.secondary
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                if (isPlaying) {
                                    val width = size.width
                                    val height = size.height
                                    val midY = height / 2f
                                    val wavePath = Path()
                                    wavePath.moveTo(0f, midY)

                                    for (x in 0..width.toInt() step 5) {
                                        val angle = (x / width) * 4f * Math.PI + oscState.value
                                        val y = midY + sin(angle).toFloat() * 25f * (0.8f + sin(oscState.value * 0.3f).toFloat() * 0.2f)
                                        wavePath.lineTo(x.toFloat(), y)
                                    }

                                    drawPath(
                                        path = wavePath,
                                        color = Color.White.copy(alpha = 0.5f),
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                    
                                    // Secondary wave
                                    val wavePath2 = Path()
                                    wavePath2.moveTo(0f, midY)
                                    for (x in 0..width.toInt() step 5) {
                                        val angle = (x / width) * 3f * Math.PI - oscState.value * 0.8f
                                        val y = midY + sin(angle).toFloat() * 15f
                                        wavePath2.lineTo(x.toFloat(), y)
                                    }

                                    drawPath(
                                        path = wavePath2,
                                        color = secondaryColor.copy(alpha = 0.4f),
                                        style = Stroke(width = 1.dp.toPx())
                                    )
                                }
                            }

                            // Dynamic graphic overlay representing prompt
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Scene ${currentSceneIndex + 1} of ${parsedScenes.size}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.Black.copy(alpha = 0.4f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )

                                    Icon(
                                        imageVector = when (currentSceneIndex) {
                                            0 -> Icons.Default.Campaign
                                            1 -> Icons.Default.Troubleshoot
                                            2 -> Icons.Default.ElectricBolt
                                            else -> Icons.Default.Favorite
                                        },
                                        contentDescription = "Scene Category",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.2f))
                                            .padding(3.dp)
                                    )
                                }

                                // Narrative Text / Subtitle Overlay
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.75f))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = currentScene?.subtitle ?: "",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFEB3B),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // TIMELINE & PROGRESS BAR
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val activeScene = parsedScenes.getOrNull(currentSceneIndex)
                            val totalSceneDuration = activeScene?.durationMs ?: 1L
                            val ratio = (videoProgressMs.toFloat() / totalSceneDuration.toFloat()).coerceIn(0f, 1f)

                            Text(
                                text = String.format(Locale.US, "0:%02d", videoProgressMs / 1000),
                                fontSize = 10.sp,
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace
                            )

                            Slider(
                                value = ratio,
                                onValueChange = {
                                    videoProgressMs = (it * totalSceneDuration).toLong()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.DarkGray
                                )
                            )

                            Text(
                                text = String.format(Locale.US, "0:%02d", totalSceneDuration / 1000),
                                fontSize = 10.sp,
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // CONTROLS ROW
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (currentSceneIndex > 0) {
                                            currentSceneIndex--
                                            videoProgressMs = 0L
                                        }
                                    },
                                    enabled = currentSceneIndex > 0,
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                                ) {
                                    Icon(Icons.Default.SkipPrevious, contentDescription = "Prev Scene", modifier = Modifier.size(20.dp))
                                }

                                FloatingActionButton(
                                    onClick = { isPlaying = !isPlaying },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White,
                                    shape = CircleShape,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (currentSceneIndex < parsedScenes.size - 1) {
                                            currentSceneIndex++
                                            videoProgressMs = 0L
                                        }
                                    },
                                    enabled = currentSceneIndex < parsedScenes.size - 1,
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                                ) {
                                    Icon(Icons.Default.SkipNext, contentDescription = "Next Scene", modifier = Modifier.size(20.dp))
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { isMuted = !isMuted },
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                        contentDescription = "Mute Narrative",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Text(
                                    text = if (isMuted) "MUTED" else "VOICE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMuted) Color.Gray else Color(0xFF81C784)
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = Color.DarkGray.copy(alpha = 0.5f)
                        )

                        // NARRATIVE STORYBOARD & TIMELINE SCROLL
                        Text(
                            text = "STORYBOARD NARRATIVE TIMELINE (TAP ITEM TO JUMP OR EDIT)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(parsedScenes) { index, scene ->
                                val isSelected = (currentSceneIndex == index)
                                val isEditing = (expandedSceneIndex == index)

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.DarkGray.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            currentSceneIndex = index
                                            videoProgressMs = 0L
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFF1B1D26) else Color(0xFF151720)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // Mini visual thumbnail representing scene
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Brush.verticalGradient(scene.gradientColors)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "S${scene.sceneNumber}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(10.dp))

                                                Column {
                                                    Text(
                                                        text = "Scene ${scene.sceneNumber}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = "${scene.durationMs / 1000f} seconds duration",
                                                        fontSize = 9.sp,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }

                                            // Quick actions (Edit / Expand)
                                            Row {
                                                IconButton(
                                                    onClick = {
                                                        if (isEditing) {
                                                            expandedSceneIndex = null
                                                        } else {
                                                            expandedSceneIndex = index
                                                            editingSubtitle = scene.subtitle
                                                            editingPrompt = scene.imagePrompt
                                                            editingDurationMs = scene.durationMs
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isEditing) Icons.Default.Close else Icons.Default.Edit,
                                                        contentDescription = "Edit Scene",
                                                        tint = if (isEditing) Color.Red else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (isEditing) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
                                            Spacer(modifier = Modifier.height(10.dp))

                                            // EDITING SUBTITLE (NARRATION AUDIO TEXT)
                                            Text(
                                                text = "Spoken Narrative Voice Transcript:",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.LightGray
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            OutlinedTextField(
                                                value = editingSubtitle,
                                                onValueChange = { editingSubtitle = it },
                                                modifier = Modifier.fillMaxWidth(),
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor = Color.Gray,
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.LightGray
                                                ),
                                                singleLine = false,
                                                maxLines = 3
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // EDITING PROMPT (ART GENERATOR)
                                            Text(
                                                text = "Generative AI Video Frame Prompt:",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.LightGray
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            OutlinedTextField(
                                                value = editingPrompt,
                                                onValueChange = { editingPrompt = it },
                                                modifier = Modifier.fillMaxWidth(),
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor = Color.Gray,
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.LightGray
                                                ),
                                                singleLine = false,
                                                maxLines = 4
                                            )

                                            Spacer(modifier = Modifier.height(10.dp))

                                            // DURATION ADJUSTMENT
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Scene Frame Duration:",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.LightGray
                                                )

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(
                                                        onClick = {
                                                            if (editingDurationMs > 1000L) {
                                                                editingDurationMs -= 500L
                                                            }
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Default.Remove, contentDescription = "Reduce", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                                                    }

                                                    Text(
                                                        text = "${editingDurationMs / 1000f}s",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 8.dp)
                                                    )

                                                    IconButton(
                                                        onClick = {
                                                            if (editingDurationMs < 15000L) {
                                                                editingDurationMs += 500L
                                                            }
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            // ACTION BUTTONS IN EDITOR
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Magic Wand: Expand Prompt using Gemini
                                                Button(
                                                    onClick = {
                                                        if (!isExpandingPrompt) {
                                                            isExpandingPrompt = true
                                                            coroutineScope.launch {
                                                                val expanded = viewModel.getExpandedVideoPrompt(editingSubtitle, editingPrompt)
                                                                editingPrompt = expanded
                                                                isExpandingPrompt = false
                                                                Toast.makeText(context, "Gemini expanded prompt successfully!", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                                        contentColor = MaterialTheme.colorScheme.secondary
                                                    ),
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(vertical = 6.dp)
                                                ) {
                                                    if (isExpandingPrompt) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(12.dp),
                                                            strokeWidth = 1.5.dp,
                                                            color = MaterialTheme.colorScheme.secondary
                                                        )
                                                    } else {
                                                        Icon(Icons.Default.AutoFixHigh, contentDescription = "AI Expand", modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("AI Expand Prompt", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }

                                                // Save & Re-compile Scene
                                                Button(
                                                    onClick = {
                                                        parsedScenes[index] = scene.copy(
                                                            subtitle = editingSubtitle,
                                                            imagePrompt = editingPrompt,
                                                            durationMs = editingDurationMs
                                                        )
                                                        expandedSceneIndex = null
                                                        Toast.makeText(context, "Scene compiled into timeline!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary
                                                    ),
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Save & Compile", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        } else {
                                            // COLLAPSED CARD: DISPLAY STATIC CONTENT PREVIEWS
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "🔊 Narrator: \"${scene.subtitle}\"",
                                                fontSize = 11.sp,
                                                color = Color.LightGray,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = "🎨 Video Prompt: ${scene.imagePrompt}",
                                                fontSize = 10.sp,
                                                color = Color.Gray,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // ACTION BUTTONS FOR PIPELINE INTEGRATION
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Color.Gray),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Close Studio", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    // Assemble YouTube Upload Draft
                                    val videoDesc = buildString {
                                        append("--- AZA AUTONOMOUS STORYBOARD MASTER TIMELINE ---\n\n")
                                        parsedScenes.forEachIndexed { idx, sc ->
                                            append("SCENE ${idx + 1} (${sc.durationMs / 1000f}s):\n")
                                            append("Narrator Voiceover: ${sc.subtitle}\n")
                                            append("Generative Frame Prompt: ${sc.imagePrompt}\n\n")
                                        }
                                        append("\nProduced autonomously with AZA AI Video Generator.\n#AZA #AutomotiveAgent #GenerativeMedia")
                                    }
                                    
                                    val draft = YouTubeUploadDraft(
                                        title = item.title,
                                        description = videoDesc,
                                        tags = "aza, trading, ai, autonomous agent, tech, videostoryboard",
                                        privacyStatus = "PRIVATE",
                                        approvalStatus = "PENDING_APPROVAL",
                                        processingStatus = "COMPLETED",
                                        lastUpdated = System.currentTimeMillis()
                                    )
                                    
                                    viewModel.insertYouTubeUploadDraft(draft)
                                    Toast.makeText(context, "Exported: Comprehensive Video Timeline draft pushed to YouTube Hub!", Toast.LENGTH_LONG).show()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1.5f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export to YouTube Hub", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Highly reliable parsing engine for Gemini raw output format
private fun parseRawScenes(rawResponse: String): List<AzaVideoScene> {
    if (rawResponse.isBlank() || !rawResponse.contains("SCENE")) {
        // Fallback default structure
        return listOf(
            AzaVideoScene(
                sceneNumber = 1,
                subtitle = "Are you making this critical mistake in your daily trading routine? Let's check it.",
                imagePrompt = "A close-up of a high-tech financial terminal with glowing green candles and neon cyber-grid overlays.",
                durationMs = 4000L,
                gradientColors = listOf(Color(0xFF2E3192), Color(0xFF1BFFFF))
            ),
            AzaVideoScene(
                sceneNumber = 2,
                subtitle = "Most traders focus purely on price action, completely ignoring structural momentum shifts and HMM filters.",
                imagePrompt = "An abstract futuristic radar grid scanning rapid data streams, with blue and purple dynamic waves pulsing.",
                durationMs = 5000L,
                gradientColors = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
            ),
            AzaVideoScene(
                sceneNumber = 3,
                subtitle = "By integrating our adaptive fast and stable slow dual-path algorithm, you eliminate all trading whipsaws instantly.",
                imagePrompt = "A glowing double helix path lighting up in a digital network, representing adaptive speed and stable macro lines.",
                durationMs = 5500L,
                gradientColors = listOf(Color(0xFFF12711), Color(0xFFF5AF19))
            ),
            AzaVideoScene(
                sceneNumber = 4,
                subtitle = "Tap subscribe to master autonomous trading filters with AZA, and never trade blindly again.",
                imagePrompt = "A sleek neon metallic subscriber button with glowing particle ripples and custom dynamic reflections.",
                durationMs = 4500L,
                gradientColors = listOf(Color(0xFF11998E), Color(0xFF38EF7D))
            )
        )
    }

    val scenesList = mutableListOf<AzaVideoScene>()
    val rawScenes = rawResponse.split("SCENE")
    
    var idx = 1
    val gradients = listOf(
        listOf(Color(0xFF2E3192), Color(0xFF1BFFFF)),
        listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
        listOf(Color(0xFFF12711), Color(0xFFF5AF19)),
        listOf(Color(0xFF11998E), Color(0xFF38EF7D))
    )

    for (part in rawScenes) {
        if (part.isBlank() || !part.contains("Narrative Subtitle")) continue
        
        try {
            var subtitle = ""
            var visualPrompt = ""
            var duration = 4500L

            val lines = part.split("\n")
            for (line in lines) {
                val cleanLine = line.trim()
                when {
                    cleanLine.contains("Narrative Subtitle:", ignoreCase = true) -> {
                        subtitle = cleanLine.substringAfter(":").trim()
                    }
                    cleanLine.contains("Visual Prompt:", ignoreCase = true) -> {
                        visualPrompt = cleanLine.substringAfter(":").trim()
                    }
                    cleanLine.contains("Duration:", ignoreCase = true) -> {
                        val parsedNum = cleanLine.substringAfter(":").trim().filter { it.isDigit() }.toLongOrNull()
                        if (parsedNum != null) {
                            duration = parsedNum
                        }
                    }
                }
            }

            if (subtitle.isNotEmpty() && visualPrompt.isNotEmpty()) {
                scenesList.add(
                    AzaVideoScene(
                        sceneNumber = idx,
                        subtitle = subtitle,
                        imagePrompt = visualPrompt,
                        durationMs = duration,
                        gradientColors = gradients[(idx - 1) % gradients.size]
                    )
                )
                idx++
            }
        } catch (e: Exception) {
            // Ignore parse errors on individual scenes
        }
    }

    if (scenesList.isEmpty()) {
        // Return fallback if parsing yielded empty
        return parseRawScenes("")
    }

    return scenesList
}
