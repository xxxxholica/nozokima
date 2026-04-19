package ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NotionSafeGreen,
    onPrimary = Color.White,
    background = NotionDarkBackground,
    onBackground = NotionDarkTextPrimary,
    surface = NotionDarkSurface,
    onSurface = NotionDarkTextPrimary,
    outline = NotionDarkBorder,
    surfaceVariant = NotionDarkSurface,
    onSurfaceVariant = NotionDarkTextSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = NotionSafeGreen,
    onPrimary = Color.White,
    background = NotionBackground,
    onBackground = NotionTextPrimary,
    surface = NotionWhite,
    onSurface = NotionTextPrimary,
    outline = NotionBorder,
    surfaceVariant = Color.White,
    onSurfaceVariant = NotionTextSecondary
)

@Composable
fun NozokimaTheme(
    themeMode: String = "SYSTEM",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
