package com.medical.chestxray.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
// Clinical healthcare palette — calm cyan + health emerald.
// Chosen via the ui-ux-pro-max "Accessible & Ethical" system for
// medical/diagnostic products. High contrast, WCAG-friendly, no
// neon or AI purple/pink gradients.
// ─────────────────────────────────────────────────────────────

// Brand — Cyan scale
val Cyan50 = Color(0xFFECFEFF)
val Cyan100 = Color(0xFFCFFAFE)
val Cyan200 = Color(0xFFA5F3FC)
val Cyan400 = Color(0xFF22D3EE)
val Cyan500 = Color(0xFF06B6D4)
val Cyan600 = Color(0xFF0891B2)
val Cyan700 = Color(0xFF0E7490)
val Cyan900 = Color(0xFF164E63)

// Accent — Health Emerald scale
val Emerald100 = Color(0xFFD1FAE5)
val Emerald400 = Color(0xFF34D399)
val Emerald600 = Color(0xFF059669)
val Emerald700 = Color(0xFF047857)

// Primary brand tokens
val MedicalBlue = Cyan600            // Clinical primary (cyan)
val MedicalBlueLight = Cyan100       // Soft primary container
val MedicalBlueDark = Cyan700        // Deep primary

// Secondary / accent
val ClinicalTeal = Emerald600        // Health emerald accent
val ClinicalTealLight = Emerald100   // Soft emerald container
val ClinicalTealDark = Emerald700

// Light neutrals
val LightBg = Color(0xFFF2FCFE)      // Airy cyan-tinted background
val LightSurface = Color(0xFFFFFFFF) // Pure white cards/sheets
val LightBorder = Color(0xFFCAE9F1)  // Soft cyan border
val TextDark = Color(0xFF0B2A34)     // High-contrast teal charcoal
val TextMuted = Color(0xFF4C6B75)    // Muted teal-gray
val TextSubtle = Color(0xFF8AA6AE)   // Subtle labels

// Dark neutrals
val DarkBg = Color(0xFF071A20)       // Very dark teal
val DarkSurface = Color(0xFF0F2A33)  // Dark teal surface
val DarkBorder = Color(0xFF1E4A56)   // Dark teal border
val TextLight = Color(0xFFECFEFF)    // Near-white cyan tint
val TextLightMuted = Color(0xFF8FB4BF)

// Semantic
val SuccessGreen = Color(0xFF10B981) // Emerald
val WarningAmber = Color(0xFFF59E0B) // Amber
val ErrorRed = Color(0xFFDC2626)     // Destructive red
val ErrorRedLight = Color(0xFFF87171) // Softer red for dark mode

// ── Backward-compatible aliases (kept so existing references resolve) ──
val PrimaryViolet = MedicalBlue
val PrimaryVioletLight = MedicalBlueLight
val PrimaryVioletDark = MedicalBlueDark
val AccentTeal = ClinicalTeal
val AccentTealLight = ClinicalTealLight
val AccentCyan = Cyan400
val BackgroundDark = DarkBg
val SurfaceDark = DarkSurface
val CardDark = DarkSurface
val CardDarkElevated = DarkSurface
val TextPrimary = TextLight
val TextSecondary = TextLightMuted
val TextTertiary = TextLightMuted
val DividerColor = LightBorder
val ShimmerBase = Color(0xFFE2EFF3)
val ShimmerHighlight = Color(0xFFF1F9FB)
