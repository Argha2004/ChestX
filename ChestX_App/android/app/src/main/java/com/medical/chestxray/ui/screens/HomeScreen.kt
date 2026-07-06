package com.medical.chestxray.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.medical.chestxray.ui.theme.IOSEasing
import com.medical.chestxray.ui.theme.entrance
import com.medical.chestxray.ui.theme.pressScale
import com.medical.chestxray.ui.theme.responsiveSp
import com.medical.chestxray.ui.theme.screenHorizontalPadding
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAnalysis: (Uri, String, Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    val scrollState = rememberScrollState()

    var patientName by remember { mutableStateOf("") }
    var patientAge by remember { mutableStateOf("") }
    var patientSex by remember { mutableStateOf("Male") }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedImageUri = it }
    }

    fun createImageUri(): Uri {
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        val file = File(cacheDir, "temp_camera_image_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "com.medical.chestxray.fileprovider",
            file
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            tempImageUri?.let { selectedImageUri = it }
        }
    }

    fun launchCamera() {
        try {
            val uri = createImageUri()
            tempImageUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Unable to open camera: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    // The manifest declares the CAMERA permission, so ACTION_IMAGE_CAPTURE requires it
    // to be granted at runtime before the camera app will open.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Camera permission is required to take a photo.", Toast.LENGTH_SHORT).show()
        }
    }

    // First-run welcome popup (disclaimer, how-to, model & dataset info) — shown once.
    var showIntro by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("chestx_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("intro_shown", false)) showIntro = true
    }
    if (showIntro) {
        FirstRunDialog(onDismiss = {
            showIntro = false
            context.getSharedPreferences("chestx_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("intro_shown", true).apply()
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                style = SpanStyle(
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                append("Chest")
                            }
                            withStyle(
                                style = SpanStyle(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                append("X")
                            }
                        },
                        fontSize = responsiveSp(25.sp, 28.sp, 30.sp),
                        lineHeight = 38.sp,
                        letterSpacing = (-0.5).sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(start = screenHorizontalPadding(), end = screenHorizontalPadding(), top = 20.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── Upload Area ──
            val uploadBorderColor = if (selectedImageUri == null) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.primary
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 3f)
                    .entrance()
                    .pressScale(0.98f)
                    .clickable { galleryLauncher.launch("image/*") },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, uploadBorderColor)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedImageUri != null) {
                        Image(
                            painter = rememberAsyncImagePainter(selectedImageUri),
                            contentDescription = "Selected X-Ray",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Import Image",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Select a Grayscale X-Ray Image from your device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // ── Camera & Gallery Buttons ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .entrance(delayMillis = 60),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        com.medical.chestxray.util.SoundFx.click()
                        val alreadyGranted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (alreadyGranted) {
                            launchCamera()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .pressScale(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Camera",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = {
                        com.medical.chestxray.util.SoundFx.click()
                        galleryLauncher.launch("image/*")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .pressScale(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Gallery",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Patient Details Form ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .entrance(delayMillis = 120),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text(
                        text = "Patient Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    OutlinedTextField(
                        value = patientName,
                        onValueChange = { patientName = it },
                        label = { Text("Full Name") },
                        placeholder = { Text("e.g. Elon Musk") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    
                    OutlinedTextField(
                        value = patientAge,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                patientAge = input
                            }
                        },
                        label = { Text("Age") },
                        placeholder = { Text("e.g. 45") },
                        leadingIcon = {
                            Icon(Icons.Default.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Sex",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Male", "Female", "Other").forEach { sexOption ->
                                val selected = patientSex == sexOption
                                // Animated like an iOS segmented control — colors glide, no snap.
                                val containerColor by animateColorAsState(
                                    targetValue = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    },
                                    animationSpec = tween(250, easing = IOSEasing),
                                    label = "sexChipContainer"
                                )
                                val contentColor by animateColorAsState(
                                    targetValue = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    animationSpec = tween(250, easing = IOSEasing),
                                    label = "sexChipContent"
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .pressScale()
                                        .clip(RoundedCornerShape(50.dp))
                                        .background(containerColor)
                                        .clickable { patientSex = sexOption }
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(50.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = sexOption,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = contentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Analyze Button ──
            val isFormValid = selectedImageUri != null && 
                    patientName.isNotBlank() && 
                    patientAge.isNotBlank() && 
                    patientSex.isNotBlank()

            Button(
                onClick = {
                    com.medical.chestxray.util.SoundFx.click()
                    val ageInt = patientAge.toIntOrNull() ?: 0
                    selectedImageUri?.let { onNavigateToAnalysis(it, patientName, ageInt, patientSex) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .entrance(delayMillis = 180)
                    .pressScale()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(50.dp)
                    ),
                enabled = isFormValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(50.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Biotech,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Analyze X-Ray",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ── Disclaimer Card ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .entrance(delayMillis = 240),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(15.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = "Research Disclaimer",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "This app is an AI research prototype. It is not intended for clinical diagnostic use, active patient care, or treatment decisions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FirstRunDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.HealthAndSafety,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Welcome to ChestX",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IntroSection(
                    icon = Icons.Default.Warning,
                    tint = MaterialTheme.colorScheme.error,
                    title = "Disclaimer",
                    body = "ChestX is an AI research & educational prototype. It is NOT a medical device and must not be used for clinical diagnosis, treatment, or any patient-care decision. Always consult a qualified physician."
                )
                IntroSection(
                    icon = Icons.Default.TouchApp,
                    tint = MaterialTheme.colorScheme.primary,
                    title = "How to Use",
                    body = "1. On the Scan tab, upload or capture a chest X-ray and enter the patient's details.\n2. Tap Analyze — the AI runs fully on your device.\n3. Review the findings, confidence, and heatmap, then export or share a PDF report.\n4. Past scans are saved under History."
                )
                IntroSection(
                    icon = Icons.Default.Memory,
                    tint = MaterialTheme.colorScheme.primary,
                    title = "The AI Model",
                    body = "A convolutional neural network (EfficientNetV2-S) performs multi-label classification across 14 thoracic findings, running offline via ONNX Runtime, and produces an occlusion-based saliency heatmap."
                )
                IntroSection(
                    icon = Icons.Default.Dataset,
                    tint = MaterialTheme.colorScheme.primary,
                    title = "Training Dataset",
                    body = "The model was trained on the NIH ChestX-ray14 dataset — 112,120 frontal-view chest radiographs from 30,805 patients, labelled across 14 common thoracic conditions."
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(50.dp)) {
                Text("Get Started")
            }
        }
    )
}

@Composable
private fun IntroSection(icon: ImageVector, tint: Color, title: String, body: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
