package com.medical.chestxray.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medical.chestxray.data.model.AnalysisResponse
import com.medical.chestxray.data.model.PatientInfo
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import com.medical.chestxray.ui.theme.IOSEasing
import com.medical.chestxray.ui.theme.entrance
import com.medical.chestxray.ui.theme.pressScale
import com.medical.chestxray.ui.theme.responsiveSp
import com.medical.chestxray.ui.theme.screenHorizontalPadding
import com.medical.chestxray.util.PdfReportExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapVisualizationScreen(
    imageUri: Uri?,
    patientInfo: PatientInfo?,
    analysisResult: AnalysisResponse?,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedView by remember { mutableStateOf(HeatmapView.OVERLAY) }

    val heatmapData = analysisResult?.heatmap_data
    val prediction = analysisResult?.top_prediction ?: "Unknown"
    val confidence = analysisResult?.confidence ?: 0f

    val heatmapBitmap by remember(heatmapData) {
        mutableStateOf(heatmapData?.let { decodeBase64Image(it) })
    }

    // Decode the original X-ray so we can composite a properly aligned overlay.
    val originalBitmap by remember(imageUri) {
        mutableStateOf(imageUri?.let { loadBitmapFromUri(context, it) })
    }

    // Composite the heatmap onto the original at the original's resolution. This
    // stretches the square 384x384 heatmap to the original's aspect ratio so the
    // hotspots line up with the anatomy instead of spilling into the letterbox.
    val overlayBitmap by remember(originalBitmap, heatmapBitmap) {
        mutableStateOf(
            if (originalBitmap != null && heatmapBitmap != null) {
                composeOverlayBitmap(originalBitmap!!, heatmapBitmap!!)
            } else null
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)) {
                                append("H")
                            }
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)) {
                                append("eatmap")
                            }
                        },
                        fontSize = responsiveSp(25.sp, 28.sp, 30.sp),
                        lineHeight = 38.sp,
                        letterSpacing = (-0.5).sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = screenHorizontalPadding(), vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Heatmap Display ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 3f)
                    .entrance(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (selectedView) {
                        HeatmapView.ORIGINAL -> {
                            if (imageUri != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(model = imageUri),
                                    contentDescription = "Original X-Ray",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(28.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                ImagePlaceholder("Original X-Ray Image not available")
                            }
                        }

                        HeatmapView.HEATMAP -> {
                            if (heatmapBitmap != null) {
                                Image(
                                    bitmap = heatmapBitmap!!.asImageBitmap(),
                                    contentDescription = "Heatmap",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(28.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                ImagePlaceholder("Saliency heatmap not available")
                            }
                        }

                        HeatmapView.OVERLAY -> {
                            when {
                                // Preferred: a single pre-composited bitmap that shares the
                                // original's aspect ratio, so the overlay aligns exactly.
                                overlayBitmap != null -> {
                                    Image(
                                        bitmap = overlayBitmap!!.asImageBitmap(),
                                        contentDescription = "Heatmap Overlay",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(28.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                // Fallback: only one of the two images is available.
                                imageUri != null || heatmapBitmap != null -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(28.dp))
                                    ) {
                                        imageUri?.let {
                                            Image(
                                                painter = rememberAsyncImagePainter(model = it),
                                                contentDescription = "Original X-Ray",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                        heatmapBitmap?.let {
                                            Image(
                                                bitmap = it.asImageBitmap(),
                                                contentDescription = "Heatmap Overlay",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .alpha(0.55f),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    ImagePlaceholder("Overlay composite not available")
                                }
                            }
                        }
                    }

                    // Prediction overlay badge
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp),
                        shape = RoundedCornerShape(50.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = "$prediction · ${confidence.toInt()}%",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Zoom controls
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledIconButton(
                            onClick = { /* Zoom in */ },
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(50.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "Zoom In",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        FilledIconButton(
                            onClick = { /* Full screen */ },
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(50.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Full Screen",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // ── View Selector ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .entrance(delayMillis = 70),
                shape = RoundedCornerShape(50.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ViewButton(
                        text = "Original",
                        isSelected = selectedView == HeatmapView.ORIGINAL,
                        onClick = { selectedView = HeatmapView.ORIGINAL },
                        modifier = Modifier.weight(1f),
                    )
                    ViewButton(
                        text = "Heatmap",
                        isSelected = selectedView == HeatmapView.HEATMAP,
                        onClick = { selectedView = HeatmapView.HEATMAP },
                        modifier = Modifier.weight(1f)
                    )
                    ViewButton(
                        text = "Overlay",
                        isSelected = selectedView == HeatmapView.OVERLAY,
                        onClick = { selectedView = HeatmapView.OVERLAY },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Share Button ──
            Button(
                onClick = {
                    com.medical.chestxray.util.SoundFx.click()
                    PdfReportExporter.shareReport(context, imageUri, patientInfo, analysisResult)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .entrance(delayMillis = 140)
                    .pressScale(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(50.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Share PDF Report",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ── Download Button ──
            OutlinedButton(
                onClick = {
                    com.medical.chestxray.util.SoundFx.click()
                    val viewModeName = when (selectedView) {
                        HeatmapView.ORIGINAL -> "Original"
                        HeatmapView.HEATMAP -> "Heatmap"
                        HeatmapView.OVERLAY -> "Overlay"
                    }
                    PdfReportExporter.downloadViewImage(context, viewModeName, imageUri, heatmapData)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .entrance(delayMillis = 200)
                    .pressScale(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(50.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Download Image",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun ImagePlaceholder(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.BrokenImage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ViewButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animated colors so switching views glides like an iOS segmented control.
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(250, easing = IOSEasing),
        label = "viewButtonContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(250, easing = IOSEasing),
        label = "viewButtonContent"
    )
    Button(
        onClick = onClick,
        modifier = modifier
            .height(40.dp)
            .pressScale(),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(50.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            hoveredElevation = 0.dp,
            focusedElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

enum class HeatmapView {
    ORIGINAL,
    HEATMAP,
    OVERLAY
}

fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Draws the heatmap over the original at the original's pixel dimensions. Because the
 * heatmap is stretched (via src/dest Rects) to the original's aspect ratio, the composite
 * lines up with the anatomy — unlike stacking a square heatmap over a letterboxed image.
 */
fun composeOverlayBitmap(original: Bitmap, heatmap: Bitmap): Bitmap? {
    return try {
        val result = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(original, 0f, 0f, null)
        val paint = Paint().apply { alpha = (0.55f * 255).toInt() }
        val src = Rect(0, 0, heatmap.width, heatmap.height)
        val dest = Rect(0, 0, original.width, original.height)
        canvas.drawBitmap(heatmap, src, dest, paint)
        result
    } catch (e: Exception) {
        null
    }
}

fun decodeBase64Image(base64String: String): Bitmap? {
    return try {
        val cleanBase64 = if (base64String.contains(",")) {
            base64String.substringAfter(",")
        } else {
            base64String
        }
        val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
        null
    }
}
