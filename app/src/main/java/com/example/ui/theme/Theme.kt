package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LuminaDarkColorScheme = darkColorScheme(
    primary = NeonGreen,                  // Vibrant Neon Green Primary
    secondary = NeonCyan,                 // Electric Neon Cyan Accent
    tertiary = NeonBlueAccent,            // Neon Sky Blue Accent
    background = NavyBackground,          // Deep Charcoal / Navy Background
    surface = CharcoalSurface,            // Charcoal Navy Card Surface
    surfaceVariant = DarkNavySurfaceVariant, // Elevated Surface Variant
    onPrimary = Color(0xFF00210B),        // High Contrast Text on Neon Green
    onSecondary = Color(0xFF002029),      // High Contrast Text on Neon Cyan
    onTertiary = Color(0xFF001A2C),
    onBackground = TextPrimary,           // Crisp White Body Text
    onSurface = TextPrimary,              // Crisp White Card Text
    onSurfaceVariant = TextSecondary,     // Muted Slate Labels & Icons
    outline = CyberBorder,                // Cybernetic Blue Outline
    outlineVariant = Color(0xFF0F2744)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LuminaDarkColorScheme,
        typography = Typography,
        content = content
    )
}


