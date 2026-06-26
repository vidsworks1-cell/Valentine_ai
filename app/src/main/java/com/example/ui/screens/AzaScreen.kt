package com.example.ui.screens

import java.util.Locale

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.ChatMessage
import com.example.data.database.AzaChannel
import com.example.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AzaScreen(
    viewModel: AppViewModel,
    innerPadding: PaddingValues
) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isAzaResponding by viewModel.isAzaResponding.collectAsStateWithLifecycle()
    
    var activeAzaSubTab by remember { mutableStateOf(0) }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current

    // Auto-scroll to the latest message on update
    LaunchedEffect(messages.size, isAzaResponding) {
        if (messages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val quickPrompts = listOf(
        "Buy 0.05 BTC",
        "Sell 1 AAPL",
        "Add script about Jetpack Compose on YouTube",
        "Write proposal for tech article gig",
        "Brief portfolio status"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tab system for Chat vs Autopilot Cockpit Dashboard
        TabRow(
            selectedTabIndex = activeAzaSubTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            Tab(
                selected = activeAzaSubTab == 0,
                onClick = { activeAzaSubTab = 0 },
                text = { Text("Consulting Advisor", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                icon = { Icon(Icons.Default.QuestionAnswer, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = activeAzaSubTab == 1,
                onClick = { activeAzaSubTab = 1 },
                text = { Text("Autopilot Pilot", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = activeAzaSubTab == 2,
                onClick = { activeAzaSubTab = 2 },
                text = { Text("Web Explorer", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = activeAzaSubTab == 3,
                onClick = { activeAzaSubTab = 3 },
                text = { Text("Validation Lab", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = activeAzaSubTab == 4,
                onClick = { activeAzaSubTab = 4 },
                text = { Text("Knowledge Hub", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                icon = { Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        if (activeAzaSubTab == 0) {
            // Tab 0: Chat Console Workflow
            // Aza Status Panel Header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Outer glowing sphere
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Aza Sparkle",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Aza AI Assistant",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isAzaResponding) "Aza is thinking..." else "Linked to Trading, Content & Gigs",
                            fontSize = 12.sp,
                            color = if (isAzaResponding) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = if (isAzaResponding) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    IconButton(
                        onClick = { viewModel.clearChat() },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Conversation Thread")
                    }
                }
            }

            // Messages Conversation Stream
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QuestionAnswer,
                                contentDescription = "No Messages",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No conversation logs. Send a message to wake up Aza!",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages) { message ->
                            MessageBubble(message = message)
                        }

                        if (isAzaResponding) {
                            item {
                                AzaThinkingBubble()
                            }
                        }

                        // Bottom list pad spacer
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }

            // Horizontal Quick-Command Helper Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickPrompts) { prompt ->
                    AssistChip(
                        onClick = {
                            viewModel.sendMessage(prompt)
                        },
                        label = { Text(prompt, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = "Shortcut Action",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            labelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Send Text Composition Row
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask Aza, or type e.g., 'buy 0.1 btc'", fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val clipText = clipboardManager.getText()?.text
                                    if (!clipText.isNullOrEmpty()) {
                                        inputText += clipText
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send,
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                    keyboardController?.hide()
                                }
                            }
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                                keyboardController?.hide()
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message to Aza",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        } else if (activeAzaSubTab == 1) {
            // Tab 1: Autopilot Dashboard Core Cockpit / Mission Control Center View
            val isAutopilotActive by viewModel.isAutopilotActive.collectAsStateWithLifecycle()
            val thoughts by viewModel.autopilotThoughts.collectAsStateWithLifecycle()
            val azaChannels by viewModel.azaChannels.collectAsStateWithLifecycle()
            val activeObjective by viewModel.activeObjective.collectAsStateWithLifecycle()
            val activeObjectiveTasks by viewModel.activeObjectiveTasks.collectAsStateWithLifecycle()
            val pendingApprovalTasks by viewModel.pendingApprovalTasks.collectAsStateWithLifecycle()
            val executionRecords by viewModel.executionRecords.collectAsStateWithLifecycle()
            val learningRecords by viewModel.learningRecords.collectAsStateWithLifecycle()
            val isAutonomousMode by viewModel.isAutonomousMode.collectAsStateWithLifecycle()
            val safetyRules by viewModel.safetyRules.collectAsStateWithLifecycle()
            val portfolioAssets by viewModel.portfolioAssets.collectAsStateWithLifecycle()

            val strategies by viewModel.strategies.collectAsStateWithLifecycle()
            val backtestReports by viewModel.backtestReports.collectAsStateWithLifecycle()
            val isBacktesting by viewModel.isBacktesting.collectAsStateWithLifecycle()
            val lastBacktestReport by viewModel.lastBacktestReport.collectAsStateWithLifecycle()
            val selectedBacktestStrategy by viewModel.selectedBacktestStrategy.collectAsStateWithLifecycle()
            val backtestMarket by viewModel.backtestMarket.collectAsStateWithLifecycle()
            val backtestCandleCount by viewModel.backtestCandleCount.collectAsStateWithLifecycle()
            val backtestPattern by viewModel.backtestPattern.collectAsStateWithLifecycle()
            val marketRegime by viewModel.marketRegime.collectAsStateWithLifecycle()
            val optimizationRecords by viewModel.optimizationRecords.collectAsStateWithLifecycle()
            val tradeAuditLogs by viewModel.tradeAuditLogs.collectAsStateWithLifecycle()
            val exportedReports by viewModel.exportedReports.collectAsStateWithLifecycle()

            // Dynamic core components direct from viewmodel backplane
            val registeredSkills = viewModel.registeredSkills
            val registeredAgents = viewModel.registeredAgents

            var newObjectiveInput by remember { mutableStateOf("") }
            var editingSafetyRules by remember { mutableStateOf(false) }
            var safetyRulesInput by remember { mutableStateOf(safetyRules) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // PANEL 1: Autopilot Core & Safety Policy Manager (Human Approval vs Autonomous Modes)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Row 1: Main autopilot toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (isAutopilotActive) Color(0xFF4CAF50) else Color(0xFFE57373))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isAutopilotActive) "COCKPIT AUTOPILOT ENABLED" else "AUTOPILOT SUSPENDED",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp
                                    )
                                }
                                Switch(
                                    modifier = Modifier.testTag("autopilot_switch"),
                                    checked = isAutopilotActive,
                                    onCheckedChange = { viewModel.toggleAutopilot(it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Row 2: Mode selection toggle (Human Approval Mode vs Direct Autonomous Mode)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "EXECUTION POLICY MODE",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = if (isAutonomousMode) "Autonomous mode - Bypass manual confirmations" else "Approval mode - Requires User Auth keys",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "AUTONOMOUS",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAutonomousMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    Switch(
                                        modifier = Modifier.testTag("mode_toggle"),
                                        checked = isAutonomousMode,
                                        onCheckedChange = { viewModel.toggleAutonomousMode(it) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Interactive safety rules panel
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🛡️ Live Safety Boundaries Policy",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        TextButton(onClick = {
                                            if (editingSafetyRules) {
                                                viewModel.updateSafetyRules(safetyRulesInput)
                                            } else {
                                                safetyRulesInput = safetyRules
                                            }
                                            editingSafetyRules = !editingSafetyRules
                                        }) {
                                            Text(
                                                text = if (editingSafetyRules) "Save Bounds" else "Modify",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    if (editingSafetyRules) {
                                        OutlinedTextField(
                                            value = safetyRulesInput,
                                            onValueChange = { safetyRulesInput = it },
                                            modifier = Modifier.fillMaxWidth().testTag("safety_rules_input"),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                                            )
                                        )
                                    } else {
                                        Text(
                                            text = safetyRules,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.forceAutonomousRun() },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Force Autonomous Iteration", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // PANEL 2: Active High-Level Objective Formulation (Planner)
                if (activeObjective == null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Flag,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Deploy Global Objective",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Formulate targets and Aza's planner will decompose steps",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(14.dp))
                                
                                OutlinedTextField(
                                    value = newObjectiveInput,
                                    onValueChange = { newObjectiveInput = it },
                                    placeholder = { Text("e.g. Expand crypto portfolio, make a script on YouTube...", fontSize = 13.sp) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("objective_input"),
                                    shape = RoundedCornerShape(10.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                                    ),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                val clipText = clipboardManager.getText()?.text
                                                if (!clipText.isNullOrEmpty()) {
                                                    newObjectiveInput += clipText
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentPaste,
                                                contentDescription = "Paste",
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                )
                                
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                Button(
                                    onClick = {
                                        if (newObjectiveInput.isNotBlank()) {
                                            viewModel.setHighLevelObjective(newObjectiveInput)
                                            newObjectiveInput = ""
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(42.dp).testTag("deploy_objective_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Decompose and Execute Objective", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    item {
                        val completedCount = activeObjectiveTasks.count { it.status == "COMPLETED" || it.status == "REJECTED" }
                        val totalCount = activeObjectiveTasks.size
                        val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(if (activeObjective!!.status == "COMPLETED") Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "ACTIVE GLOBAL OBJECTIVE",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${completedCount}/${totalCount} Tasks Done",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                Text(
                                    text = activeObjective!!.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(
                                        text = "Status: ${activeObjective!!.status}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeObjective!!.status == "COMPLETED") Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Priority: ${activeObjective!!.priority}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "Current State Operational Reason:",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = activeObjective!!.completionReason ?: "Planning tasks running sequentially on coordinator queue...",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(14.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Focus Target: ${activeObjective!!.targetPlatform}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        onClick = { viewModel.clearActiveObjective() },
                                        modifier = Modifier.height(30.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Clear Objective", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // PANEL 3: SENSITIVE APPROVALS QUEUE (Only visible in user verification cycle)
                if (pendingApprovalTasks.isNotEmpty()) {
                    item {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF9800))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SENSITIVE HUMAN AUTHORIZATION FLOWS (${pendingApprovalTasks.size})",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    color = Color(0xFFFF9800),
                                    letterSpacing = 1.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Aza has paused execution on safety boundaries. Tap Approve to sign and authorize task payload.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    items(pendingApprovalTasks, key = { "appr_${it.id}" }) { task ->
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("approval_card_${task.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.25f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = null,
                                            tint = Color(0xFFFF9800),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = task.assignedAgent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(Color(0xFFFF9800).copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Priority: ${task.priority}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE65100)
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                Text(
                                    text = "Action Core: ${task.title}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = task.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                
                                if (task.estimatedBudget > 0.0) {
                                    Text(
                                        text = "Allocated Capital limit: $${task.estimatedBudget} USD",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.rejectAgentTask(task) },
                                        modifier = Modifier.height(32.dp).testTag("reject_button_${task.id}"),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                                    ) {
                                        Text("REJECT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    
                                    Spacer(modifier = Modifier.width(8.dp))
                                    
                                    Button(
                                        onClick = { viewModel.approveAgentTask(task) },
                                        modifier = Modifier.height(32.dp).testTag("approve_button_${task.id}"),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF4CAF50),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("APPROVE ACTION", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // PANEL 4: DECOMPOSED PLANNER TASKS STEPPER (Core Execution Queue)
                if (activeObjectiveTasks.isNotEmpty()) {
                    item {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PLANNING DECOMPOSITION & DECENTRALIZED SCHEDULER",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = "Decentralized task scheduler matching competencies with active agents.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    items(activeObjectiveTasks, key = { "task_${it.id}" }) { task ->
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("task_card_${task.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, 
                                if (task.status == "COMPLETED") Color(0xFF4CAF50).copy(alpha = 0.25f)
                                else if (task.status == "RUNNING") MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (task.status == "COMPLETED") Color(0xFFE8F5E9)
                                                    else if (task.status == "RUNNING") MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = when (task.status) {
                                                    "COMPLETED" -> Icons.Default.CheckCircle
                                                    "RUNNING" -> Icons.Default.Bolt
                                                    "REJECTED" -> Icons.Default.Cancel
                                                    else -> Icons.Default.Lock
                                                },
                                                contentDescription = null,
                                                tint = when (task.status) {
                                                    "COMPLETED" -> Color(0xFF4CAF50)
                                                    "RUNNING" -> MaterialTheme.colorScheme.primary
                                                    "REJECTED" -> MaterialTheme.colorScheme.error
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                },
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = task.assignedAgent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                when (task.status) {
                                                    "COMPLETED" -> Color(0xFFE8F5E9)
                                                    "RUNNING" -> MaterialTheme.colorScheme.primaryContainer
                                                    "REJECTED" -> MaterialTheme.colorScheme.errorContainer
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = task.status,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (task.status) {
                                                "COMPLETED" -> Color(0xFF2E7D32)
                                                "RUNNING" -> MaterialTheme.colorScheme.onPrimaryContainer
                                                "REJECTED" -> MaterialTheme.colorScheme.onErrorContainer
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                Text(
                                    text = task.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = task.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                
                                if (task.performanceImpact.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = "📈 Execution Outcomes: ${task.performanceImpact}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // PANEL 5: DECENTRALIZED SKILL REGISTRY (Capabilities Catalog)
                item {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AZA CORE DECENTRALIZED SKILL REGISTRY",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = "Centralized system competency specifications. Routing queries automatically.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                        ) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(registeredSkills) { skill ->
                                    Card(
                                        modifier = Modifier.width(220.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = skill.skillName.uppercase(),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Conf: ${String.format("%.0f%%", skill.confidence * 100)}",
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = skill.description,
                                                fontSize = 10.sp,
                                                lineHeight = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 3
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))
                                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                            Spacer(modifier = Modifier.height(6.dp))

                                            Text(
                                                text = "📥 Inputs: ${skill.supportedInputs.joinToString(", ")}",
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            Text(
                                                text = "📤 Outputs: ${skill.supportedOutputs.joinToString(", ")}",
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Cost: ${skill.executionCost} pts",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "Latency: ${skill.averageLatencyMs} ms",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // PANEL 6: ACTIVE DECENTRALIZED AGENT TOPOLOGY (Registry Grid)
                item {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ACTIVE AUTONOMOUS AGENT NODES",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = "Specialized cognitive agents registered within the decentralized planning loop.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                registeredAgents.forEach { agent ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF4CAF50))
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = agent.displayName,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                Text(
                                                    text = "ID: ${agent.id}",
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Matched Competency Schemas: ${agent.capabilities().joinToString { it.skill }}",
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // PANEL 7: DECENTRALIZED REINFORCEMENT LEARNING LOGS & STATISTICS
                item {
                    val learnCount = learningRecords.size
                    val positiveCount = learningRecords.count { it.success }
                    val avgLatencyLearn = if (learningRecords.isEmpty()) 0L else learningRecords.map { it.latencyMs }.average().toLong()

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "REINFORCEMENT LEARNING STATISTICS & FEEDBACK",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                            }
                            if (learnCount > 0) {
                                TextButton(
                                    onClick = { viewModel.clearLearningHistory() },
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Reset Logs", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        Text(
                            text = "Telemetry collected by learning engine to update routing weights based on reward histories.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("$learnCount", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Total Trials", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("$positiveCount", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2E7D32))
                                        Text("Rewards (+)", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${avgLatencyLearn}ms", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Avg Latency", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                if (learningRecords.isEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No outcomes registered yet. Run task loops to train policy weights.",
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column {
                                            learningRecords.take(5).forEach { record ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "Skill: ${record.skillName}",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                        Text(
                                                            text = "Outcome: ${if (record.success) "Optimized routing pass" else "Route degraded"}",
                                                            fontSize = 9.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(if (record.success) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "${if (record.success) "+1.0 Rec" else "-1.0 Rec"}",
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (record.success) Color(0xFF2E7D32) else Color(0xFFC62828)
                                                        )
                                                    }
                                                }
                                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // PANEL 8: ACTIVE PORTFOLIO ASSETS TRACKER (Operational Memory Vault)
                item {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ACTIVE MULTI-ASSET PORTFOLIO REAL-TIME AUDIT",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = "Secure capital custody. Changes inside task sandboxes settle directly here.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                        ) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(portfolioAssets) { asset ->
                                    Card(
                                        modifier = Modifier.width(130.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = asset.symbol,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = asset.displayName,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Owned: ${asset.amountOwned}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Price: $${asset.currentPrice}",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // PANEL 9: TERMINAL COGNITIVE LOG STREAM
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Terminal,
                                        contentDescription = null,
                                        tint = Color(0xFFBB86FC),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Aza Cognitive Log Stream",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFFBB86FC)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (thoughts.isNotEmpty()) {
                                                clipboardManager.setText(AnnotatedString(thoughts.joinToString("\n")))
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy all logs",
                                            tint = Color(0xFFBB86FC),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    if (isAutopilotActive) {
                                        Text(
                                            text = "● STANDBY",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF4CAF50)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .background(Color(0xFF12121A), RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                if (thoughts.isEmpty()) {
                                    Text(
                                        "No execution logs. Turn on autopilot above.",
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(thoughts) { log ->
                                            Text(
                                                text = log,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = if (log.contains("grew") || log.contains("Success") || log.contains("likes")) Color(0xFF81C784) 
                                                       else if (log.contains("Error") || log.contains("⚠️")) Color(0xFFE57373)
                                                       else Color(0xFFCFD8DC),
                                                lineHeight = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // PANEL 9.5: STRATEGY RESEARCH & BACKTESTING SANDBOX
                item {
                    var showLedgerReport by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Section Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Analytics,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Strategy Research & Sandbox",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("SANDBOX V1.2", color = MaterialTheme.colorScheme.primary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // MARKET INTELLIGENCE DETECTED
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("MARKET INTELLIGENCE DETECTED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text(
                                                text = "Regime: ${marketRegime.uppercase()}", 
                                                fontSize = 16.sp, 
                                                fontWeight = FontWeight.ExtraBold, 
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Aza continuously analyzes historical price sequences to identify structural market changes. Active strategy selection is continuously re-optimized to exploit this regime.",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.runAutonomousEvaluationAndOptimization("BTC") },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(28.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("Run Autonomous Evaluation & Optimization", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // 1. ACTIVE MODEL CONFIGURATION
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RANKED EXECUTION ALGORITHMS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp
                                )
                                val bestPerformer = strategies.maxByOrNull { it.paperNetProfit }
                                if (bestPerformer != null && bestPerformer.paperNetProfit > 0) {
                                    Text(
                                        text = "👑 Leader: ${bestPerformer.strategyName}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            val sortedStrategies = strategies.sortedByDescending { it.ranking }
                            sortedStrategies.forEachIndexed { index, strat ->
                                val isActive = strat.isActive
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                                                         else MaterialTheme.colorScheme.surface
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) 
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(text = "#${index + 1}", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = strat.strategyName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "v${strat.version}",
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                            Text(
                                                text = strat.description,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 13.sp,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                            
                                            // Strategy stats metrics grid
                                            Row(
                                                modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Win Rate: ${String.format(Locale.US, "%.1f", strat.winRate)}%", fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                                                    Text("Profit Factor: ${String.format(Locale.US, "%.2f", strat.profitFactor)}", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Sharpe Ratio: ${String.format(Locale.US, "%.2f", strat.sharpeRatio)}", fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                                                    Text("Max Drawdown: ${String.format(Locale.US, "%.1f", strat.maxDrawdown)}%", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Column(modifier = Modifier.weight(1.2f)) {
                                                    Text("Confidence Score: ${String.format(Locale.US, "%.2f", strat.currentConfidenceScore)}", fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                                                    Text("Optimal Regime: ${strat.designedRegimes}", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                                                }
                                            }
                                            if (strat.bestConfigParams.isNotEmpty()) {
                                                Text(
                                                    text = "Optimal Params: ${strat.bestConfigParams}",
                                                    fontSize = 8.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Color(0xFFE65100),
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                            
                                            Row(
                                                modifier = Modifier.padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(text = "Risk: ${strat.riskProfile}", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(text = "Paper trades: ${strat.totalPaperTrades} (${String.format(Locale.US, "%.1f", strat.paperWinRate)}% WR)", fontSize = 8.sp)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        if (isActive) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFFE8F5E9))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("ACTIVE", color = Color(0xFF2E7D32), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                            }
                                        } else {
                                            Button(
                                                onClick = { viewModel.changeActiveStrategy(strat.strategyName) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text("Activate", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // AUTONOMOUS PARAMETER OPTIMIZATION HISTORY LOG
                            if (optimizationRecords.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "AUTONOMOUS PARAMETER OPTIMIZATION HISTORY",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth().height(120.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                ) {
                                    LazyColumn(
                                        modifier = Modifier.padding(8.dp).fillMaxHeight(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(optimizationRecords) { record ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(record.strategyName, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    Text("Config: ${record.paramConfig}", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("Profit: +$${String.format(Locale.US, "%.2f", record.inSampleProfit)}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                                    Text("Win Rate: ${String.format(Locale.US, "%.1f", record.winRate)}%", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            Spacer(modifier = Modifier.height(12.dp))

                            // 2. BACKTEST CONTROL PANEL
                            Text(
                                text = "BACKTEST HISTORICAL SIMULATOR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Strategy Selection
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Select Strategy:", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("Trend Following", "Breakout", "Mean Reversion").forEach { name ->
                                        val isSel = selectedBacktestStrategy == name
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                                .border(1.dp, if (isSel) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                                .clickable { viewModel.selectBacktestStrategy(name) }
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = name.substringBefore(" "),
                                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Market Selection
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Asset Symbol:", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("BTC", "ETH", "AAPL", "NVDA").forEach { sym ->
                                        val isSel = backtestMarket == sym
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                                .border(1.dp, if (isSel) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                                .clickable { viewModel.selectBacktestMarket(sym) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = sym,
                                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Pattern & Candle Config Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Simulation Trend style:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                                        listOf("TREND", "CYCLIC").forEach { pat ->
                                            val isSel = backtestPattern == pat
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
                                                    .border(1.dp, if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                                    .clickable { viewModel.selectBacktestPattern(pat) }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = pat,
                                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("History Length (Candles):", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                                        listOf(50, 100, 200).forEach { cnt ->
                                            val isSel = backtestCandleCount == cnt
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
                                                    .border(1.dp, if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                                    .clickable { viewModel.setBacktestCandleCount(cnt) }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "$cnt",
                                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Action Buttons: Run Backtest & Trigger Signal
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.runBacktest() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = !isBacktesting
                                ) {
                                    if (isBacktesting) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Run Backtest", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Button(
                                    onClick = { viewModel.runPaperTradeSignal(backtestMarket) },
                                    modifier = Modifier.weight(1.0f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Evaluate Signal", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // 3. BACKTEST RESULTS METRICS DISPLAY
                            val activeReport = lastBacktestReport ?: backtestReports.lastOrNull { it.strategyName == selectedBacktestStrategy }
                            if (activeReport != null) {
                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "SIMULATION METRICS: ${activeReport.strategyName.uppercase()}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 0.5.sp
                                    )

                                    TextButton(
                                        onClick = { showLedgerReport = !showLedgerReport },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text(
                                            text = if (showLedgerReport) "Hide Ledger" else "View Full Ledger",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Grid of key results
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Net Profit", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = "${if (activeReport.netProfit >= 0) "+" else ""}$${String.format(Locale.US, "%,.2f", activeReport.netProfit)}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (activeReport.netProfit >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }

                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Win Rate", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = "${String.format(Locale.US, "%.1f", activeReport.winRate)}%",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Total Trades", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = "${activeReport.totalTrades}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Max Drawdown", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = "${String.format(Locale.US, "%.2f", activeReport.maxDrawdown)}%",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }

                                if (showLedgerReport) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp)
                                            .background(Color(0xFF0D0D14), RoundedCornerShape(8.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        val scrollState = rememberScrollState()
                                        Text(
                                            text = activeReport.reportDetails,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = Color(0xFFCFD8DC),
                                            lineHeight = 12.sp,
                                            modifier = Modifier.verticalScroll(scrollState)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        val formatted = com.example.aza.web.AiCollaborationExporter.formatBacktestReport(activeReport)
                                        clipboardManager.setText(AnnotatedString(formatted))
                                        val newReport = com.example.data.database.ExportedReportHistory(
                                            reportType = "Backtest Report",
                                            strategyName = activeReport.strategyName,
                                            market = "BTC",
                                            exportTimestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date()),
                                            formattedContent = formatted
                                        )
                                        viewModel.insertExportedReport(newReport)
                                        viewModel.addThought("📋 AI COLLABORATION: Exported Backtest Report for ${activeReport.strategyName} to AI console & copied to clipboard!")
                                    },
                                    modifier = Modifier.fillMaxWidth().height(36.dp).testTag("export_backtest_to_ai_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export to AI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // PANEL 10: REAL-TIME SYSTEM HEALTH & PACING MONITOR
                item {
                    val runtime = Runtime.getRuntime()
                    val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                    val maxMem = runtime.maxMemory() / (1024 * 1024)
                    val runningTasks = activeObjectiveTasks.count { it.status == "RUNNING" }
                    val failedTally = executionRecords.count { !it.success }
                    val totalRuns = executionRecords.size
                    
                    val avgMillis = if (executionRecords.isEmpty()) 0L else executionRecords.map { it.durationMs }.average().toLong()
                    val averageLatency = if (avgMillis > 0) "${avgMillis} ms" else "Idle"

                    val dbStatus = com.example.aza.market.MarketDataService.databaseStatus
                    val exchangeConnected = com.example.aza.market.MarketDataService.exchangeConnected
                    val latencyMs = com.example.aza.market.MarketDataService.currentLatencyMs
                    val dataQuality = com.example.aza.market.MarketDataService.dataQualityScore
                    val execQuality = com.example.aza.market.MarketDataService.executionQualityScore
                    val isDataStale = dataQuality < 90.0

                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("system_health_panel"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "System Health & Safety Verifiers",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isDataStale || dbStatus == "ERROR" || !exchangeConnected) Color(0xFFFFEBEE) else Color(0xFFE8F5E9))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isDataStale || dbStatus == "ERROR" || !exchangeConnected) "DEGRADED" else "ONLINE",
                                        color = if (isDataStale || dbStatus == "ERROR" || !exchangeConnected) Color(0xFFC62828) else Color(0xFF2E7D32),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Grid of indicators
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Database Connection", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(dbStatus, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (dbStatus == "HEALTHY") Color(0xFF2E7D32) else Color(0xFFC62828))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Status: Local Persistence", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    }
                                }

                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Exchange Connection", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(if (exchangeConnected) "CONNECTED" else "DISCONNECTED", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (exchangeConnected) Color(0xFF2E7D32) else Color(0xFFC62828))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Protocol: JSON-RPC over TLS", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Market Data Freshness", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(if (isDataStale) "STALE" else "OK", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isDataStale) Color(0xFFC62828) else Color(0xFF2E7D32))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Last checked: <1s ago", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    }
                                }

                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Active Timeframes", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("5m,15m,1h,4h,1D", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Primary Scale: 1h", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("API Latency", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${latencyMs} ms", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (latencyMs > 150) Color(0xFFE65100) else Color(0xFF2E7D32))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Target Limit: <250ms", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    }
                                }

                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Data Quality Score", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${String.format(Locale.US, "%.1f", dataQuality)}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isDataStale) Color(0xFFC62828) else Color(0xFF2E7D32))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Based on gap validation", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Execution Quality", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${String.format(Locale.US, "%.1f", execQuality)}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Slippage vs Target Limit", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    }
                                }

                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Active Threads", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("$runningTasks Running", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("JVM Mem: ${usedMem}M / ${maxMem}M", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                                    Text("AI Gateway (Gemini)", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                                    Text("Coordinator Engine", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                                    Text("SQLite (Room)", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // PANEL 10.5: AI COLLABORATION & COGNITIVE EXPORT
                item {
                    val context = LocalContext.current
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("ai_collaboration_panel"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "AI Collaboration Console",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (exportedReports.isNotEmpty()) {
                                    TextButton(
                                        onClick = { viewModel.clearAllExportedReports() },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text("Clear History", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Export system outputs, backtest reports, and cognitive thought chains as optimized Markdown documents formatted explicitly for quantitative LLM code review.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            val lastReport = exportedReports.firstOrNull()
                            if (lastReport != null) {
                                Text(
                                    text = "LAST EXPORTED INTEL REPORT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = lastReport.reportType,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = lastReport.exportTimestamp,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Strategy: ${lastReport.strategyName} | Asset: ${lastReport.market}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(lastReport.formattedContent))
                                                    viewModel.addThought("📋 AI COLLABORATION: Re-copied last report to clipboard!")
                                                },
                                                modifier = Modifier.weight(1f).height(36.dp).testTag("copy_report_btn"),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Copy Report", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    val sendIntent = Intent().apply {
                                                        action = Intent.ACTION_SEND
                                                        putExtra(Intent.EXTRA_TEXT, lastReport.formattedContent)
                                                        type = "text/plain"
                                                    }
                                                    val shareIntent = Intent.createChooser(sendIntent, "Share Report to AI")
                                                    context.startActivity(shareIntent)
                                                    viewModel.addThought("📤 AI COLLABORATION: Shared report intent triggered!")
                                                },
                                                modifier = Modifier.weight(1f).height(36.dp).testTag("share_report_btn")
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Share to...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No reports exported in current cycle.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                }
                            }

                            if (exportedReports.size > 1) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "EXPORT HISTORY (${exportedReports.size})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    exportedReports.drop(1).take(4).forEach { historicReport ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                                .padding(8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = historicReport.reportType,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Strategy: ${historicReport.strategyName} | ${historicReport.exportTimestamp}",
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                IconButton(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(historicReport.formattedContent))
                                                        viewModel.addThought("📋 AI COLLABORATION: Copied report '${historicReport.reportType}' to clipboard!")
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                }
                                                IconButton(
                                                    onClick = { viewModel.deleteExportedReportById(historicReport.id) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // PANEL 11: IMMUTABLE AUDIT TRAIL & EXECUTION RECORDS
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "IMMUTABLE AUDIT TRAIL & EXECUTION RECORDS",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            if (executionRecords.isNotEmpty()) {
                                TextButton(
                                    onClick = { viewModel.clearExecutionHistory() },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Clear Trail", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        if (executionRecords.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No recorded executions in database cache.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                            ) {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    items(executionRecords) { rec ->
                                        Card(
                                            modifier = Modifier.width(180.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(if (rec.success) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = if (rec.success) "SUCCESS" else "FAILED",
                                                            color = if (rec.success) Color(0xFF2E7D32) else Color(0xFFC62828),
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Text(
                                                        text = "${String.format("%.1f", rec.durationMs / 1000f)}s",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(6.dp))

                                                Text(
                                                    text = rec.agent,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "skill: ${rec.skill}",
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Text(
                                                    text = rec.outputSummary,
                                                    fontSize = 9.sp,
                                                    lineHeight = 11.sp,
                                                    maxLines = 2,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // PANEL 12: AUTONOMOUS PLATFORM CONNECTIONS & CHANNELS
                item {
                    Text(
                        text = "ACTIVE SOCIAL CONNECTIONS & ADAPTIVE STRATEGIES",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (azaChannels.isEmpty()) {
                    item {
                        Text(
                            "Initializing social components...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(azaChannels) { ch ->
                        val colorAccent = when (ch.platform) {
                            "YOUTUBE" -> Color(0xFFE53935)
                            "INSTAGRAM" -> Color(0xFFE1306C)
                            else -> Color(0xFF00F2FE)
                        }

                        val iconVector = when (ch.platform) {
                            "YOUTUBE" -> Icons.Default.PlayArrow
                            "INSTAGRAM" -> Icons.Default.CameraAlt
                            else -> Icons.Default.MusicNote
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(colorAccent.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                iconVector,
                                                contentDescription = null,
                                                tint = colorAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = ch.platform,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            color = colorAccent
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = ch.handle,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Text(
                                        text = ch.targetSegment,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Dynamic stats row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = String.format("%,d", ch.followersCount),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Followers",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = String.format("%,d", ch.totalVideosPublished),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Videos Shared",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = String.format("%,d", ch.totalLikesAccumulated),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Global Views/Likes",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        Icons.Default.Insights,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp).padding(top = 1.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "Aza Adaptive Strategy Rule:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = ch.autonomousInsight,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (activeAzaSubTab == 2) {
            WebSurferTabContent(viewModel)
        } else if (activeAzaSubTab == 3) {
            ValidationLabTabContent(viewModel)
        } else {
            KnowledgeHubTabContent(viewModel)
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.sender == "USER"
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val arrangement = if (isUser) Arrangement.End else Arrangement.Start
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = arrangement
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Aza Avatar",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Card(
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            val clipboardManager = LocalClipboardManager.current
            Column(modifier = Modifier.padding(12.dp)) {
                // Main message text rendering with rich custom Markdown parsing
                val primaryColor = MaterialTheme.colorScheme.primary
                Text(
                    text = parseFormattedMarkdown(message.text, isUser, primaryColor),
                    color = textColor,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(message.text))
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy message",
                            tint = textColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
        
        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = "User Avatar",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun AzaThinkingBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "Dots Shimmer")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Thinking Dots Alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Aza Avatar",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))

        Card(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.widthIn(max = 100.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (index == 0) alphaAnim else 0.5f))
                    )
                }
            }
        }
    }
}

/**
 * A native light-weight helper to parse bold (`**text**` and `*text*`) and bullets (`•`)
 * in conversational replies so they look flawless and gorgeous under Material Design 3 guidelines.
 */
fun parseFormattedMarkdown(text: String, isUser: Boolean, primaryColor: Color) = buildAnnotatedString {
    try {
        // Basic markdown tags parsing
        val regex = """(\*\*.*?\*\*|\*.*?\*|•.*?\n|•.*?\$)""".toRegex()
        var lastIndex = 0
        val matches = regex.findAll(text)

        for (match in matches) {
            val range = match.range
            val matchVal = match.value

            // Append anything before this matched markdown element
            if (range.start > lastIndex) {
                append(text.substring(lastIndex, range.start))
            }

            // Formatting matching styles
            when {
                matchVal.startsWith("**") && matchVal.endsWith("**") && matchVal.length >= 4 -> {
                    val cleanText = matchVal.substring(2, matchVal.length - 2)
                    withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.SansSerif)) {
                        append(cleanText)
                    }
                }
                matchVal.startsWith("*") && matchVal.endsWith("*") && matchVal.length >= 2 -> {
                    val cleanText = matchVal.substring(1, matchVal.length - 1)
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(cleanText)
                    }
                }
                matchVal.startsWith("•") -> {
                    withStyle(style = SpanStyle(color = if (isUser) Color.White else primaryColor, fontWeight = FontWeight.Bold)) {
                        append(" • ")
                    }
                    if (matchVal.length > 1) {
                        append(matchVal.substring(1))
                    }
                }
                else -> {
                    append(matchVal)
                }
            }
            lastIndex = range.endInclusive + 1
        }

        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    } catch (e: Exception) {
        // Safe fallback to original text to prevent crashes
        append(text)
    }
}

@Composable
fun WebSurferTabContent(
    viewModel: com.example.viewmodel.AppViewModel,
    modifier: Modifier = Modifier
) {
    val surfUrl by viewModel.surfUrl.collectAsStateWithLifecycle()
    val surfContent by viewModel.surfContent.collectAsStateWithLifecycle()
    val isSurfing by viewModel.isSurfing.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val surfHistory by viewModel.surfHistory.collectAsStateWithLifecycle()

    var urlInput by remember { mutableStateOf("") }
    var searchInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Keep urlInput in sync with active URL changes
    LaunchedEffect(surfUrl) {
        if (surfUrl.isNotEmpty() && urlInput != surfUrl) {
            urlInput = surfUrl
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Hero Section Header
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Web Surfer Logo",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Aza Web Explorer",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Scrape, crawl, and analyze any website or perform search index queries.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Live Surfing Address Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Surf Website URL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = { Text("https://example.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "Website",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (isSurfing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        if (urlInput.isNotBlank()) {
                                            viewModel.runWebSurf(urlInput)
                                            keyboardController?.hide()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "Navigate to website",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                if (urlInput.isNotBlank()) {
                                    viewModel.runWebSurf(urlInput)
                                    keyboardController?.hide()
                                }
                            }
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Search Index Directory",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        placeholder = { Text("Latest crypto trends, tech news...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search icon",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (searchInput.isNotBlank()) {
                                        viewModel.runWebSearch(searchInput)
                                        keyboardController?.hide()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TravelExplore,
                                    contentDescription = "Search button",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (searchInput.isNotBlank()) {
                                    viewModel.runWebSearch(searchInput)
                                    keyboardController?.hide()
                                }
                            }
                        )
                    )
                }
            }
        }

        // Surf History Chips
        if (surfHistory.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Recent Explorations",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(surfHistory) { histUrl ->
                            val cleanLabel = histUrl
                                .removePrefix("https://")
                                .removePrefix("http://")
                                .removePrefix("www.")
                                .take(25)
                            InputChip(
                                selected = histUrl == surfUrl,
                                onClick = {
                                    viewModel.runWebSurf(histUrl)
                                },
                                label = { Text(cleanLabel, fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "History Item",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Live Web Content Scraper Console
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scraped Page Console",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isSurfing) Color.Green else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSurfing) "CRAWLING LIVE..." else "CONNECTED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 250.dp, max = 500.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            Text(
                                text = surfContent,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ValidationLabTabContent(viewModel: AppViewModel) {
    val experiments by viewModel.experiments.collectAsStateWithLifecycle()
    val allTrades by viewModel.allExperimentTrades.collectAsStateWithLifecycle()
    val selectedExpId by viewModel.selectedExperimentId.collectAsStateWithLifecycle()
    val selectedExpTrades by viewModel.selectedExperimentTrades.collectAsStateWithLifecycle()

    var isAutoSimulating by remember { mutableStateOf(false) }
    var sortCriterion by remember { mutableStateOf("Net Profit") }
    var showCreateForm by remember { mutableStateOf(false) }

    var selectedTradeForReview by remember { mutableStateOf<com.example.data.database.ExperimentTrade?>(null) }

    // Create Form state
    var expName by remember { mutableStateOf("") }
    var expDesc by remember { mutableStateOf("") }
    var expStrategy by remember { mutableStateOf("Trend Following") }
    var expMarket by remember { mutableStateOf("BTC") }
    var expTimeframe by remember { mutableStateOf("1h") }
    var expRisk by remember { mutableStateOf("Medium") }
    var expBalance by remember { mutableStateOf("10000") }

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    // Auto Simulation background tick
    if (isAutoSimulating) {
        LaunchedEffect(Unit) {
            while (isAutoSimulating) {
                viewModel.tickContinuousPaperTrading()
                delay(3000)
            }
        }
    }

    // Set selected ID to first experiment if null
    LaunchedEffect(experiments) {
        if (selectedExpId == null && experiments.isNotEmpty()) {
            viewModel.selectedExperimentId.value = experiments.first().id
        }
    }

    val selectedExp = experiments.find { it.id == selectedExpId }

    val sortedExperiments = remember(experiments, sortCriterion) {
        when (sortCriterion) {
            "Net Profit" -> experiments.sortedByDescending { it.netProfit }
            "Risk-adjusted" -> experiments.sortedByDescending { it.riskAdjustedReturn }
            "Win Rate" -> experiments.sortedByDescending { it.winRate }
            "Drawdown" -> experiments.sortedBy { it.maxDrawdown }
            "Stability" -> experiments.sortedByDescending { it.stabilityScore }
            else -> experiments
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
    ) {
        // --- 1. CONTINUOUS EVALUATION HEADER PANEL ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = "Lab flask icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Aza Validation Lab",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Continuous paper-trading quality controls measuring real performance against theoretical benchmarks.",
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // --- 2. CONTROLS BAR (MANUAL TRIGGER & AUTO ROTATE) ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.tickContinuousPaperTrading() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Simulate Trade", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { isAutoSimulating = !isAutoSimulating },
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isAutoSimulating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isAutoSimulating) Icons.Default.Close else Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isAutoSimulating) "Stop Auto (Loop)" else "Auto-Simulate", fontSize = 11.sp)
                    }
                }
            }
        }

        // --- 3. DYNAMIC AI SUGGESTIONS SECTION ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Insights & Strategy Calibration", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Continuous Feedback", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = viewModel.generateImprovementSuggestions(),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- 4. EXPERIMENT SELECTOR & COMPARATIVE RANKING ---
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Experiment Comparative Arena", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    IconButton(onClick = { showCreateForm = !showCreateForm }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Experiment", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text("Sorting and comparative evaluation ranking based on rigorous telemetry:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable Chip row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Net Profit", "Risk-adjusted", "Win Rate", "Drawdown", "Stability").forEach { criterion ->
                        FilterChip(
                            selected = sortCriterion == criterion,
                            onClick = { sortCriterion = criterion },
                            label = { Text(criterion, fontSize = 10.sp) }
                        )
                    }
                }
            }
        }

        // Create form inline if open
        if (showCreateForm) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Create Validation Experiment", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        
                        OutlinedTextField(
                            value = expName,
                            onValueChange = { expName = it },
                            label = { Text("Experiment Name", fontSize = 11.sp) },
                            textStyle = TextStyle(fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = expDesc,
                            onValueChange = { expDesc = it },
                            label = { Text("Description", fontSize = 11.sp) },
                            textStyle = TextStyle(fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = expStrategy,
                                onValueChange = { expStrategy = it },
                                label = { Text("Strategy", fontSize = 10.sp) },
                                textStyle = TextStyle(fontSize = 11.sp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = expMarket,
                                onValueChange = { expMarket = it },
                                label = { Text("Market (BTC/ETH)", fontSize = 10.sp) },
                                textStyle = TextStyle(fontSize = 11.sp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = expTimeframe,
                                onValueChange = { expTimeframe = it },
                                label = { Text("Timeframe", fontSize = 10.sp) },
                                textStyle = TextStyle(fontSize = 11.sp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = expRisk,
                                onValueChange = { expRisk = it },
                                label = { Text("Risk (Low/Medium)", fontSize = 10.sp) },
                                textStyle = TextStyle(fontSize = 11.sp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Button(
                            onClick = {
                                if (expName.isNotEmpty()) {
                                    viewModel.createNewExperiment(
                                        name = expName,
                                        description = expDesc,
                                        strategyName = expStrategy,
                                        market = expMarket,
                                        timeframe = expTimeframe,
                                        riskLevel = expRisk,
                                        initialBalance = expBalance.toDoubleOrNull() ?: 10000.0
                                    )
                                    // Reset fields
                                    expName = ""
                                    expDesc = ""
                                    showCreateForm = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Initialize Experiment", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // List experiments sorted
        items(sortedExperiments.size) { index ->
            val exp = sortedExperiments[index]
            val isSelected = exp.id == selectedExpId
            
            val borderModifier = if (isSelected) {
                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            } else {
                Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            }

            Card(
                onClick = { viewModel.selectedExperimentId.value = exp.id },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(borderModifier),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "#${index + 1} ${exp.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(exp.riskLevel, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(exp.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Net Profit: $${String.format(Locale.US, "%.2f", exp.netProfit)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (exp.netProfit >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        Text("Win Rate: ${String.format(Locale.US, "%.1f", exp.winRate)}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Stability: ${String.format(Locale.US, "%.0f", exp.stabilityScore)}/100", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // --- 5. PERFORMANCE DASHBOARD CARD (FOR SELECTED EXPERIMENT) ---
        if (selectedExp != null) {
            item {
                Text("Performance Dashboard: ${selectedExp.name}", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Column 1
                            Column(modifier = Modifier.weight(1f)) {
                                Text("DAILY RETURN", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${String.format(Locale.US, "%+.2f", selectedExp.dailyReturn)}%", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (selectedExp.dailyReturn >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                            // Column 2
                            Column(modifier = Modifier.weight(1f)) {
                                Text("WEEKLY RETURN", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${String.format(Locale.US, "%+.2f", selectedExp.weeklyReturn)}%", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (selectedExp.weeklyReturn >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                            // Column 3
                            Column(modifier = Modifier.weight(1f)) {
                                Text("MONTHLY RETURN", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${String.format(Locale.US, "%+.2f", selectedExp.monthlyReturn)}%", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (selectedExp.monthlyReturn >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("SHARPE RATIO", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format(Locale.US, "%.2f", selectedExp.sharpeRatio), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("PROFIT FACTOR", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format(Locale.US, "%.2f", selectedExp.profitFactor), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("MAX DRAWDOWN", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${String.format(Locale.US, "%.1f", selectedExp.maxDrawdown)}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("AVG DURATION", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${String.format(Locale.US, "%.0f", selectedExp.averageTradeDuration)} min", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("TOTAL FEES", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$${String.format(Locale.US, "%.2f", selectedExp.totalSimulatedFees)}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("TOTAL SLIPPAGE", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$${String.format(Locale.US, "%.2f", selectedExp.totalSimulatedSlippage)}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // --- 6. DECISION REVIEW & TRADE DETAIL CARDS ---
            item {
                Text("Decision Review & Historic Logs", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }

            if (selectedExpTrades.isEmpty()) {
                item {
                    Text("No historic trades recorded for this experiment. Click 'Simulate Trade' to generate validation records.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(selectedExpTrades.size) { idx ->
                    val trade = selectedExpTrades[idx]
                    val isReviewed = selectedTradeForReview?.id == trade.id

                    Card(
                        onClick = { selectedTradeForReview = if (isReviewed) null else trade },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (trade.profitLoss >= 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = trade.direction,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (trade.profitLoss >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${trade.symbol} trade", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(java.util.Date(trade.timestamp)), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = String.format(Locale.US, "%+,.2f USD", trade.profitLoss),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    color = if (trade.profitLoss >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }

                            // Show detailed review when clicked
                            if (isReviewed) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(modifier = Modifier.height(12.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("MARKET ENVIRONMENT SNAPSHOT", fontWeight = FontWeight.Bold, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                                    Text(trade.marketSnapshot, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text("ENTRY PRICE", fontWeight = FontWeight.Bold, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$${String.format(Locale.US, "%,.2f", trade.entryPrice)}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Column {
                                            Text("EXIT PRICE", fontWeight = FontWeight.Bold, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$${String.format(Locale.US, "%,.2f", trade.exitPrice)}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Column {
                                            Text("CONFIDENCE", fontWeight = FontWeight.Bold, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${String.format(Locale.US, "%.0f", trade.confidence * 100.0)}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("WHY OPENED", fontWeight = FontWeight.Bold, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                                    Text(trade.entryReason, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("WHY CLOSED", fontWeight = FontWeight.Bold, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                                    Text(trade.exitReason, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("WHAT COULD HAVE BEEN DONE DIFFERENTLY?", fontWeight = FontWeight.Bold, fontSize = 9.sp, color = MaterialTheme.colorScheme.secondary)
                                    Text(trade.alternativeAction, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            val reportContent = """
                                                # AI COLLABORATION DECISION REVIEW
                                                **Experiment**: ${selectedExp.name}
                                                **Asset**: ${trade.symbol} (${selectedExp.strategyName})
                                                **Direction**: ${trade.direction} | **Net PnL**: $${String.format(Locale.US, "%.2f", trade.profitLoss)} USD
                                                
                                                ## 1. Trade Execution Audit
                                                - **Entry Price**: $${String.format(Locale.US, "%,.2f", trade.entryPrice)}
                                                - **Exit Price**: $${String.format(Locale.US, "%,.2f", trade.exitPrice)}
                                                - **Position Size**: ${trade.quantity} units
                                                - **Latency**: 120 ms
                                                - **Simulated Fees**: $${String.format(Locale.US, "%,.2f", trade.fees)}
                                                - **Simulated Slippage**: $${String.format(Locale.US, "%,.2f", trade.slippage)}
                                                - **Duration**: ${trade.durationMin} minutes
                                                - **Confidence Score**: ${String.format(Locale.US, "%.1f", trade.confidence * 100.0)}%
                                                
                                                ## 2. Market Environment Snapshot
                                                ${trade.marketSnapshot}
                                                
                                                ## 3. Retrospective Analysis
                                                - **Why Opened**: ${trade.entryReason}
                                                - **Why Closed**: ${trade.exitReason}
                                                - **Retrospective Recommendation**: ${trade.alternativeAction}
                                            """.trimIndent()
                                            
                                            val newReport = com.example.data.database.ExportedReportHistory(
                                                reportType = "DECISION_REVIEW",
                                                strategyName = selectedExp.strategyName,
                                                market = trade.symbol,
                                                exportTimestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date()),
                                                formattedContent = reportContent
                                            )
                                            viewModel.insertExportedReport(newReport)
                                            viewModel.addThought("📋 AI COLLABORATION: Exported trade decision review for ${trade.symbol} to Collaboration hub!")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Export Report to AI Collab", fontSize = 11.sp)
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
fun KnowledgeHubTabContent(viewModel: AppViewModel) {
    val knowledgeEntries by viewModel.knowledgeEntries.collectAsStateWithLifecycle()
    val skillLibrary by viewModel.skillLibrary.collectAsStateWithLifecycle()
    val conflicts by viewModel.activeTradingConflicts.collectAsStateWithLifecycle()
    val influencedKnowledgeTitle by viewModel.influencedKnowledgeTitle.collectAsStateWithLifecycle()

    var hubSubTab by remember { mutableStateOf(0) } // 0: Knowledge Base, 1: Skill Library
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var showCreateForm by remember { mutableStateOf(false) }

    // Manual Creation States
    var newTitle by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("Strategy") }
    var newDescription by remember { mutableStateOf("") }
    var newEvidence by remember { mutableStateOf("") }
    var newConfidence by remember { mutableStateOf("0.80") }

    var selectedKnowledgeForDetail by remember { mutableStateOf<com.example.data.database.KnowledgeEntry?>(null) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    val filteredLessons = remember(knowledgeEntries, searchQuery, selectedCategoryFilter) {
        knowledgeEntries.filter { item ->
            val matchesQuery = item.title.contains(searchQuery, ignoreCase = true) || item.description.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategoryFilter == "All" || item.category.equals(selectedCategoryFilter, ignoreCase = true)
            matchesQuery && matchesCategory
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
    ) {
        // --- 1. Header Banner ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = "Knowledge icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Aza Knowledge Base & Skill Learning Hub",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Synthesizing safe continuous trading experience into persistent, explanatory rules and calibrating specialized skills.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // --- 2. Real-Time Active Conflicts & Influenced Lesson Banner ---
        if (conflicts.isNotEmpty() || influencedKnowledgeTitle != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (influencedKnowledgeTitle != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = "Influence Info",
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "💡 DECISION INFLUENCED BY LESSON:\n\"$influencedKnowledgeTitle\"",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }

                    conflicts.forEach { conflict ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Conflict Warning",
                                    tint = Color(0xFFC62828),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "⚠️ ACTIVE SAFETY CONFLICT DETECTED",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFC62828),
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = conflict,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFB71C1C)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 3. Sub-Tab Switcher (Knowledge Explorer vs. Skill Library) ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { hubSubTab = 0 },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hubSubTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Knowledge Base", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { hubSubTab = 1 },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hubSubTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Skill Library", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- 4. Sub-Tab Rendering ---
        if (hubSubTab == 0) {
            // --- KNOWLEDGE BASE TAB ---
            // statistics cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val totalLessons = knowledgeEntries.size
                    val totalConfirms = knowledgeEntries.sumOf { it.timesConfirmed }
                    
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Total Lessons", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$totalLessons", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Confirmed Sessions", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$totalConfirms times", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4CAF50))
                        }
                    }
                }
            }

            // Filters & Actions Row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search lessons...", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { showCreateForm = !showCreateForm },
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = if (showCreateForm) Icons.Default.Close else Icons.Default.Add,
                                contentDescription = "Add Custom Lesson",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Category scroll row
                    val categories = listOf("All", "Strategy", "Risk", "Regime", "Pattern", "User Feedback")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSel = selectedCategoryFilter == cat
                            AssistChip(
                                onClick = { selectedCategoryFilter = cat },
                                label = { Text(cat, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    labelColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }

            // Create Lesson form inside Base list
            if (showCreateForm) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Formulate Custom Lesson Entry", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            
                            OutlinedTextField(
                                value = newTitle,
                                onValueChange = { newTitle = it },
                                label = { Text("Lesson Title", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            // Category selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Category:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                listOf("Strategy", "Risk", "Regime", "Pattern", "User Feedback").forEach { cat ->
                                    val isSel = newCategory == cat
                                    AssistChip(
                                        onClick = { newCategory = cat },
                                        label = { Text(cat, fontSize = 9.sp) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent
                                        )
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = newDescription,
                                onValueChange = { newDescription = it },
                                label = { Text("Core Description", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = newEvidence,
                                onValueChange = { newEvidence = it },
                                label = { Text("Evidence/Backtest details", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = newConfidence,
                                onValueChange = { newConfidence = it },
                                label = { Text("Confidence Score (0.0 - 1.0)", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Button(
                                onClick = {
                                    if (newTitle.isNotBlank() && newDescription.isNotBlank()) {
                                        val confVal = newConfidence.toDoubleOrNull() ?: 0.8
                                        viewModel.createNewKnowledgeEntry(
                                            title = newTitle,
                                            category = newCategory,
                                            description = newDescription,
                                            evidence = newEvidence,
                                            confidenceScore = confVal
                                        )
                                        // Clear form
                                        newTitle = ""
                                        newDescription = ""
                                        newEvidence = ""
                                        showCreateForm = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Commit Lesson to persistent KB", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Knowledge List
            if (filteredLessons.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No lessons found matching active filters.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(filteredLessons) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedKnowledgeForDetail = entry },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val catBgColor = when (entry.category.uppercase()) {
                                    "RISK" -> Color(0xFFFFEBEE)
                                    "REGIME" -> Color(0xFFE3F2FD)
                                    "STRATEGY" -> Color(0xFFEDE7F6)
                                    else -> Color(0xFFF5F5F5)
                                }
                                val catTextColor = when (entry.category.uppercase()) {
                                    "RISK" -> Color(0xFFC62828)
                                    "REGIME" -> Color(0xFF1565C0)
                                    "STRATEGY" -> Color(0xFF5E35B1)
                                    else -> Color(0xFF616161)
                                }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = catBgColor),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = entry.category.uppercase(),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = catTextColor
                                    )
                                }

                                Text(
                                    text = "Confidence: ${String.format(Locale.US, "%.1f", entry.confidenceScore * 100.0)}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(entry.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(entry.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔄 Confirmed: ${entry.timesConfirmed} times",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF4CAF50)
                                )
                                Text(
                                    text = "Inspect evidence ↗",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // --- SKILL LIBRARY TAB ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "🛡️ Safety First: Skill calibration is automated based on real trading data from the Validation Lab. Confidence evolves slowly across many trades to prevent over-optimization.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (skillLibrary.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Initializing Skill Library...", fontSize = 11.sp)
                        }
                    }
                }
            } else {
                items(skillLibrary) { skill ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(skill.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Regimes: ${skill.associatedRegimes}",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(skill.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Success rate bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Success Rate: ${String.format(Locale.US, "%.1f", skill.successRate)}%", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("Confidence: ${String.format(Locale.US, "%.2f", skill.confidence)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (skill.successRate / 100.0).toFloat() },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = if (skill.successRate > 60.0) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Real-time Calibration trigger button for user simulation
                            Button(
                                onClick = {
                                    // Trigger rapid validation run
                                    val isWin = java.util.Random().nextDouble() > (1.0 - (skill.successRate / 100.0))
                                    val newSuccess = if (isWin) {
                                        ((skill.successRate * 9.0) + 100.0) / 10.0
                                    } else {
                                        (skill.successRate * 9.0) / 10.0
                                    }
                                    val newConf = if (isWin) {
                                        (skill.confidence + 0.01).coerceAtMost(1.0)
                                    } else {
                                        (skill.confidence - 0.02).coerceAtLeast(0.1)
                                    }
                                    viewModel.updateSkillEntry(
                                        skill.copy(
                                            successRate = newSuccess,
                                            failureRate = 100.0 - newSuccess,
                                            confidence = newConf,
                                            lastUpdated = System.currentTimeMillis()
                                        )
                                    )
                                    viewModel.addThought("🎯 SKILL VALIDATION: Simulated calibration run for ${skill.name}. Outcome: ${if (isWin) "SUCCESS" else "FAILURE"}")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Trigger Validation Calibration", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Detail & Export Dialog ---
    selectedKnowledgeForDetail?.let { entry ->
        AlertDialog(
            onDismissRequest = { selectedKnowledgeForDetail = null },
            title = { Text(entry.title, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("CATEGORY: ${entry.category.uppercase()}", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                    Text("DESCRIPTION:\n${entry.description}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(8.dp)
                    ) {
                        Column {
                            Text("EVIDENCE LOG & PROOFS:", fontWeight = FontWeight.Bold, fontSize = 9.sp, color = MaterialTheme.colorScheme.secondary)
                            Text(entry.evidence, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Times Confirmed: ${entry.timesConfirmed}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Confidence: ${String.format(Locale.US, "%.1f", entry.confidenceScore * 100.0)}%", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val shareContent = """
                            # AZA KNOWLEDGE EXPORT
                            **Title**: ${entry.title}
                            **Category**: ${entry.category}
                            **Confidence**: ${String.format(Locale.US, "%.1f", entry.confidenceScore * 100.0)}%
                            **Confirmed Times**: ${entry.timesConfirmed}
                            
                            ## Description
                            ${entry.description}
                            
                            ## Evidence & Background
                            ${entry.evidence}
                        """.trimIndent()
                        
                        // Copy to clipboard
                        clipboardManager.setText(AnnotatedString(shareContent))
                        viewModel.addThought("🧠 KNOWLEDGE EXPLORER: Copied lesson '${entry.title}' to clipboard!")
                        selectedKnowledgeForDetail = null
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export & Copy", fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedKnowledgeForDetail = null }) {
                    Text("Close", fontSize = 11.sp)
                }
            }
        )
    }
}
