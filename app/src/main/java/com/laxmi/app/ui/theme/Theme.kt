package com.laxmi.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LaxmiGreen = Color(0xFF2E6B45)
private val LaxmiRed = Color(0xFF9E2F23)

private val LightColors = lightColorScheme(
    primary = LaxmiRed,
    secondary = LaxmiGreen,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD46455),
    secondary = Color(0xFF7DBB96),
)

@Composable
fun LaxmiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
