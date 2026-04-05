package de.keiss.selfhealing.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFD32F2F),       // DB-Rot
    onPrimary = Color.White,
    secondary = Color(0xFF1976D2),
    surface = Color.White,
    background = Color(0xFFF5F5F5),
    error = Color(0xFFB00020)
)

@Composable
fun ZugverbindungTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
