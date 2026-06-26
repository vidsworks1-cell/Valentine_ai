package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GeometricLightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),       // Balanced Purple
    secondary = Color(0xFF7D5260),     // Soft Rose/Plum Accent
    tertiary = Color(0xFF21005D),      // Dark Violet Accent
    background = Color(0xFFFEF7FF),    // Warm Light Lavender Base
    surface = Color(0xFFFFFFFF),       // Clear White Card Base
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1D1B20),  // Deep Charcoal Body
    onSurface = Color(0xFF1D1B20),     // Safe Contrast Content
    surfaceVariant = Color(0xFFF3EDF7), // Light Soft Pastel Surface Variant
    onSurfaceVariant = Color(0xFF49454F), // Neutral Grey Label
    outline = Color(0xFFCAC4D0),       // M3 Gray Outlines
    outlineVariant = Color(0xFFEADDFF)  // Pale Purple Borders
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Preserve original requested colors exactly
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = GeometricLightColorScheme,
        typography = Typography,
        content = content
    )
}

