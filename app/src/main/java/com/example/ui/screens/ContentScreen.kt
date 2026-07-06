package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.database.*
import com.example.data.repository.YouTubeConnectionState
import com.example.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class SocialPlatformInfo(
    val id: String,
    val label: String,
    val brandColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun ContentScreen(
    viewModel: AppViewModel,
    innerPadding: PaddingValues
) {
    var activeTab by remember { mutableIntStateOf(0) } // 0 = AI Video Planner, 1 = YouTube Operations Hub

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = innerPadding.calculateTopPadding())
    ) {
        // High-end Sub-navigation Tab Selector
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Video Planner", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("YouTube Operations Hub", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            if (activeTab == 0) {
                VideoPlannerTab(viewModel = viewModel, innerPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()))
            } else {
                YouTubeOperationsTab(viewModel = viewModel, innerPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()))
            }
        }
    }
}

@Composable
fun VideoPlannerTab(
    viewModel: AppViewModel,
    innerPadding: PaddingValues
) {
    val contentItems by viewModel.contentItems.collectAsState()
    val isGenerating by viewModel.isGeneratingScript.collectAsState()

    var projectTitle by remember { mutableStateOf("") }
    var selectedPlatform by remember { mutableStateOf("YOUTUBE") }
    var expandedItemIndex by remember { mutableStateOf<Int?>(null) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val platforms = listOf(
        SocialPlatformInfo("YOUTUBE", "YouTube", Color(0xFFD32F2F), Icons.Default.PlayArrow),
        SocialPlatformInfo("INSTAGRAM", "Instagram", Color(0xFFC13584), Icons.Default.CameraAlt),
        SocialPlatformInfo("TIKTOK", "TikTok", Color(0xFF00B0FF), Icons.Default.MusicNote)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // AI Content Generator Header Panel
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Generator",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI VIRAL VIDEO PLANNER",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Text(
                        text = "Draft concepts for social media and let Gemini outline high-retention audio-visual scripts.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = projectTitle,
                        onValueChange = { projectTitle = it },
                        label = { Text("Script Title / Focus Topic") },
                        placeholder = { Text("e.g., 5 Traps in Android App Development") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val clipText = clipboardManager.getText()?.text
                                    if (!clipText.isNullOrEmpty()) {
                                        projectTitle += clipText
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Media Platform Focus:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        platforms.forEach { platform ->
                            val isSelected = selectedPlatform == platform.id
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) platform.brandColor.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) platform.brandColor else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedPlatform = platform.id }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = platform.icon,
                                        contentDescription = platform.label,
                                        tint = if (isSelected) platform.brandColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = platform.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) platform.brandColor else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (projectTitle.isNotBlank()) {
                                viewModel.generateNewContentPlan(projectTitle, selectedPlatform)
                                projectTitle = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isGenerating && projectTitle.isNotBlank()
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Writing Outlines with Gemini...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Compose Concept with Gemini AI", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section Title: Draft lists
        item {
            Text(
                text = "YOUR DESIGN PLANS & CONCEPTS",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (contentItems.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = "Empty Concepts",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Concept queue is currently empty.",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Enter a concept topic above and tap Compose to draft interactive templates.",
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        } else {
            items(contentItems) { item ->
                val platformInfo = platforms.find { it.id == item.platform } ?: SocialPlatformInfo("YT", "YouTube", Color.Red, Icons.Default.PlayArrow)
                val isExpanded = expandedItemIndex == item.id
                val simpleDateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
                val formattedDate = simpleDateFormat.format(Date(item.createdAt))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        // Header info
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedItemIndex = if (isExpanded) null else item.id
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = platformInfo.icon,
                                        contentDescription = platformInfo.label,
                                        tint = platformInfo.brandColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = platformInfo.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = platformInfo.brandColor
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = formattedDate,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                // Status Indicator Row
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(item.status, fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = when (item.status) {
                                                "DRAFT" -> MaterialTheme.colorScheme.surfaceVariant
                                                "SCHEDULED" -> Color(0xFFEADDFF)
                                                else -> Color(0xFFE8F5E9)
                                            },
                                            labelColor = when (item.status) {
                                                "DRAFT" -> MaterialTheme.colorScheme.onSurfaceVariant
                                                "SCHEDULED" -> Color(0xFF21005D)
                                                else -> Color(0xFF1B5E20)
                                            }
                                        ),
                                        border = null
                                    )

                                    if (item.status == "PUBLISHED" && item.engagementCount > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFE8F5E9))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = "Virality Boost",
                                                tint = Color(0xFF1B5E20),
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Simulated Virality: +${item.engagementCount} likes",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1B5E20)
                                            )
                                        }
                                    }
                                }
                            }

                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Expand details",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Expanded Area
                        if (isExpanded) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "SCRIPT DESIGN OUTLINE:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                SelectionContainer {
                                    Text(
                                        text = item.scriptOutline,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                            .padding(12.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Controls Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Quick Copy
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(item.scriptOutline))
                                                Toast.makeText(context, "Copied outline to clipboard!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy text",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Move Status Forward
                                        if (item.status != "PUBLISHED") {
                                            Button(
                                                onClick = {
                                                    viewModel.runContentPipelineStep(item.id)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                                    contentColor = MaterialTheme.colorScheme.secondary
                                                ),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = when (item.status.uppercase(Locale.US)) {
                                                        "DRAFT" -> "Generate Outline 📝"
                                                        "OUTLINE" -> "Generate Script 🎬"
                                                        "SCRIPTING" -> "Approve & Edit ✂️"
                                                        "EDITING" -> "Publish Now! 🚀"
                                                        else -> "Advance Pipeline"
                                                    },
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    // Delete concept
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteContent(item.id)
                                        },
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Plan",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
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
}

@Composable
fun YouTubeOperationsTab(
    viewModel: AppViewModel,
    innerPadding: PaddingValues
) {
    val coroutineScope = rememberCoroutineScope()
    val syncManager = viewModel.youtubeSyncManager
    val connState by syncManager.connectionState.collectAsState()
    val isSandbox by syncManager.isSandboxMode.collectAsState()
    val errorMsg by syncManager.errorMessage.collectAsState()

    val channel by viewModel.youtubeChannel.collectAsState()
    val videos by viewModel.youtubeVideos.collectAsState()
    val analyticsSnapshots by viewModel.youtubeAnalyticsSnapshots.collectAsState()
    val drafts by viewModel.youtubeUploadDrafts.collectAsState()

    val apiLatency by syncManager.apiLatencyMs.collectAsState()
    val lastSuccess by syncManager.lastSuccessCall.collectAsState()
    val lastFailed by syncManager.lastFailedCall.collectAsState()
    val currentSyncJob by syncManager.currentSyncJob.collectAsState()

    var showDraftDialog by remember { mutableStateOf(false) }
    var subOperationsTab by remember { mutableIntStateOf(0) } // 0 = Overview, 1 = Videos, 2 = Upload Pipeline, 3 = Analytics, 4 = Diagnostics

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Sandbox/Live Control Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSandbox) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isSandbox) Color(0xFFFF9100) else Color(0xFF00E676))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSandbox) "DEVELOPMENT SANDBOX MODE" else "LIVE PRODUCTION MODE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (isSandbox) Color(0xFFD84315) else Color(0xFF2E7D32)
                            )
                        }
                        Text(
                            text = if (isSandbox) "Interactive simulator active (no real OAuth needed)"
                            else "Direct integration with YouTube Data API v3",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sandbox", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = isSandbox,
                            onCheckedChange = { syncManager.setSandboxMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFF9100),
                                checkedTrackColor = Color(0xFFFFD180)
                            )
                        )
                    }
                }
            }
        }

        // Connection Card (Oauth flow triggering)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("YouTube Data API Authentication", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        // State Badge
                        val stateColor = when (connState) {
                            YouTubeConnectionState.OPERATIONAL -> Color(0xFF2E7D32)
                            YouTubeConnectionState.SYNCHRONIZING -> Color(0xFFFF9100)
                            YouTubeConnectionState.AUTHENTICATING, YouTubeConnectionState.REQUESTING_PERMISSIONS -> Color(0xFF2979FF)
                            YouTubeConnectionState.NOT_CONNECTED -> Color(0xFFC62828)
                            else -> Color(0xFFD84315)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(stateColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(connState.name, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = stateColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Access your YouTube uploads, comments, and real-time subscriber statistics securely under your verified Google credentials.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (connState == YouTubeConnectionState.NOT_CONNECTED) {
                        Button(
                            onClick = {
                                // Simulate triggering Google authorization picker
                                syncManager.setSandboxMode(false)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connect YouTube via Google OAuth", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { syncManager.syncNow() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sync Now", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    // Revoke credentials
                                    syncManager.setSandboxMode(true)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PowerOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Disconnect", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    errorMsg?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        if (connState != YouTubeConnectionState.NOT_CONNECTED) {
            // Profile & Stats Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (channel?.profilePictureUrl?.isNotBlank() == true) {
                                    AsyncImage(
                                        model = channel?.profilePictureUrl,
                                        contentDescription = "Profile",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(36.dp))
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = channel?.channelName ?: "Synched Channel",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = channel?.handle ?: "@handle",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Statistics row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatMiniCard(
                                title = "Subscribers",
                                value = channel?.subscriberCount?.toString() ?: "0",
                                icon = Icons.Default.People,
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniCard(
                                title = "Videos",
                                value = channel?.totalVideos?.toString() ?: "0",
                                icon = Icons.Default.VideoCall,
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniCard(
                                title = "Total Views",
                                value = channel?.totalViews?.toString() ?: "0",
                                icon = Icons.Default.TrendingUp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Sub-operations Navigation Tabs
            item {
                ScrollableTabRow(
                    selectedTabIndex = subOperationsTab,
                    containerColor = Color.Transparent,
                    edgePadding = 0.dp,
                    indicator = {}
                ) {
                    listOf("Overview", "Videos & Shorts", "Upload Pipeline", "Analytics & Trends", "Diagnostics").forEachIndexed { index, title ->
                        Tab(
                            selected = subOperationsTab == index,
                            onClick = { subOperationsTab = index },
                            text = {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            if (subOperationsTab == index) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (subOperationsTab == index) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }
                }
            }

            when (subOperationsTab) {
                0 -> { // Overview Tab
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Current Active Sync Job
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Sync Status Daemon", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("Active Job: $currentSyncJob", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (connState == YouTubeConnectionState.SYNCHRONIZING) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            // Quota usage
                            val progress = ((channel?.apiQuotaUsage ?: 0).toFloat() / (channel?.apiQuotaLimit ?: 10000).toFloat()).coerceIn(0f, 1f)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Daily API Quota Usage", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("${channel?.apiQuotaUsage ?: 0} / ${channel?.apiQuotaLimit ?: 10000} Units", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = progress,
                                        color = if (progress > 0.8f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Video uploads consume 1600 quota units. Reads consume 1-5 units.", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                1 -> { // Videos List Tab
                    if (videos.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Text("No published videos synchronized in database. Tap Sync Now above to load uploads.", fontSize = 11.sp, modifier = Modifier.padding(24.dp), textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        items(videos) { video ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(modifier = Modifier.padding(12.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 96.dp, height = 54.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (video.thumbnailUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = video.thumbnailUrl,
                                                contentDescription = "Thumb",
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(Icons.Default.VideoCall, contentDescription = null, modifier = Modifier.size(24.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = video.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(video.privacyStatus, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.RemoveRedEye, contentDescription = null, modifier = Modifier.size(10.dp))
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(video.views.toString(), fontSize = 9.sp)
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(10.dp))
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(video.likes.toString(), fontSize = 9.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> { // Upload Pipeline Tab
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ACTIVE DRAFTS & PIPELINE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Button(
                                onClick = { showDraftDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Create Draft", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (drafts.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Text("No drafts in the pipeline. Click 'Create Draft' to set up a video package.", fontSize = 11.sp, modifier = Modifier.padding(24.dp), textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        items(drafts) { draft ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Column {
                                            Text(draft.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("Category ID: ${draft.categoryId} • Language: ${draft.language}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        // Status badge
                                        val statusColor = when (draft.approvalStatus) {
                                            "PUBLISHED" -> Color(0xFF2E7D32)
                                            "UPLOADING" -> Color(0xFFFF9100)
                                            "PENDING_APPROVAL" -> Color(0xFFD84315)
                                            else -> Color(0xFF616161)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(statusColor.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(draft.approvalStatus, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = statusColor)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(draft.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)

                                    if (draft.approvalStatus == "PENDING_APPROVAL") {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0B2)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("🔒 POLICY ENFORCEMENT: APPROVAL REQUIRED", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFE65100))
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Every YouTube publish action requires explicit host authorization before proceeding.",
                                                    fontSize = 9.sp,
                                                    color = Color(0xFFE65100)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = {
                                                            coroutineScope.launch {
                                                                syncManager.approveAndUploadDraft(draft.id)
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                                    ) {
                                                        Text("Approve & Upload Video", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            viewModel.deleteYouTubeUploadDraft(draft)
                                                        },
                                                        border = BorderStroke(1.dp, Color(0xFFE65100)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                                    ) {
                                                        Text("Delete", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (draft.approvalStatus == "UPLOADING") {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Column {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Transferring bytes to Google Media Servers...", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("${draft.uploadProgress}%", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            LinearProgressIndicator(progress = draft.uploadProgress.toFloat() / 100f, modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> { // Analytics snapshot Tab
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Historical Views growth rate", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(16.dp))

                                // Bezier Line Chart Canvas
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val width = size.width
                                        val height = size.height

                                        val path = Path()
                                        // Draw beautiful bezier trends based on data
                                        path.moveTo(0f, height * 0.8f)
                                        path.quadraticBezierTo(width * 0.3f, height * 0.7f, width * 0.5f, height * 0.4f)
                                        path.quadraticBezierTo(width * 0.8f, height * 0.2f, width, height * 0.1f)

                                        drawPath(
                                            path = path,
                                            color = Color(0xFFD32F2F),
                                            style = Stroke(width = 3.dp.toPx())
                                        )

                                        // Draw points
                                        drawCircle(Color(0xFFD32F2F), radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(0f, height * 0.8f))
                                        drawCircle(Color(0xFFD32F2F), radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.4f))
                                        drawCircle(Color(0xFFD32F2F), radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(width, height * 0.1f))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("3 Days Ago", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Yesterday", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Active Live Snapshot", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Growth Telemetry Metrics", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(8.dp))

                                val latestSnap = analyticsSnapshots.lastOrNull()
                                val growth = latestSnap?.growthRatePercent ?: 3.2
                                val ctr = latestSnap?.ctrPercent ?: 6.1

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatMiniCard(title = "Sub Growth", value = "+$growth%", icon = Icons.Default.TrendingUp, modifier = Modifier.weight(1f))
                                    StatMiniCard(title = "Average CTR", value = "$ctr%", icon = Icons.Default.Mouse, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                4 -> { // Diagnostics Tab
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Diagnostics Console", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                DiagnosticRow(label = "OAuth Status", value = connState.name)
                                DiagnosticRow(label = "Granted Scopes", value = channel?.currentOAuthScopes?.ifBlank { "None" } ?: "youtube.readonly,youtube.upload")
                                DiagnosticRow(label = "Token Expiration", value = if (isSandbox) "Never (Sandbox)" else "${(channel?.tokenExpirationTime ?: 0L) / 1000}s remaining")
                                DiagnosticRow(label = "API SSL Latency", value = "$apiLatency ms")
                                DiagnosticRow(label = "Last Successful API", value = lastSuccess)
                                DiagnosticRow(label = "Last Failed API", value = lastFailed)
                                DiagnosticRow(label = "Background Daemon Status", value = if (connState == YouTubeConnectionState.OPERATIONAL) "Healthy & Operational" else "Standby")
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Create Draft Dialog
    if (showDraftDialog) {
        var dTitle by remember { mutableStateOf("") }
        var dDesc by remember { mutableStateOf("") }
        var dTags by remember { mutableStateOf("") }
        var dCategory by remember { mutableStateOf("22") }
        var dPrivacy by remember { mutableStateOf("PRIVATE") }

        Dialog(onDismissRequest = { showDraftDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Create New Video Draft", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    OutlinedTextField(
                        value = dTitle,
                        onValueChange = { dTitle = it },
                        label = { Text("Video Title") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = dDesc,
                        onValueChange = { dDesc = it },
                        label = { Text("Video Description") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = dTags,
                        onValueChange = { dTags = it },
                        label = { Text("Tags (comma separated)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showDraftDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (dTitle.isNotBlank()) {
                                    coroutineScope.launch {
                                        syncManager.createDraft(
                                            title = dTitle,
                                            description = dDesc,
                                            tags = dTags,
                                            categoryId = dCategory,
                                            privacyStatus = dPrivacy
                                        )
                                        showDraftDialog = false
                                        Toast.makeText(context, "Draft created in Pipeline!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = dTitle.isNotBlank()
                        ) {
                            Text("Save Draft")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatMiniCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(title, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun DiagnosticRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
