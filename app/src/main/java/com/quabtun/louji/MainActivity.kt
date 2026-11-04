package com.quabtun.louji

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quabtun.louji.data.AppSettings
import com.quabtun.louji.ui.AppViewModelFactory
import com.quabtun.louji.ui.lock.AppLockScreen
import com.quabtun.louji.ui.onboarding.OnboardingScreen
import com.quabtun.louji.ui.detail.RecordDetailScreen
import com.quabtun.louji.ui.detail.RecordDetailViewModel
import com.quabtun.louji.ui.edit.RecordEditScreen
import com.quabtun.louji.ui.edit.RecordEditViewModel
import com.quabtun.louji.ui.list.RecordListScreen
import com.quabtun.louji.ui.list.RecordListViewModel
import com.quabtun.louji.ui.media.MediaViewerScreen
import com.quabtun.louji.ui.media.RecordMediaScreen
import com.quabtun.louji.ui.media.MediaViewerViewModel
import com.quabtun.louji.ui.media.RecordMediaViewModel
import com.quabtun.louji.ui.settings.SettingsScreen
import com.quabtun.louji.ui.settings.SettingsViewModel
import com.quabtun.louji.ui.stats.StatsScreen
import com.quabtun.louji.ui.stats.StatsViewModel
import com.quabtun.louji.ui.theme.LouJiTheme
import com.quabtun.louji.ui.update.UpdateSheet
import com.quabtun.louji.ui.update.UpdateUiState
import com.quabtun.louji.ui.update.UpdateViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LouJiApp).container
        setContent {
            LouJiRoot(container)
        }
    }
}

private const val ROUTE_LIST = "records"
private const val ROUTE_EDIT = "edit?recordId={recordId}&duplicateId={duplicateId}"
private const val ROUTE_DETAIL = "detail/{recordId}"
private const val ROUTE_RECORD_MEDIA = "record-media/{recordId}"
private const val ROUTE_VIEWER = "viewer/{recordId}?index={index}"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_STATS = "stats"
private const val GITHUB_URL = "https://github.com/Quabtun/louji"

@Composable
private fun LouJiRoot(container: AppContainer) {
    val factory = remember(container) { AppViewModelFactory(container) }
    val updateViewModel: UpdateViewModel = viewModel(factory = factory)
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var settings by remember { mutableStateOf(AppSettings()) }
    var settingsReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        container.settings.settings.collect {
            settings = it
            settingsReady = true
        }
    }
    LaunchedEffect(Unit) {
        updateViewModel.checkOnStartup()
    }
    LaunchedEffect(updateState) {
        when (val state = updateState) {
            is UpdateUiState.UpToDate -> {
                snackbarHostState.showSnackbar("已经是最新版本")
                updateViewModel.consumeUpToDate()
            }
            is UpdateUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                updateViewModel.consumeError()
            }
            else -> Unit
        }
    }

    LouJiTheme(themeMode = settings.themeMode) {
        val lockState by container.lock.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        if (!lockState.ready || !settingsReady) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (!settings.onboardingSeen) {
            OnboardingScreen(
                onStart = { scope.launch { container.settings.setOnboardingSeen(true) } },
            )
        } else if (lockState.locked) {
            AppLockScreen(onUnlocked = container.lock::unlock)
        } else {
        val navController = rememberNavController()
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = ROUTE_LIST,
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 12 }
                },
                exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = {
                    fadeOut(tween(120)) + slideOutHorizontally(tween(200)) { it / 12 }
                },
            ) {
                composable(ROUTE_LIST) {
                    val viewModel: RecordListViewModel =
                        viewModel(factory = factory)
                    RecordListScreen(
                        viewModel = viewModel,
                        onCreate = { navController.navigate(editRoute()) },
                        onOpenMedia = { id -> navController.navigate("record-media/$id") },
                        onOpenDetail = { id -> navController.navigate("detail/$id") },
                        onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                        onOpenStats = { navController.navigate(ROUTE_STATS) },
                    )
                }
                composable(
                    route = ROUTE_EDIT,
                    arguments = listOf(
                        navArgument("recordId") {
                            type = NavType.LongType
                            defaultValue = -1L
                        },
                        navArgument("duplicateId") {
                            type = NavType.LongType
                            defaultValue = -1L
                        },
                    ),
                ) { entry ->
                    val viewModel: RecordEditViewModel = viewModel(factory = factory)
                    val recordId = entry.arguments?.getLong("recordId")?.takeIf { it > 0 }
                    val duplicateId = entry.arguments?.getLong("duplicateId")?.takeIf { it > 0 }
                    RecordEditScreen(
                        viewModel = viewModel,
                        mediaStore = container.mediaStore,
                        recordId = recordId,
                        duplicateId = duplicateId,
                        onBack = { navController.popBackStack() },
                        onSaved = { id ->
                            navController.navigate("detail/$id") {
                                popUpTo(ROUTE_LIST)
                            }
                        },
                    )
                }
                composable(
                    route = ROUTE_DETAIL,
                    arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
                ) { entry ->
                    val recordId = entry.arguments?.getLong("recordId") ?: return@composable
                    val viewModel: RecordDetailViewModel = viewModel(factory = factory)
                    RecordDetailScreen(
                        viewModel = viewModel,
                        recordId = recordId,
                        onBack = { navController.popBackStack() },
                        onEdit = { id -> navController.navigate(editRoute(recordId = id)) },
                        onDuplicate = { id -> navController.navigate(editRoute(duplicateId = id)) },
                        onArchive = { archived ->
                            viewModel.setArchived(archived)
                            navController.popBackStack()
                        },
                        onOpenMedia = { id, index ->
                            navController.navigate("viewer/$id?index=$index")
                        },
                    )
                }
                composable(
                    route = ROUTE_RECORD_MEDIA,
                    arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
                ) { entry ->
                    val recordId = entry.arguments?.getLong("recordId") ?: return@composable
                    val viewModel: RecordMediaViewModel = viewModel(factory = factory)
                    RecordMediaScreen(
                        viewModel = viewModel,
                        recordId = recordId,
                        onBack = { navController.popBackStack() },
                        onOpenDetail = { navController.navigate("detail/$recordId") },
                        onOpenMedia = { index ->
                            navController.navigate("viewer/$recordId?index=$index")
                        },
                        onEdit = { navController.navigate(editRoute(recordId = recordId)) },
                    )
                }
                composable(
                    route = ROUTE_VIEWER,
                    arguments = listOf(
                        navArgument("recordId") { type = NavType.LongType },
                        navArgument("index") {
                            type = NavType.IntType
                            defaultValue = 0
                        },
                    ),
                ) { entry ->
                    val recordId = entry.arguments?.getLong("recordId") ?: return@composable
                    val index = entry.arguments?.getInt("index") ?: 0
                    val viewModel: MediaViewerViewModel = viewModel(factory = factory)
                    MediaViewerScreen(
                        viewModel = viewModel,
                        recordId = recordId,
                        initialIndex = index,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(ROUTE_SETTINGS) {
                    val viewModel: SettingsViewModel = viewModel(factory = factory)
                    SettingsScreen(
                        viewModel = viewModel,
                        updateViewModel = updateViewModel,
                        onBack = { navController.popBackStack() },
                        onOpenGitHub = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
                            runCatching { context.startActivity(intent) }
                        },
                    )
                }
                composable(ROUTE_STATS) {
                    val viewModel: StatsViewModel = viewModel(factory = factory)
                    StatsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        }

        when (val state = updateState) {
            is UpdateUiState.Available,
            is UpdateUiState.Downloading,
            is UpdateUiState.Ready,
            is UpdateUiState.NeedsPermission,
            -> UpdateSheet(
                state = state,
                onDismiss = updateViewModel::dismiss,
                onDownload = updateViewModel::download,
                onInstall = updateViewModel::install,
                onOpenInstallSettings = updateViewModel::openInstallPermissionSettings,
            )
            else -> Unit
        }
        }
    }
}

private fun editRoute(recordId: Long? = null, duplicateId: Long? = null): String =
    "edit?recordId=${recordId ?: -1}&duplicateId=${duplicateId ?: -1}"
