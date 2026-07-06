package com.medical.chestxray.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medical.chestxray.ui.theme.entrance
import com.medical.chestxray.ui.theme.responsiveSp
import com.medical.chestxray.ui.theme.screenHorizontalPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)) {
                                append("P")
                            }
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)) {
                                append("rivacy & License")
                            }
                        },
                        fontSize = responsiveSp(22.sp, 25.sp, 27.sp),
                        lineHeight = 34.sp,
                        letterSpacing = (-0.5).sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .padding(start = screenHorizontalPadding(), end = screenHorizontalPadding(), top = 4.dp, bottom = 32.dp)
                .entrance(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── Privacy Policy ──
            PolicySection(title = "Privacy Policy", icon = Icons.Default.Shield) {
                PolicyCard(
                    heading = "Local Data Protection",
                    headingIcon = Icons.Default.Security,
                    body = "All uploaded scans, patient details (name, age, sex), classification results, imported models, and generated PDF reports are stored exclusively on this device in a local database. No images, patient data, or reports are ever uploaded, transmitted, or stored on any remote server — every step of the AI analysis runs fully offline on your device."
                )
                PolicyCard(
                    heading = "Permissions",
                    headingIcon = Icons.Default.Key,
                    body = "• Camera — used only to capture a chest X-ray photo when you choose the camera option.\n• Photos / Media — used only to let you pick an existing X-ray image from your device.\n\nThese permissions are used solely for the stated purpose and never for tracking, advertising, or analytics."
                )
                PolicyCard(
                    heading = "Your Control",
                    headingIcon = Icons.Default.DeleteForever,
                    body = "You can permanently erase all stored scans, images, and reports at any time from Settings → Data → Clear Scan Cache. Uninstalling the app also removes all locally stored data."
                )
            }

            // ── License ──
            PolicySection(title = "License", icon = Icons.Default.Gavel) {
                PolicyCard(
                    heading = "MIT License",
                    headingIcon = Icons.Default.Description,
                    body = "Copyright (c) 2026-27 ChestX\n\n" +
                            "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\n" +
                            "The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\n" +
                            "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE."
                )
                PolicyCard(
                    heading = "Open-Source Components",
                    headingIcon = Icons.Default.Code,
                    body = "This app is built with Jetpack Compose, Material 3, ONNX Runtime, Room, and Coil, each distributed under its own open-source license and used in accordance with those terms."
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PolicySection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
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
private fun PolicyCard(
    heading: String,
    headingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    body: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = headingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}
