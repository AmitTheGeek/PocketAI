package com.pocketai.offline.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PocketAiLightColors = lightColorScheme(
    primary = Color(0xFF1C6B4A),
    onPrimary = Color.White,
    secondary = Color(0xFF315F80),
    onSecondary = Color.White,
    background = Color(0xFFF7F8F3),
    onBackground = Color(0xFF17211B),
    surface = Color(0xFFF7F8F3),
    onSurface = Color(0xFF17211B),
    surfaceVariant = Color(0xFFE2E8DF),
    onSurfaceVariant = Color(0xFF414B44),
    error = Color(0xFF9B2C1D),
    onError = Color.White,
)

private val PocketAiDarkColors = darkColorScheme(
    primary = Color(0xFF87D7AE),
    onPrimary = Color(0xFF003922),
    secondary = Color(0xFF9CCAE6),
    onSecondary = Color(0xFF00364F),
    background = Color(0xFF101510),
    onBackground = Color(0xFFE0E5DD),
    surface = Color(0xFF101510),
    onSurface = Color(0xFFE0E5DD),
    surfaceVariant = Color(0xFF344239),
    onSurfaceVariant = Color(0xFFC4CFC6),
    error = Color(0xFFFFB4A8),
    onError = Color(0xFF5F150C),
)

@Composable
internal fun PocketAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PocketAiDarkColors else PocketAiLightColors,
        content = content,
    )
}
