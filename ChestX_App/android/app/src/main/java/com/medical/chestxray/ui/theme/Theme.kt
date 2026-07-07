package com.medical.chestxray.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = AccentCyan,
    onPrimary = Color.White,
    primaryContainer = CyanTintLight,
    onPrimaryContainer = Color(0xFF06455F),

    secondary = AccentEmerald,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCF5EC),
    onSecondaryContainer = Color(0xFF044D38),

    tertiary = AccentCyan,
    onTertiary = Color.White,
    tertiaryContainer = CyanTintLight,
    onTertiaryContainer = Color(0xFF06455F),

    background = LightBg,
    onBackground = TextDark,

    surface = LightSurface,
    onSurface = TextDark,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextMuted,

    outline = LightBorder,
    outlineVariant = LightBorder.copy(alpha = 0.5f),

    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRed.copy(alpha = 0.12f),
    onErrorContainer = ErrorRed,

    inverseSurface = TextDark,
    inverseOnSurface = LightBg,
    inversePrimary = AccentCyanDark,

    scrim = Color.Black.copy(alpha = 0.45f),
)

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyanDark,
    onPrimary = Color(0xFF00212E),
    primaryContainer = CyanTintDark,
    onPrimaryContainer = Color(0xFFB8ECFB),

    secondary = AccentEmeraldDark,
    onSecondary = Color(0xFF002114),
    secondaryContainer = Color(0xFF0B3D2C),
    onSecondaryContainer = Color(0xFFB9F5D8),

    tertiary = AccentCyanDark,
    onTertiary = Color(0xFF00212E),
    tertiaryContainer = CyanTintDark,
    onTertiaryContainer = Color(0xFFB8ECFB),

    background = DarkBg,
    onBackground = TextLight,

    surface = DarkSurface,
    onSurface = TextLight,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextLightMuted,

    outline = DarkBorder,
    outlineVariant = DarkBorder.copy(alpha = 0.6f),

    error = ErrorRedLight,
    onError = Color(0xFF3A0906),
    errorContainer = ErrorRedLight.copy(alpha = 0.16f),
    onErrorContainer = ErrorRedLight,

    inverseSurface = TextLight,
    inverseOnSurface = DarkBg,
    inversePrimary = AccentCyan,

    scrim = Color.Black.copy(alpha = 0.55f),
)

@Composable
fun ChestXRayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
