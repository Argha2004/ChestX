package com.medical.chestxray.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * iOS-flavoured palette: neutral system grays for surfaces, a single calm health accent,
 * and Apple's system semantic colors (green / orange / red) for status. Grouped-inset look —
 * light gray background with white cards and hairline separators.
 */

// ── Brand accent (health cyan/teal) ──
val AccentCyan = Color(0xFF0A7CB0)          // primary tint (light) — white text ≥4.5:1 (AA)
val AccentCyanDark = Color(0xFF40C8E8)      // brighter tint for dark mode
val AccentEmerald = Color(0xFF059669)       // secondary (health green)
val AccentEmeraldDark = Color(0xFF30D158)

val CyanTintLight = Color(0xFFE3F4FA)       // primaryContainer (light)
val CyanTintDark = Color(0xFF0C3B49)        // primaryContainer (dark)

// ── iOS system semantic colors ──
val SuccessGreen = Color(0xFF34C759)        // systemGreen
val SuccessGreenDark = Color(0xFF30D158)
val WarningAmber = Color(0xFFFF9500)        // systemOrange
val ErrorRed = Color(0xFFFF3B30)            // systemRed (light)
val ErrorRedLight = Color(0xFFFF453A)       // systemRed (dark)

// ── Light neutrals (iOS grouped) ──
val LightBg = Color(0xFFF2F2F7)             // systemGroupedBackground
val LightSurface = Color(0xFFFFFFFF)        // elevated card
val LightSurfaceVariant = Color(0xFFEFEFF4) // grouped secondary fill
val LightBorder = Color(0xFFD1D1D6)         // separator (opaque)
val TextDark = Color(0xFF1C1C1E)            // label
val TextMuted = Color(0xFF6E6E73)           // secondaryLabel

// ── Dark neutrals (iOS grouped dark) ──
val DarkBg = Color(0xFF000000)              // grouped background
val DarkSurface = Color(0xFF1C1C1E)         // elevated card
val DarkSurfaceVariant = Color(0xFF2C2C2E)  // grouped secondary fill
val DarkBorder = Color(0xFF38383A)          // separator
val TextLight = Color(0xFFFFFFFF)           // label
val TextLightMuted = Color(0xFF98989F)      // secondaryLabel
