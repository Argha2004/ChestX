package com.medical.chestxray.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Coarse phone width buckets used to adapt spacing and typography so the UI reads
 * well from small (~320dp) phones up to large phablets, without a full tablet layout.
 */
enum class WidthClass { COMPACT, REGULAR, LARGE }

@Composable
fun rememberWidthClass(): WidthClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < 360 -> WidthClass.COMPACT   // small phones (~320–359dp)
        widthDp < 410 -> WidthClass.REGULAR   // most phones
        else -> WidthClass.LARGE              // large phones / phablets
    }
}

/** Picks a [Dp] value for the current phone width bucket. */
@Composable
fun responsiveDp(compact: Dp, regular: Dp, large: Dp): Dp = when (rememberWidthClass()) {
    WidthClass.COMPACT -> compact
    WidthClass.REGULAR -> regular
    WidthClass.LARGE -> large
}

/** Picks a [TextUnit] value for the current phone width bucket. */
@Composable
fun responsiveSp(compact: TextUnit, regular: TextUnit, large: TextUnit): TextUnit = when (rememberWidthClass()) {
    WidthClass.COMPACT -> compact
    WidthClass.REGULAR -> regular
    WidthClass.LARGE -> large
}

/** Standard screen edge padding that tightens on small phones and relaxes on large ones. */
@Composable
fun screenHorizontalPadding(): Dp = responsiveDp(16.dp, 20.dp, 24.dp)
