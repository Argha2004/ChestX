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
    primary = MedicalBlue,
    onPrimary = Color.White,
    primaryContainer = MedicalBlueLight,
    onPrimaryContainer = MedicalBlueDark,

    secondary = ClinicalTeal,
    onSecondary = Color.White,
    secondaryContainer = ClinicalTealLight,
    onSecondaryContainer = ClinicalTealDark,

    tertiary = AccentCyan,
    onTertiary = Cyan900,
    tertiaryContainer = Cyan100,
    onTertiaryContainer = Cyan700,

    background = LightBg,
    onBackground = TextDark,

    surface = LightSurface,
    onSurface = TextDark,
    surfaceVariant = Cyan50,
    onSurfaceVariant = TextMuted,

    outline = LightBorder,
    outlineVariant = LightBorder.copy(alpha = 0.5f),

    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRed.copy(alpha = 0.12f),
    onErrorContainer = ErrorRed,

    inverseSurface = TextDark,
    inverseOnSurface = LightBg,
    inversePrimary = MedicalBlueDark,

    scrim = Color.Black.copy(alpha = 0.6f),
)

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = DarkBg,
    primaryContainer = MedicalBlueDark,
    onPrimaryContainer = MedicalBlueLight,

    secondary = Emerald400,
    onSecondary = DarkBg,
    secondaryContainer = ClinicalTealDark,
    onSecondaryContainer = ClinicalTealLight,

    tertiary = Cyan400,
    onTertiary = DarkBg,
    tertiaryContainer = Cyan700,
    onTertiaryContainer = Cyan100,

    background = DarkBg,
    onBackground = TextLight,

    surface = DarkSurface,
    onSurface = TextLight,
    surfaceVariant = DarkBg,
    onSurfaceVariant = TextLightMuted,

    outline = DarkBorder,
    outlineVariant = DarkBorder.copy(alpha = 0.5f),

    error = ErrorRedLight,
    onError = DarkBg,
    errorContainer = ErrorRedLight.copy(alpha = 0.15f),
    onErrorContainer = ErrorRedLight,

    inverseSurface = TextLight,
    inverseOnSurface = DarkBg,
    inversePrimary = MedicalBlue,

    scrim = Color.Black.copy(alpha = 0.6f),
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
            window.navigationBarColor = colorScheme.surface.toArgb()
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
