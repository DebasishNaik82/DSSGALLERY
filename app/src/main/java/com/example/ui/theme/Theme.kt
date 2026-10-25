package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = AppPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = AppPrimaryContainerLight,
    onPrimaryContainer = AppOnPrimaryContainerLight,
    secondary = PorcelainSecondaryTextLight,
    onSecondary = Color.White,
    secondaryContainer = AppSecondaryContainerLight,
    onSecondaryContainer = AppOnSecondaryContainerLight,
    background = PorcelainBackgroundLight,
    onBackground = PorcelainPrimaryTextLight,
    surface = PorcelainBackgroundLight,
    onSurface = PorcelainPrimaryTextLight,
    surfaceVariant = PorcelainSurfaceContainerLight,
    onSurfaceVariant = PorcelainSecondaryTextLight,
    surfaceContainerLowest = PorcelainSurfaceLight,
    surfaceContainerLow = PorcelainSurfaceLowLight,
    surfaceContainer = PorcelainSurfaceContainerLight,
    surfaceContainerHigh = PorcelainSurfaceHighLight,
    surfaceContainerHighest = PorcelainSurfaceHighestLight,
    outline = PorcelainOutlineLight,
    outlineVariant = PorcelainDividerLight,
    error = ErrorLight,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = AppPrimaryDark,
    onPrimary = Color(0xFF1D1D1D),
    primaryContainer = AppPrimaryContainerDark,
    onPrimaryContainer = AppOnPrimaryContainerDark,
    secondary = PorcelainSecondaryTextDark,
    onSecondary = Color(0xFF1D1D1D),
    secondaryContainer = AppSecondaryContainerDark,
    onSecondaryContainer = AppOnSecondaryContainerDark,
    background = PorcelainBackgroundDark,
    onBackground = PorcelainPrimaryTextDark,
    surface = PorcelainBackgroundDark,
    onSurface = PorcelainPrimaryTextDark,
    surfaceVariant = PorcelainSurfaceContainerDark,
    onSurfaceVariant = PorcelainSecondaryTextDark,
    surfaceContainerLowest = PorcelainSurfaceDark,
    surfaceContainerLow = PorcelainSurfaceLowDark,
    surfaceContainer = PorcelainSurfaceContainerDark,
    surfaceContainerHigh = PorcelainSurfaceHighDark,
    surfaceContainerHighest = PorcelainSurfaceHighestDark,
    outline = PorcelainOutlineDark,
    outlineVariant = PorcelainDividerDark,
    error = ErrorDark,
    onError = Color(0xFF690005)
)

val GalleryShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    forceDarkStatusBar: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                val useLightIcons = darkTheme || forceDarkStatusBar
                controller.isAppearanceLightStatusBars = !useLightIcons
                controller.isAppearanceLightNavigationBars = !useLightIcons
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = GalleryShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    GalleryTheme(darkTheme = darkTheme, content = content)
}
