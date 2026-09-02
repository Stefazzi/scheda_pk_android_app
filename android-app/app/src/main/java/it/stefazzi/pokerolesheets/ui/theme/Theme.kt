package it.stefazzi.pokerolesheets.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PokeroleColors = darkColorScheme(
    primary = Color(0xFFE75B54),
    onPrimary = Color.White,
    secondary = Color(0xFF64B5F6),
    background = Color(0xFF10151D),
    surface = Color(0xFF18212D),
    surfaceVariant = Color(0xFF243140),
)

@Composable
fun PokeroleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PokeroleColors,
        content = content,
    )
}

