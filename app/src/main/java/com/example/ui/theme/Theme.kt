package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val LightColors = lightColorScheme(
    primary = TawasulTeal,
    onPrimary = Color.White,
    primaryContainer = TawasulDarkGreen,
    onPrimaryContainer = Color.White,
    secondary = TawasulLightGreen,
    onSecondary = Color.White,
    secondaryContainer = TawasulBubbleOutgoing,
    onSecondaryContainer = TawasulDarkGreen,
    background = TawasulBackground,
    onBackground = TawasulTextPrimary,
    surface = Color.White,
    onSurface = TawasulTextPrimary,
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = TawasulTextSecondary,
    error = TawasulDanger,
    onError = Color.White,
    outline = Color(0xFFE0E0E0)
)

private val DarkColors = darkColorScheme(
    primary = TawasulLightGreen,
    onPrimary = TawasulDarkBg,
    primaryContainer = TawasulDarkSurface,
    onPrimaryContainer = Color.White,
    secondary = TawasulTeal,
    onSecondary = Color.White,
    secondaryContainer = TawasulDarkBubbleOutgoing,
    onSecondaryContainer = Color.White,
    background = TawasulDarkBg,
    onBackground = TawasulDarkTextPrimary,
    surface = TawasulDarkSurface,
    onSurface = TawasulDarkTextPrimary,
    surfaceVariant = TawasulDarkCard,
    onSurfaceVariant = TawasulDarkTextSecondary,
    error = Color(0xFFEF5350),
    onError = Color.White,
    outline = Color(0xFF2A3942)
)

@Composable
fun TawasulTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
