package com.medical.chestxray.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medical.chestxray.ui.theme.*
import com.medical.chestxray.ui.viewmodel.UpdateStatus
import com.medical.chestxray.ui.viewmodel.UpdateViewModel
import com.medical.chestxray.util.ModelRepository
import com.medical.chestxray.util.SoundFx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenPrivacyPolicy: () -> Unit = {},
    updateViewModel: UpdateViewModel = viewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var soundEnabled by remember { mutableStateOf(SoundFx.enabled) }
    val updateState by updateViewModel.uiState.collectAsState()

    val appVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.1.0"
        } catch (e: Exception) {
            "0.1.0"
        }
    }

    // Active CNN model (bundled default or a side-loaded one).
    var activeModel by remember { mutableStateOf(ModelRepository.getActiveModel(context)) }

    // Import dialog state.
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var importName by remember { mutableStateOf("") }
    var importArch by remember { mutableStateOf("") }
    var importParams by remember { mutableStateOf("") }
    var importVersion by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val fileName = queryFileName(context, uri)
            if (fileName != null && !fileName.endsWith(".onnx", ignoreCase = true)) {
                SoundFx.error()
                scope.launch {
                    snackbarHostState.showSnackbar("Unsupported file. Please select a .onnx model.")
                }
            } else {
                importName = (fileName ?: "Custom model").substringBeforeLast('.')
                importArch = ""
                importParams = ""
                importVersion = ""
                pendingUri = uri
            }
        }
    }

    // ── Import metadata dialog ──
    if (pendingUri != null) {
        AlertDialog(
            onDismissRequest = { if (!isImporting) pendingUri = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Import ONNX Model",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Describe the model so its details show correctly on the model card.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = importName,
                        onValueChange = { importName = it },
                        label = { Text("Model name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(30.dp)
                    )
                    OutlinedTextField(
                        value = importArch,
                        onValueChange = { importArch = it },
                        label = { Text("Architecture") },
                        placeholder = { Text("e.g. EfficientNetV2-S") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(30.dp)
                    )
                    OutlinedTextField(
                        value = importParams,
                        onValueChange = { importParams = it },
                        label = { Text("Parameters") },
                        placeholder = { Text("e.g. 20 M") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(30.dp)
                    )
                    OutlinedTextField(
                        value = importVersion,
                        onValueChange = { importVersion = it },
                        label = { Text("Version") },
                        placeholder = { Text("e.g. v0.1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(30.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isImporting && importName.isNotBlank(),
                    onClick = {
                        val uri = pendingUri ?: return@TextButton
                        isImporting = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                ModelRepository.importModel(
                                    context = context,
                                    uri = uri,
                                    name = importName.trim(),
                                    architecture = importArch.trim().ifBlank { "Custom" },
                                    parameters = importParams.trim().ifBlank { "—" },
                                    version = importVersion.trim().ifBlank { "—" }
                                )
                            }
                            isImporting = false
                            result.onSuccess { info ->
                                activeModel = info
                                pendingUri = null
                                SoundFx.success()
                                snackbarHostState.showSnackbar("Model imported successfully.")
                            }.onFailure { e ->
                                SoundFx.error()
                                snackbarHostState.showSnackbar("Failed: ${e.localizedMessage ?: "Import error"}")
                            }
                        }
                    }
                ) {
                    Text(if (isImporting) "Importing…" else "Import")
                }
            },
            dismissButton = {
                TextButton(enabled = !isImporting, onClick = { pendingUri = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 100.dp)
            ) { data ->
                val message = data.visuals.message
                val isError = message.startsWith("Failed") ||
                        message.startsWith("Unable") ||
                        message.startsWith("Unsupported")
                val accent = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)) {
                                append("S")
                            }
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)) {
                                append("ettings")
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(start = screenHorizontalPadding(), end = screenHorizontalPadding(), top = 8.dp, bottom = 100.dp)
                .entrance(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // ── About ──
            SettingsSection(title = "About the App", icon = Icons.Default.Science) {
                Text(
                    text = "ChestX is an AI-powered chest X-ray analysis research assistant designed to identify potential thoracic abnormalities. Utilizing deep learning models, the application processes digital chest radiographs to detect patterns corresponding to various conditions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        MetadataRow("App Version", "v${appVersion}")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // ── Software Update ──
            SettingsSection(title = "Software Update", icon = Icons.Default.SystemUpdate) {
                val statusText = when (updateState.status) {
                    UpdateStatus.CHECKING -> "Checking for updates…"
                    UpdateStatus.AVAILABLE -> "Update available: ${updateState.info?.latestVersion ?: ""}."
                    UpdateStatus.DOWNLOADING -> "Downloading update… ${updateState.progress}%"
                    UpdateStatus.READY -> "Update downloaded — ready to install."
                    UpdateStatus.UP_TO_DATE -> "You're on the latest version (v${appVersion})."
                    UpdateStatus.ERROR -> updateState.errorMessage
                        ?: "Couldn't check for updates. Check your connection and try again."
                    else -> "Updates are delivered through GitHub Releases. Check anytime for a new version."
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val busy = updateState.status == UpdateStatus.CHECKING ||
                        updateState.status == UpdateStatus.DOWNLOADING
                Button(
                    onClick = {
                        SoundFx.click()
                        updateViewModel.checkForUpdate()
                    },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .pressScale(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(50.dp)
                ) {
                    if (updateState.status == UpdateStatus.CHECKING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Check for Updates",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // ── AI Model ──
            SettingsSection(title = "AI Model", icon = Icons.Default.Memory) {
                // Active model status card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.DeveloperMode,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeModel.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (activeModel.isBundled) {
                                        "Built-in default model"
                                    } else {
                                        "Side-loaded · ${formatBytes(activeModel.sizeBytes)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val chipColor = if (activeModel.isBundled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                            Surface(
                                shape = RoundedCornerShape(50.dp),
                                color = chipColor.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = if (activeModel.isBundled) "DEFAULT" else "CUSTOM",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = chipColor
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        MetadataRow("Architecture", activeModel.architecture)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        MetadataRow("Parameters", activeModel.parameters)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        MetadataRow("Version", activeModel.version)
                    }
                }

                // Details note
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Only ONNX float32 (.onnx) models are supported. Enter the architecture, parameters, and version when importing so they appear above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            SoundFx.click()
                            filePicker.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .pressScale(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Import Your Model",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (!activeModel.isBundled) {
                        OutlinedButton(
                            onClick = {
                                SoundFx.click()
                                ModelRepository.resetToDefault(context)
                                activeModel = ModelRepository.getActiveModel(context)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Reverted to the built-in model.")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .pressScale(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(50.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reset",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // ── Preferences ──
            SettingsSection(title = "Preferences", icon = Icons.Default.Tune) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Interface Sounds",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Subtle tones for taps, tabs, and results",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { checked ->
                                soundEnabled = checked
                                SoundFx.enabled = checked
                                if (checked) SoundFx.click()
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // ── Privacy ──
            SettingsSection(title = "Privacy", icon = Icons.Default.Shield) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScale(0.98f)
                        .clickable {
                            SoundFx.click()
                            onOpenPrivacyPolicy()
                        },
                    shape = RoundedCornerShape(50.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Privacy Policy & License",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "How your data is handled · MIT License",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // ── Disclaimer ──
            SettingsSection(title = "Disclaimer", icon = Icons.Default.Warning) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Research & Educational Use Only",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = "This application and the AI models are designed solely for scientific research and educational study. They are not approved for active clinical diagnostic use. Always consult a qualified physician or healthcare provider for professional medical diagnosis or treatment.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // ── Developer ──
            SettingsSection(title = "Developed By", icon = Icons.Default.Code) {
                Text(
                    text = "Arghadeep Pakhira",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        SoundFx.click()
                        try {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/Argha2004")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Unable to open link.")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .pressScale(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Engineering,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Github",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // ── Data ──
            SettingsSection(title = "Data", icon = Icons.Default.Storage) {
                Text(
                    text = "Clear all scan history, cached images, and diagnostic reports stored on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        SoundFx.click()
                        scope.launch {
                            try {
                                val scanDao = com.medical.chestxray.data.local.AppDatabase.getDatabase(context.applicationContext).scanDao()
                                scanDao.deleteAllScans()

                                SoundFx.success()
                                snackbarHostState.showSnackbar("History cleared successfully.")
                            } catch (e: Exception) {
                                SoundFx.error()
                                snackbarHostState.showSnackbar(
                                    "Failed: ${e.localizedMessage ?: "Storage error"}"
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp).pressScale(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Clear Scan Cache",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        content()
    }
}

@Composable
fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Reads a human-readable file name from a content URI, if available. */
private fun queryFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            name = cursor.getString(index)
        }
    }
    return name
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) String.format("%.1f MB", mb) else String.format("%.0f KB", bytes / 1024.0)
}
