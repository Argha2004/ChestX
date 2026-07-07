package com.medical.chestxray.ui.navigation

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medical.chestxray.ui.theme.IOSEasing
import com.medical.chestxray.ui.theme.pressScale
import com.medical.chestxray.ui.theme.responsiveDp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.medical.chestxray.ui.screens.*
import com.medical.chestxray.ui.viewmodel.AnalysisViewModel
import com.medical.chestxray.ui.viewmodel.UpdateViewModel
import com.medical.chestxray.data.local.AppDatabase
import com.medical.chestxray.data.model.AnalysisResponse
import com.medical.chestxray.data.model.PatientInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : Screen("home", "Scan", Icons.Filled.DocumentScanner, Icons.Outlined.DocumentScanner)
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.SpaceDashboard, Icons.Outlined.SpaceDashboard)
    object History : Screen("history", "History", Icons.Filled.History, Icons.Outlined.History)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    object AnalysisResult : Screen("analysis_result", "Results", Icons.Filled.Analytics, Icons.Outlined.Analytics)
    object Heatmap : Screen("heatmap", "Heatmap", Icons.Filled.Map, Icons.Outlined.Map)
    object Privacy : Screen("privacy", "Privacy", Icons.Filled.Shield, Icons.Outlined.Shield)
    object ModelPerformance : Screen("model_performance", "Performance", Icons.Filled.Insights, Icons.Outlined.Insights)
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var patientInfo by remember { mutableStateOf<PatientInfo?>(null) }
    val analysisViewModel: AnalysisViewModel = viewModel()
    val updateViewModel: UpdateViewModel = viewModel()
    val updateState by updateViewModel.uiState.collectAsState()

    // Check GitHub Releases for a newer version once per app launch.
    LaunchedEffect(Unit) { updateViewModel.checkForUpdate() }

    // App opening animation — a gentle fade + scale-in of the whole UI on launch.
    var appLaunched by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appLaunched = true }
    val appAlpha by animateFloatAsState(
        targetValue = if (appLaunched) 1f else 0f,
        animationSpec = tween(durationMillis = 550, easing = IOSEasing),
        label = "appAlpha"
    )
    val appScale by animateFloatAsState(
        targetValue = if (appLaunched) 1f else 0.94f,
        animationSpec = tween(durationMillis = 550, easing = IOSEasing),
        label = "appScale"
    )

    Scaffold(
        modifier = Modifier.graphicsLayer {
            alpha = appAlpha
            scaleX = appScale
            scaleY = appScale
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        val hideBottomBar = currentDestination?.route == Screen.AnalysisResult.route
                || currentDestination?.route == Screen.Heatmap.route
                || currentDestination?.route == Screen.Privacy.route
                || currentDestination?.route == Screen.ModelPerformance.route

        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                // Tabs cross-fade smoothly; detail screens override with a push (below).
                enterTransition = { fadeIn(animationSpec = tween(280, easing = IOSEasing)) },
                exitTransition = { fadeOut(animationSpec = tween(220, easing = IOSEasing)) }
            ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToAnalysis = { uri, name, age, sex ->
                        selectedImageUri = uri
                        patientInfo = PatientInfo(name, age, sex)
                        navController.navigate(Screen.AnalysisResult.route)
                    }
                )
            }

            composable(Screen.Dashboard.route) {
                DashboardScreen()
            }

            composable(Screen.History.route) {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                HistoryScreen(
                    onOpenScan = { scanId ->
                        scope.launch {
                            val entity = withContext(Dispatchers.IO) {
                                AppDatabase.getDatabase(context.applicationContext)
                                    .scanDao()
                                    .getScanById(scanId)
                            }
                            if (entity != null) {
                                val response = AnalysisResponse(
                                    id = entity.id,
                                    timestamp = entity.timestamp,
                                    top_prediction = entity.topPrediction,
                                    confidence = entity.confidence,
                                    predictions = entity.predictions,
                                    heatmap_data = entity.heatmapData,
                                    image_url = entity.imageUri
                                )
                                val storedPatient = PatientInfo(entity.patientName, entity.patientAge, entity.patientSex)
                                val storedUri = Uri.parse(entity.imageUri ?: "")
                                selectedImageUri = storedUri
                                patientInfo = storedPatient
                                analysisViewModel.loadStoredResult(response, storedUri, storedPatient)
                                navController.navigate(Screen.AnalysisResult.route)
                            }
                        }
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onOpenPrivacyPolicy = { navController.navigate(Screen.Privacy.route) },
                    onOpenModelPerformance = { navController.navigate(Screen.ModelPerformance.route) },
                    updateViewModel = updateViewModel
                )
            }

            composable(
                route = Screen.AnalysisResult.route,
                enterTransition = {
                    slideInHorizontally(animationSpec = tween(380, easing = IOSEasing)) { it } +
                            fadeIn(animationSpec = tween(380, easing = IOSEasing))
                },
                exitTransition = {
                    slideOutHorizontally(animationSpec = tween(380, easing = IOSEasing)) { -it / 5 } +
                            fadeOut(animationSpec = tween(300, easing = IOSEasing))
                },
                popEnterTransition = {
                    slideInHorizontally(animationSpec = tween(380, easing = IOSEasing)) { -it / 5 } +
                            fadeIn(animationSpec = tween(380, easing = IOSEasing))
                },
                popExitTransition = {
                    slideOutHorizontally(animationSpec = tween(380, easing = IOSEasing)) { it } +
                            fadeOut(animationSpec = tween(300, easing = IOSEasing))
                }
            ) {
                selectedImageUri?.let { uri ->
                    AnalysisResultScreen(
                        imageUri = uri,
                        patientInfo = patientInfo,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToHeatmap = { navController.navigate(Screen.Heatmap.route) },
                        viewModel = analysisViewModel
                    )
                }
            }

            composable(
                route = Screen.Heatmap.route,
                enterTransition = {
                    slideInHorizontally(animationSpec = tween(380, easing = IOSEasing)) { it } +
                            fadeIn(animationSpec = tween(380, easing = IOSEasing))
                },
                exitTransition = {
                    slideOutHorizontally(animationSpec = tween(380, easing = IOSEasing)) { -it / 5 } +
                            fadeOut(animationSpec = tween(300, easing = IOSEasing))
                },
                popEnterTransition = {
                    slideInHorizontally(animationSpec = tween(380, easing = IOSEasing)) { -it / 5 } +
                            fadeIn(animationSpec = tween(380, easing = IOSEasing))
                },
                popExitTransition = {
                    slideOutHorizontally(animationSpec = tween(380, easing = IOSEasing)) { it } +
                            fadeOut(animationSpec = tween(300, easing = IOSEasing))
                }
            ) {
                val uiState by analysisViewModel.uiState.collectAsState()
                val result = uiState.analysisResult
                HeatmapVisualizationScreen(
                    imageUri = selectedImageUri,
                    patientInfo = patientInfo,
                    analysisResult = result,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Privacy.route,
                enterTransition = {
                    slideInHorizontally(animationSpec = tween(380, easing = IOSEasing)) { it } +
                            fadeIn(animationSpec = tween(380, easing = IOSEasing))
                },
                exitTransition = {
                    slideOutHorizontally(animationSpec = tween(380, easing = IOSEasing)) { -it / 5 } +
                            fadeOut(animationSpec = tween(300, easing = IOSEasing))
                },
                popEnterTransition = {
                    slideInHorizontally(animationSpec = tween(380, easing = IOSEasing)) { -it / 5 } +
                            fadeIn(animationSpec = tween(380, easing = IOSEasing))
                },
                popExitTransition = {
                    slideOutHorizontally(animationSpec = tween(380, easing = IOSEasing)) { it } +
                            fadeOut(animationSpec = tween(300, easing = IOSEasing))
                }
            ) {
                PrivacyPolicyScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(
                route = Screen.ModelPerformance.route,
                enterTransition = {
                    slideInHorizontally(animationSpec = tween(380, easing = IOSEasing)) { it } +
                            fadeIn(animationSpec = tween(380, easing = IOSEasing))
                },
                exitTransition = {
                    slideOutHorizontally(animationSpec = tween(380, easing = IOSEasing)) { -it / 5 } +
                            fadeOut(animationSpec = tween(300, easing = IOSEasing))
                },
                popEnterTransition = {
                    slideInHorizontally(animationSpec = tween(380, easing = IOSEasing)) { -it / 5 } +
                            fadeIn(animationSpec = tween(380, easing = IOSEasing))
                },
                popExitTransition = {
                    slideOutHorizontally(animationSpec = tween(380, easing = IOSEasing)) { it } +
                            fadeOut(animationSpec = tween(300, easing = IOSEasing))
                }
            ) {
                ModelPerformanceScreen(onNavigateBack = { navController.popBackStack() })
            }
            }

            // Floating nav bar overlays the content so it appears to hover over the
            // screen. A soft top-to-bottom gradient lets the content fade out beneath it.
            AnimatedVisibility(
                visible = !hideBottomBar,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val items = listOf(
                    Screen.Home,
                    Screen.Dashboard,
                    Screen.History,
                    Screen.Settings
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .padding(top = 44.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    FloatingNavBar(
                        items = items,
                        currentDestination = currentDestination,
                        onItemClick = { screen ->
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }

            UpdateDialog(
                state = updateState,
                onDownload = { updateViewModel.startDownload() },
                onInstall = { updateViewModel.install() },
                onDismiss = { updateViewModel.dismiss() }
            )
        }
    }
}

/**
 * A modern floating bottom navigation bar. The container floats above the content
 * with rounded corners, a soft shadow and a subtle border. The selected destination
 * animates into a filled "pill" that reveals its label beside the icon, while the
 * other destinations remain compact icon-only targets.
 */
@Composable
private fun FloatingNavBar(
    items: List<Screen>,
    currentDestination: NavDestination?,
    onItemClick: (Screen) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
            shape = RoundedCornerShape(50.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 5.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavBarItem(
                        screen = screen,
                        selected = selected,
                        onClick = { onItemClick(screen) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavBarItem(
    screen: Screen,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 500),
        label = "navItemContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 500),
        label = "navItemContent"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .pressScale()
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(
                horizontal = if (selected) responsiveDp(16.dp, 22.dp, 25.dp) else responsiveDp(10.dp, 12.dp, 14.dp),
                vertical = 15.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
            contentDescription = screen.title,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        AnimatedVisibility(visible = selected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = screen.title,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}
