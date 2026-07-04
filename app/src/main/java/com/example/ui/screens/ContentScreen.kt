package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.ContentItem
import com.example.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ContentScreen(
    viewModel: AppViewModel,
    innerPadding: PaddingValues
) {
    val contentItems by viewModel.contentItems.collectAsState()
    val isGenerating by viewModel.isGeneratingScript.collectAsState()
    val accountInfo by viewModel.accountInfo.collectAsState()

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
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = innerPadding.calculateBottomPadding() + 8.dp
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
                                    .minimumInteractiveComponentSize()
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
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
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
                                                    text = when (item.status.uppercase(java.util.Locale.US)) {
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

// Inline fake container selection helper
@Composable
fun SelectionContainer(content: @Composable () -> Unit) {
    Box { content() }
}

data class SocialPlatformInfo(
    val id: String,
    val label: String,
    val brandColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
