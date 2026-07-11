package com.laxmi.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Bahi-khata / Laxmi visual language: warm paper grounds, a deep vermillion-maroon
 * primary (the traditional ledger red), emerald for money coming in, saffron-gold
 * accent. Chosen, not defaulted — this is a money app for Indian shopkeepers.
 */

// Brand — semantic money colors, tuned to read on both paper and dark grounds.
data class LaxmiColors(
    val getGreen: Color,   // aana hai (owed to me)
    val oweRed: Color,     // dena hai (I owe)
    val gold: Color,       // accent / highlights
    val paper: Color,      // subtle card tint
    val ink: Color,
    val muted: Color,
)

private val LightExtras = LaxmiColors(
    getGreen = Color(0xFF1E7A4D),
    oweRed = Color(0xFFB23A2A),
    gold = Color(0xFFCB8A2E),
    paper = Color(0xFFFFFDF8),
    ink = Color(0xFF2A2118),
    muted = Color(0xFF8A7E6E),
)
private val DarkExtras = LaxmiColors(
    getGreen = Color(0xFF5FC48C),
    oweRed = Color(0xFFE0806F),
    gold = Color(0xFFE6B35C),
    paper = Color(0xFF242019),
    ink = Color(0xFFF0E9DC),
    muted = Color(0xFFA79B88),
)

val LocalLaxmiColors = staticCompositionLocalOf { LightExtras }

@Composable
fun laxmi(): LaxmiColors = LocalLaxmiColors.current

private val LightColors = lightColorScheme(
    primary = Color(0xFF9E2F23),
    onPrimary = Color(0xFFFFF6F0),
    secondary = Color(0xFF1E7A4D),
    background = Color(0xFFF7F1E7),
    onBackground = Color(0xFF2A2118),
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF2A2118),
    surfaceVariant = Color(0xFFEFE7D8),
    onSurfaceVariant = Color(0xFF6F6455),
    outline = Color(0xFFD9CFBd),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE0806F),
    onPrimary = Color(0xFF2A0F0A),
    secondary = Color(0xFF5FC48C),
    background = Color(0xFF191510),
    onBackground = Color(0xFFF0E9DC),
    surface = Color(0xFF242019),
    onSurface = Color(0xFFF0E9DC),
    surfaceVariant = Color(0xFF352F25),
    onSurfaceVariant = Color(0xFFC5B9A6),
    outline = Color(0xFF4A4235),
)

private val LaxmiType = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun LaxmiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLaxmiColors provides if (darkTheme) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = LaxmiType,
            content = content,
        )
    }
}
