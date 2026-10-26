package com.sozvezdie.messenger.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = IndigoDark,
    secondary = Violet,
    background = LightBackground,
    onBackground = androidx.compose.ui.graphics.Color(0xFF1B1B26),
    surface = LightSurface,
    onSurface = androidx.compose.ui.graphics.Color(0xFF1B1B26),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF5B5B6B),
)

private val DarkColors = darkColorScheme(
    primary = SoftIndigo,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF1B1B26),
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFE4E4F5),
    secondary = androidx.compose.ui.graphics.Color(0xFFC4B5FD),
    background = DarkBackground,
    onBackground = androidx.compose.ui.graphics.Color(0xFFE8E8F2),
    surface = DarkSurface,
    onSurface = androidx.compose.ui.graphics.Color(0xFFE8E8F2),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB8B8CA),
)

/** Общая тема всех экранов. Тёмная включается вместе с системной. */
@Composable
fun MessengerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
