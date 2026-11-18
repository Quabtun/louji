package com.quabtun.louji

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quabtun.louji.data.AppSettings
import com.quabtun.louji.data.OrderAutoSaver
import com.quabtun.louji.data.SharePayload
import com.quabtun.louji.model.BackupReminder
import com.quabtun.louji.ui.AppViewModelFactory
import com.quabtun.louji.ui.building.BuildingDetailScreen
import com.quabtun.louji.ui.building.BuildingViewModel
import com.quabtun.louji.ui.building.BuildingWikiScreen
import com.quabtun.louji.ui.detail.RecordDetailScreen
import com.quabtun.louji.ui.detail.RecordDetailViewModel
import com.quabtun.louji.ui.edit.RecordEditScreen
import com.quabtun.louji.ui.edit.RecordEditViewModel
import com.quabtun.louji.ui.list.RecordListScreen
import com.quabtun.louji.ui.list.RecordListViewModel
import com.quabtun.louji.ui.lock.AppLockScreen
import com.quabtun.louji.ui.media.ImageAnnotationScreen
import com.quabtun.louji.ui.media.MediaViewerScreen
import com.quabtun.louji.ui.media.MediaViewerViewModel
import com.quabtun.louji.ui.media.RecordMediaScreen
import com.quabtun.louji.ui.media.RecordMediaViewModel
import com.quabtun.louji.ui.onboarding.OnboardingScreen
import com.quabtun.louji.ui.settings.SettingsScreen
import com.quabtun.louji.ui.settings.SettingsViewModel
import com.quabtun.louji.ui.stats.StatsScreen
import com.quabtun.louji.ui.stats.StatsViewModel
import com.quabtun.louji.ui.theme.LouJiTheme
import com.quabtun.louji.ui.update.UpdateSheet
import com.quabtun.louji.ui.update.UpdateUiState
import com.quabtun.louji.ui.update.UpdateViewModel
import com.quabtun.louji.util.ClipboardOrderParser
import com.quabtun.louji.util.ParsedOrder
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var shareTick by mutableIntStateOf(0)
    private var shortcutTick by mutableIntStateOf(0)
    private var shortcutKind by mutableStateOf<String?>(null)

    private val container: AppContainer by lazy { (application as LouJiApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            LouJiRoot(
                container = container,
                shareTick = shareTick,
                shortcutTick = shortcutTick,
                shortcutKind = shortcutKind,
                onShortcutConsumed = { shortcutKind = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val stream: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (text != null || stream != null) {
                    container.shareIntake.submit(SharePayload(text = text, imageUri = stream))
                    shareTick++
                }
            }
            else -> {
                val kind = intent.getStringExtra(EXTRA_SHORTCUT)
                if (kind != null) {
                    shortcutKind = kind
                    shortcutTick++
                }
            }
        }
    }
}

private const val EXTRA_SHORTCUT = "louji.shortcut"

private const val ROUTE_LEDGER = "ledger"
private const val ROUTE_BUILDINGS = "buildings"
private const val ROUTE_INSIGHTS = "insights"
private const val ROUTE_ME = "me"
private const val ROUTE_EDIT =
    "edit?recordId={recordId}&duplicateId={duplicateId}&building={building}&pasteNow={pasteNow}"
private const val ROUTE_DETAIL = "detail/{recordId}"
private const val ROUTE_RECORD_MEDIA = "record-media/{recordId}"
private const val ROUTE_VIEWER = "viewer/{recordId}?index={index}"
private const val ROUTE_BUILDING = "building/{location}"
private const val ROUTE_ANNOTATE = "annotate/{attachmentId}"
private const val GITHUB_URL = "https://github.com/Quabtun/louji"

@Composable
private fun LouJiRoot(
    container: AppContainer,
    shareTick: Int,
    shortcutTick: Int,
    shortcutKind: String?,
    onShortcutConsumed: () -> Unit,
) {
    val factory = remember(container) { AppViewModelFactory(container) }
    val updateViewModel: UpdateViewModel = viewModel(factory = factory)
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val updateSheetVisible by updateViewModel.sheetVisible.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var settings by remember { mutableStateOf(AppSettings()) }
    var settingsReady by remember { mutableStateOf(false) }
    var showBackupReminder by remember { mutableStateOf(false) }
    var searchFocusTick by remember { mutableIntStateOf(0) }
    var shareBusy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        container.settings.settings.collect {
            settings = it
            settingsReady = true
        }
    }
    LaunchedEffect(Unit) { updateViewModel.checkOnStartup() }
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
            return@LouJiTheme
        }
        if (!settings.onboardingSeen) {
            OnboardingScreen(
                onStart = { scope.launch { container.settings.setOnboardingSeen(true) } },
            )
            return@LouJiTheme
        }
        if (lockState.locked) {
            AppLockScreen(onUnlocked = container.lock::unlock)
            return@LouJiTheme
        }

        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route
        val showBottomBar = currentRoute in setOf(
            ROUTE_LEDGER,
            ROUTE_BUILDINGS,
            ROUTE_INSIGHTS,
            ROUTE_ME,
        )

        fun switchTab(route: String) {
            navController.navigate(route) {
                popUpTo(ROUTE_LEDGER) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        suspend fun prepareSharedOrder(payload: SharePayload): PreparedShare {
            val text = payload.text?.trim().orEmpty()
            val fromText = if (text.isNotEmpty()) {
                ClipboardOrderParser.parse(text)
            } else {
                ParsedOrder()
            }
            // Copy the screenshot first: the external URI can be revoked as soon as this method
            // returns, and the edit form must still be able to attach it.
            val media = if (payload.imageMedia == null && payload.imageUri != null) {
                runCatching { container.mediaStore.importFromUri(payload.imageUri) }.getOrNull()
            } else {
                payload.imageMedia
            }
            val ocr = payload.ocr ?: payload.imageUri?.let { uri ->
                runCatching { container.orderTextRecognizer.analyze(uri) }.getOrElse {
                    com.quabtun.louji.data.OcrResult(errorReason = "无法读取图片文件")
                }
            }
            val fromImage = ocr?.rawText
                ?.takeIf { it.isNotBlank() }
                ?.let { ClipboardOrderParser.parse(it) }
                ?: ParsedOrder()
            val parsed = if (!fromText.isEmpty) fromText.fillMissingFrom(fromImage) else fromImage
            return PreparedShare(
                parsed = parsed,
                rawText = listOf(text, ocr?.rawText.orEmpty())
                    .filter { it.isNotBlank() }
                    .joinToString("\n"),
                ocr = ocr,
                media = media,
            )
        }

        LaunchedEffect(shareTick) {
            if (shareTick == 0) return@LaunchedEffect
            val payload = container.shareIntake.payload.value ?: return@LaunchedEffect
            if (!settings.autoSaveOrders) {
                if (payload.ocr == null && payload.imageMedia == null) {
                    // Prepare once here so the form never re-reads a revoked external URI.
                    val prepared = runCatching { prepareSharedOrder(payload) }.getOrNull()
                    if (prepared != null) {
                        container.shareIntake.submit(
                            payload.copy(
                                ocr = prepared.ocr,
                                imageMedia = prepared.media,
                                text = payload.text ?: prepared.rawText.takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                }
                navController.navigate(editRoute())
                return@LaunchedEffect
            }
            container.shareIntake.consume()
            shareBusy = true
            val prepared = runCatching { prepareSharedOrder(payload) }
            val result = prepared.mapCatching { item ->
                if (item.parsed.isEmpty) error("empty order")
                container.orderAutoSaver.save(
                    parsed = item.parsed,
                    screenshotUri = if (item.media == null) payload.imageUri else null,
                    sourceLabel = if (payload.imageUri != null) "分享截图" else "分享文字",
                    rawText = item.rawText,
                    preImportedMedia = item.media,
                )
            }
            shareBusy = false
            result.fold(
                onSuccess = { saved ->
                    navController.navigate("detail/${saved.recordId}") {
                        popUpTo(ROUTE_LEDGER)
                    }
                    scope.launch {
                        snackbarHostState.showSnackbar("已自动保存：${saved.label()}")
                    }
                },
                onFailure = { error ->
                    // Hand the screenshot/text back to the form so nothing the rider shared is lost.
                    val preparedShare = prepared.getOrNull()
                    container.shareIntake.submit(
                        payload.copy(
                            ocr = preparedShare?.ocr,
                            imageMedia = preparedShare?.media,
                        ),
                    )
                    navController.navigate(editRoute())
                    val reason = preparedShare?.ocr?.errorReason
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            when {
                                reason != null -> "识别未完成：$reason，已为你打开手动建单"
                                error.message == "empty order" ->
                                    "未识别出有效订单信息，已为你打开手动建单"
                                else -> "保存失败，已为你打开手动建单"
                            },
                        )
                    }
                },
            )
        }
        LaunchedEffect(shortcutTick) {
            if (shortcutTick > 0) {
                when (shortcutKind) {
                    "record" -> navController.navigate(editRoute())
                    "paste" -> navController.navigate(editRoute(pasteNow = true))
                    "search" -> {
                        searchFocusTick++
                        switchTab(ROUTE_LEDGER)
                    }
                }
                onShortcutConsumed()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_LEDGER,
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
                        composable(ROUTE_LEDGER) {
                            val viewModel: RecordListViewModel = viewModel(factory = factory)
                            RecordListScreen(
                                viewModel = viewModel,
                                onCreate = { navController.navigate(editRoute()) },
                                onOpenDetail = { id -> navController.navigate("detail/$id") },
                                onOpenInsights = { switchTab(ROUTE_INSIGHTS) },
                                insightsVisible = settings.showInsightsTab,
                                batchAndArchiveEnabled = settings.enableBatchAndArchive,
                                quickSaveBarVisible = settings.showQuickSaveBar,
                                onOpenBuilding = { location ->
                                    navController.navigate("building/${Uri.encode(location)}")
                                },
                                onDuplicate = { id ->
                                    navController.navigate(editRoute(duplicateId = id))
                                },
                                onQuickEdit = { text, imageUri, message, ocr, media ->
                                    if (message != null) {
                                        scope.launch { snackbarHostState.showSnackbar(message) }
                                    }
                                    container.shareIntake.submit(
                                        SharePayload(
                                            text = text,
                                            imageUri = imageUri,
                                            ocr = ocr,
                                            imageMedia = media,
                                        ),
                                    )
                                    navController.navigate(editRoute())
                                },
                                focusSearch = searchFocusTick > 0,
                            )
                        }
                        composable(ROUTE_BUILDINGS) {
                            val viewModel: BuildingViewModel = viewModel(factory = factory)
                            BuildingWikiScreen(
                                viewModel = viewModel,
                                onOpenBuilding = { location ->
                                    navController.navigate("building/${Uri.encode(location)}")
                                },
                                onCreateRecordAt = { location ->
                                    navController.navigate(editRoute(building = location))
                                },
                            )
                        }
                        composable(ROUTE_INSIGHTS) {
                            val viewModel: StatsViewModel = viewModel(factory = factory)
                            StatsScreen(
                                viewModel = viewModel,
                                onOpenBuilding = { location ->
                                    navController.navigate("building/${Uri.encode(location)}")
                                },
                            )
                        }
                        composable(ROUTE_ME) {
                            val viewModel: SettingsViewModel = viewModel(factory = factory)
                            SettingsScreen(
                                viewModel = viewModel,
                                updateViewModel = updateViewModel,
                                onBack = null,
                                onOpenGitHub = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)),
                                        )
                                    }
                                },
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
                                navArgument("building") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("pasteNow") {
                                    type = NavType.IntType
                                    defaultValue = 0
                                },
                            ),
                        ) { entry ->
                            val viewModel: RecordEditViewModel = viewModel(factory = factory)
                            RecordEditScreen(
                                viewModel = viewModel,
                                mediaStore = container.mediaStore,
                                recordId = entry.arguments?.getLong("recordId")?.takeIf { it > 0 },
                                duplicateId = entry.arguments?.getLong("duplicateId")
                                    ?.takeIf { it > 0 },
                                initialLocation = entry.arguments?.getString("building")
                                    ?.takeIf { it.isNotBlank() },
                                shareIntake = container.shareIntake,
                                autoPaste = entry.arguments?.getInt("pasteNow") == 1,
                                fullFormFields = settings.enableFullFormFields,
                                mediaProTools = settings.enableMediaProTools,
                                onBack = { navController.popBackStack() },
                                onSaved = { id ->
                                    navController.navigate("detail/$id") { popUpTo(ROUTE_LEDGER) }
                                },
                                onOpenAnnotate = { attachmentId ->
                                    navController.navigate("annotate/$attachmentId")
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
                                onDuplicate = { id ->
                                    navController.navigate(editRoute(duplicateId = id))
                                },
                                onArchive = { archived ->
                                    viewModel.setArchived(archived)
                                    navController.popBackStack()
                                },
                                onOpenMedia = { id, index ->
                                    navController.navigate("viewer/$id?index=$index")
                                },
                                onOpenMediaScreen = { id ->
                                    navController.navigate("record-media/$id")
                                },
                                mediaProToolsEnabled = settings.enableMediaProTools,
                                showEmptyDetailFields = settings.showEmptyDetailFields,
                                batchAndArchiveEnabled = settings.enableBatchAndArchive,
                                onOpenBuilding = { location ->
                                    navController.navigate("building/${Uri.encode(location)}")
                                },
                                onOpenAnnotate = { attachmentId ->
                                    navController.navigate("annotate/$attachmentId")
                                },
                            )
                        }
                        composable(
                            route = ROUTE_BUILDING,
                            arguments = listOf(navArgument("location") { type = NavType.StringType }),
                        ) { entry ->
                            val location = entry.arguments?.getString("location").orEmpty()
                            val viewModel: BuildingViewModel = viewModel(factory = factory)
                            BuildingDetailScreen(
                                viewModel = viewModel,
                                mediaStore = container.mediaStore,
                                location = location,
                                onBack = { navController.popBackStack() },
                                onOpenRecord = { id -> navController.navigate("detail/$id") },
                                onCreateRecordAt = { place ->
                                    navController.navigate(editRoute(building = place))
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
                                onEdit = {
                                    navController.navigate(editRoute(recordId = recordId))
                                },
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
                        composable(
                            route = ROUTE_ANNOTATE,
                            arguments = listOf(
                                navArgument("attachmentId") { type = NavType.LongType },
                            ),
                        ) { entry ->
                            val attachmentId = entry.arguments?.getLong("attachmentId")
                                ?: return@composable
                            val viewModel: com.quabtun.louji.ui.media.AnnotationViewModel =
                                viewModel(factory = factory)
                            ImageAnnotationScreen(
                                viewModel = viewModel,
                                attachmentId = attachmentId,
                                onClose = { navController.popBackStack() },
                            )
                        }
                    }
                }

                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        data class Tab(
                            val route: String,
                            val label: String,
                            val icon: Int,
                        )

                        val tabs = buildList {
                            add(Tab(ROUTE_LEDGER, "台账", R.drawable.ic_stairs))
                            if (settings.showBuildingTab) {
                                add(Tab(ROUTE_BUILDINGS, "楼栋库", R.drawable.ic_building))
                            }
                            if (settings.showInsightsTab) {
                                add(Tab(ROUTE_INSIGHTS, "洞察", R.drawable.ic_chart))
                            }
                            add(Tab(ROUTE_ME, "我的", R.drawable.ic_person))
                        }
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = { switchTab(tab.route) },
                                modifier = Modifier.testTag("tab_${tab.route}"),
                                icon = {
                                    Icon(
                                        painter = painterResource(tab.icon),
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(22.dp),
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )

            if (shareBusy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 6.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("正在识别并保存…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        LaunchedEffect(settingsReady) {
            if (settingsReady && BackupReminder.shouldRemind(
                    lastBackupAt = settings.lastBackupAt,
                    now = System.currentTimeMillis(),
                    enabled = settings.backupReminderEnabled,
                )
            ) {
                showBackupReminder = true
            }
        }
        if (showBackupReminder) {
            AlertDialog(
                onDismissRequest = { showBackupReminder = false },
                title = { Text("备份提醒") },
                text = { Text("距离上次备份已超过一周，建议导出一次备份，以防数据丢失。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showBackupReminder = false
                            switchTab(ROUTE_ME)
                        },
                    ) { Text("去备份") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showBackupReminder = false
                            scope.launch {
                                container.settings.setLastBackupAt(System.currentTimeMillis())
                            }
                        },
                    ) { Text("稍后") }
                },
            )
        }

        // Sheet visibility is driven by its own flag so "后台下载" can minimize without the
        // next onProgress tick popping the sheet back open.
        if (updateSheetVisible) {
            when (val state = updateState) {
                is UpdateUiState.Available,
                is UpdateUiState.Downloading,
                is UpdateUiState.Ready,
                is UpdateUiState.NeedsPermission,
                is UpdateUiState.Paused,
                -> UpdateSheet(
                    state = state,
                    onDismiss = updateViewModel::minimize,
                    onDownload = { updateViewModel.download() },
                    onInstall = updateViewModel::install,
                    onOpenInstallSettings = updateViewModel::openInstallPermissionSettings,
                    onPauseDownload = updateViewModel::pauseDownload,
                    onResumeDownload = { updateViewModel.download() },
                    onRestartDownload = { updateViewModel.download(forceRestart = true) },
                )
                else -> Unit
            }
        }
    }
}

private fun editRoute(
    recordId: Long? = null,
    duplicateId: Long? = null,
    building: String? = null,
    pasteNow: Boolean = false,
): String = "edit?recordId=${recordId ?: -1}&duplicateId=${duplicateId ?: -1}" +
    "&building=${Uri.encode(building.orEmpty())}&pasteNow=${if (pasteNow) 1 else 0}"

/** Everything the share pipeline learned once, reused by the edit form on failure. */
private data class PreparedShare(
    val parsed: ParsedOrder,
    val rawText: String,
    val ocr: com.quabtun.louji.data.OcrResult?,
    val media: com.quabtun.louji.model.MediaDraft?,
)
