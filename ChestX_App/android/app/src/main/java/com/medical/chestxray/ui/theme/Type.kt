package com.medical.chestxray.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = com.medical.chestxray.R.array.com_google_android_gms_fonts_certs
)

// Headings — Figtree (geometric, friendly, medical-clean)
val FigtreeFont = GoogleFont("Figtree")

// Body — Noto Sans (highly legible, accessible)
val NotoSansFont = GoogleFont("Noto Sans")

val HeadingFamily = FontFamily(
    Font(googleFont = FigtreeFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = FigtreeFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = FigtreeFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = FigtreeFont, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = FigtreeFont, fontProvider = provider, weight = FontWeight.ExtraBold),
)

val BodyFamily = FontFamily(
    Font(googleFont = NotoSansFont, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = NotoSansFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = NotoSansFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = NotoSansFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = NotoSansFont, fontProvider = provider, weight = FontWeight.Bold),
)

// Kept for any external references to the previous font family name.
val InterFamily = BodyFamily

val Typography = Typography(
    // ── Display & Headlines & Titles → Figtree ──
    displayLarge = TextStyle(
        fontFamily = HeadingFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = HeadingFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = HeadingFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = HeadingFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = HeadingFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = HeadingFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = HeadingFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = HeadingFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    // ── Body → Noto Sans (16px base for readability / no iOS auto-zoom) ──
    bodyLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.15.sp
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.25.sp
    ),
    // ── Labels → Noto Sans (medium/semibold) ──
    labelLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp
    )
)
