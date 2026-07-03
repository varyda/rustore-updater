package ru.app.rustoreupdater.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// RuStore-ish blue brand palette.
private val Blue = Color(0xFF0A5CE6)
private val BlueDark = Color(0xFF003B9C)
private val BlueLight = Color(0xFF6E8EFF)

private val DarkColors = darkColorScheme(
    primary = BlueLight,
    onPrimary = Color.Black,
    primaryContainer = BlueDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFB8C5FF),
)
private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF565E71),
)

@Composable
fun RuStoreUpdaterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
