package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
    darkColorScheme(
        primary = GreenAccent,
        onPrimary = GreenAccentDark,
        secondary = GreenSecondaryContainer,
        background = DarkBackground,
        surface = DarkSurface,
        onBackground = DarkTextPrimary,
        onSurface = DarkTextPrimary,
        outline = MinimalBorder
    )

private val LightColorScheme =
    lightColorScheme(
        primary = GreenPrimary,
        onPrimary = MinimalSurface,
        primaryContainer = GreenAccent,
        onPrimaryContainer = GreenAccentDark,
        secondary = GreenSecondaryContainer,
        onSecondary = GreenPrimary,
        background = MinimalBackground,
        surface = MinimalSurface,
        onBackground = MinimalTextPrimary,
        onSurface = MinimalTextPrimary,
        outline = MinimalBorder
    )

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
