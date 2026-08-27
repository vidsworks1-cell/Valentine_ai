package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.data.database.AppDatabase
import com.example.data.repository.AppRepository
import com.example.ui.screens.AccountScreen
import com.example.ui.screens.AzaScreen
import com.example.ui.screens.ContentScreen
import com.example.ui.screens.GigScreen
import com.example.ui.screens.TradingScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.AppViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by lazy {
        val database = AppDatabase.getDatabase(applicationContext, lifecycleScope)
        val repository = AppRepository(database)
        ViewModelProvider(this, AppViewModelFactory(repository))[AppViewModel::class.java]
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.aza.security.SecureCredentialStore.init(applicationContext)
        com.example.aza.execution.SystemNotificationHelper.init(applicationContext)

        // Sync initial trading mode with the connector's safety lock
        try {
            val savedMode = com.example.aza.security.SecureCredentialStore.getBinanceTradingMode()
            com.example.aza.connector.BinanceConnector.setOrderPlacementEnabled(savedMode == "Autonomous Trading")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Request POST_NOTIFICATIONS permission dynamically on API 33+ (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val requestPermissionLauncher = registerForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { _ -> 
                try {
                    com.example.aza.execution.AzaBackgroundService.startService(applicationContext)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    "android.permission.POST_NOTIFICATIONS"
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
            } else {
                try {
                    com.example.aza.execution.AzaBackgroundService.startService(applicationContext)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            try {
                com.example.aza.execution.AzaBackgroundService.startService(applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                var selectedTab by remember { mutableIntStateOf(0) }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = when (selectedTab) {
                                        0 -> "Lumina AI Advisor"
                                        1 -> "Nexus Trading"
                                        2 -> "AI Content Studio"
                                        3 -> "Gig Freelance Broker"
                                        else -> "Secure Token Hub"
                                    },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            },
                            navigationIcon = {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Core AI",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = "Lumina AI Chat Tab"
                                    )
                                },
                                label = { Text("Lumina AI", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = Color(0xFF0F2744),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            )

                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.TrendingUp,
                                        contentDescription = "Trading Tab"
                                    )
                                },
                                label = { Text("Trading", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = Color(0xFF0F2744),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            )

                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.VideoCall,
                                        contentDescription = "Content Tab"
                                    )
                                },
                                label = { Text("Content", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = Color(0xFF0F2744),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            )

                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Work,
                                        contentDescription = "Freelance Tab"
                                    )
                                },
                                label = { Text("Gigs", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = Color(0xFF0F2744),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            )

                            NavigationBarItem(
                                selected = selectedTab == 4,
                                onClick = { selectedTab = 4 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = "Account Tab"
                                    )
                                },
                                label = { Text("Account", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = Color(0xFF0F2744),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    when (selectedTab) {
                        0 -> AzaScreen(viewModel = viewModel, innerPadding = innerPadding)
                        1 -> TradingScreen(viewModel = viewModel, innerPadding = innerPadding)
                        2 -> ContentScreen(viewModel = viewModel, innerPadding = innerPadding)
                        3 -> GigScreen(viewModel = viewModel, innerPadding = innerPadding)
                        else -> AccountScreen(viewModel = viewModel, innerPadding = innerPadding)
                    }
                }
            }
        }
    }
}
