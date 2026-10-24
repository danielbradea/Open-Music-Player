package com.bid90.omp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

val MusicBackground = Brush.linearGradient(listOf(
    Color(0xFF29243E), Color(0xFF29265C), Color(0xFF571D60), Color(0xFF29243E)))

private val MusicColors = darkColorScheme(
    primary = Color(0xFFE8A4FF), onPrimary = Color(0xFF351341),
    primaryContainer = Color(0xFF513469), onPrimaryContainer = Color(0xFFF9F6FF),
    secondaryContainer = Color(0xFF443053).copy(alpha = 0.75f), onSecondaryContainer = Color(0xFFF9F6FF),
    background = Color(0xFF29243E), onBackground = Color(0xFFF9F6FF),
    surface = Color(0xFF302640), onSurface = Color(0xFFF9F6FF),
    surfaceContainerLow = Color(0xFF382C4D).copy(alpha = 0.7f),
    onSurfaceVariant = Color(0xFFCBBFD8), outline = Color(0xFF9583AA)
)

@Composable
fun OpenMusicPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MusicColors, typography = Typography, content = content)
}
