package com.hawdietpro.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hawdietpro.app.data.prefs.ThemeMode
import com.hawdietpro.app.ui.components.LoadingBox
import com.hawdietpro.app.ui.feature.analytics.AnalyticsScreen
import com.hawdietpro.app.ui.feature.backup.BackupScreen
import com.hawdietpro.app.ui.feature.camera.CameraScreen
import com.hawdietpro.app.ui.feature.history.HistoryScreen
import com.hawdietpro.app.ui.feature.home.HomeScreen
import com.hawdietpro.app.ui.feature.meal.MealEditorScreen
import com.hawdietpro.app.ui.feature.onboarding.OnboardingScreen
import com.hawdietpro.app.ui.feature.profile.ProfileEditScreen
import com.hawdietpro.app.ui.feature.profile.ProfileScreen
import com.hawdietpro.app.ui.feature.queue.QueueScreen
import com.hawdietpro.app.ui.feature.relog.RelogScreen
import com.hawdietpro.app.ui.feature.achievements.AchievementsScreen
import com.hawdietpro.app.ui.feature.settings.SettingsScreen
import com.hawdietpro.app.ui.feature.update.UpdateScreen
import com.hawdietpro.app.ui.feature.weight.WeightScreen
import com.hawdietpro.app.ui.navigation.HawBottomBar
import com.hawdietpro.app.ui.navigation.QuickAction
import com.hawdietpro.app.ui.navigation.QuickAddSheet
import com.hawdietpro.app.ui.navigation.Routes
import com.hawdietpro.app.ui.navigation.TopLevelDestination
import com.hawdietpro.app.ui.theme.HawDietTheme
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface DeepLink {
    data class Meal(val id: Long) : DeepLink
    data object Update : DeepLink
}

@Composable
fun HawDietRoot(
    deepLinks: StateFlow<DeepLink?>,
    onDeepLinkHandled: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val theme by viewModel.themeMode.collectAsStateWithLifecycle()
    val dark = when (theme) {
        ThemeMode.SYSTEM -> null
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    HawDietTheme(darkTheme = dark) {
        val start by viewModel.startState.collectAsStateWithLifecycle()
        when (start) {
            StartState.Loading -> LoadingBox()
            StartState.NeedsOnboarding -> OnboardingScreen()
            StartState.Ready -> MainScaffold(viewModel, deepLinks, onDeepLinkHandled)
        }
    }
}

@Composable
private fun MainScaffold(
    viewModel: MainViewModel,
    deepLinks: StateFlow<DeepLink?>,
    onDeepLinkHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination
    val showBottomBar = TopLevelDestination.entries.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    val deepLink by deepLinks.collectAsStateWithLifecycle()
    LaunchedEffect(deepLink) {
        when (val link = deepLink) {
            is DeepLink.Meal -> navController.navigate(Routes.editMeal(link.id))
            DeepLink.Update -> navController.navigate(Routes.UPDATE)
            null -> return@LaunchedEffect
        }
        onDeepLinkHandled()
    }

    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    if (showQuickAdd) {
        QuickAddSheet(
            onDismiss = { showQuickAdd = false },
            onAction = { action ->
                showQuickAdd = false
                when (action) {
                    QuickAction.CAMERA -> navController.navigate(Routes.camera())
                    QuickAction.GALLERY -> navController.navigate(Routes.camera(openGallery = true))
                    QuickAction.RELOG -> navController.navigate(Routes.RELOG)
                    QuickAction.MANUAL -> navController.navigate(Routes.manualMeal())
                    QuickAction.WEIGHT -> navController.navigate(Routes.WEIGHT)
                }
            },
        )
    }

    val mustUpdate by viewModel.mustUpdate.collectAsStateWithLifecycle()
    var mustUpdateDismissed by rememberSaveable { mutableStateOf(false) }
    if (mustUpdate && !mustUpdateDismissed) {
        AlertDialog(
            onDismissRequest = { mustUpdateDismissed = true },
            title = { Text("Perlu update aplikasi") },
            text = { Text("Versi aplikasi ini sudah terlalu lama untuk fitur & data terbaru. Pasang update agar semua fitur berjalan normal. Data Anda tetap aman.") },
            confirmButton = {
                TextButton(onClick = {
                    mustUpdateDismissed = true
                    navController.navigate(Routes.UPDATE)
                }) { Text("Update sekarang") }
            },
            dismissButton = { TextButton(onClick = { mustUpdateDismissed = true }) { Text("Nanti") } },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) {
                HawBottomBar(
                    currentRoute = { route -> currentDestination?.hierarchy?.any { it.route == route } == true },
                    onNavigate = { navController.navigateTopLevel(it) },
                    onAdd = { showQuickAdd = true },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = if (showBottomBar) padding.calculateBottomPadding() else 0.dp)) {
            NavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        mainViewModel = viewModel,
                        onOpenCamera = { type -> navController.navigate(Routes.camera(type)) },
                        onManual = { type, time -> navController.navigate(Routes.manualMeal(type, time)) },
                        onOpenMeal = { id -> navController.navigate(Routes.editMeal(id)) },
                        onOpenQueue = { navController.navigate(Routes.QUEUE) },
                        onOpenUpdate = { navController.navigate(Routes.UPDATE) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenWeight = { navController.navigate(Routes.WEIGHT) },
                        onOpenProfile = { navController.navigateTopLevel(Routes.PROFILE) },
                        onOpenAchievements = { navController.navigate(Routes.ACHIEVEMENTS) },
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        onOpenMeal = { id -> navController.navigate(Routes.editMeal(id)) },
                        onAddForDate = { time -> navController.navigate(Routes.manualMeal(null, time)) },
                    )
                }
                composable(Routes.ANALYTICS) {
                    AnalyticsScreen(onOpenWeight = { navController.navigate(Routes.WEIGHT) })
                }
                composable(Routes.PROFILE) {
                    ProfileScreen(
                        onEditProfile = { navController.navigate(Routes.PROFILE_EDIT) },
                        onSettings = { navController.navigate(Routes.SETTINGS) },
                        onWeight = { navController.navigate(Routes.WEIGHT) },
                        onQueue = { navController.navigate(Routes.QUEUE) },
                        onUpdate = { navController.navigate(Routes.UPDATE) },
                        onBackup = { navController.navigate(Routes.BACKUP) },
                        onAchievements = { navController.navigate(Routes.ACHIEVEMENTS) },
                        onRelog = { navController.navigate(Routes.RELOG) },
                    )
                }
                composable(
                    Routes.CAMERA,
                    arguments = listOf("type", "gallery").map { name ->
                        navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }
                    },
                ) {
                    CameraScreen(
                        onClose = { navController.popBackStack() },
                        onPhotoReady = { path, type, time, hint ->
                            navController.navigate(Routes.analyzePhoto(path, type, time, hint)) {
                                popUpTo(Routes.CAMERA) { inclusive = true }
                            }
                        },
                        onManual = { type ->
                            navController.navigate(Routes.manualMeal(type)) { popUpTo(Routes.CAMERA) { inclusive = true } }
                        },
                    )
                }
                composable(
                    Routes.MEAL_EDITOR,
                    arguments = listOf("id", "photo", "type", "time", "hint", "pending").map { name ->
                        navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }
                    },
                ) {
                    MealEditorScreen(
                        onClose = { message ->
                            navController.popBackStack()
                            message?.let(showMessage)
                        },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.PROFILE_EDIT) {
                    ProfileEditScreen(onDone = { message ->
                        navController.popBackStack()
                        message?.let(showMessage)
                    })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onUpdate = { navController.navigate(Routes.UPDATE) },
                        onBackup = { navController.navigate(Routes.BACKUP) },
                        showMessage = showMessage,
                    )
                }
                composable(Routes.WEIGHT) { WeightScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.QUEUE) {
                    QueueScreen(
                        onBack = { navController.popBackStack() },
                        onFillManually = { id, path, type, time -> navController.navigate(Routes.fromPending(id, path, type, time)) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.UPDATE) { UpdateScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.RELOG) { RelogScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.ACHIEVEMENTS) { AchievementsScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.BACKUP) { BackupScreen(onBack = { navController.popBackStack() }, showMessage = showMessage) }
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
