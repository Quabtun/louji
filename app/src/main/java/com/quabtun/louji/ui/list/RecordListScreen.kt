@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.quabtun.louji.ui.list

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusManager
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.quabtun.louji.R
import com.quabtun.louji.data.db.RecordWithAttachments
import com.quabtun.louji.model.AttachmentCategory
import com.quabtun.louji.model.DateRange
import com.quabtun.louji.model.DateRangePresets
import com.quabtun.louji.model.DeliveryMethod
import com.quabtun.louji.model.ElevatorType
import com.quabtun.louji.model.MediaType
import com.quabtun.louji.model.RecordFilter
import com.quabtun.louji.model.RecordSort
import com.quabtun.louji.model.RecordStats
import com.quabtun.louji.model.TriState
import com.quabtun.louji.model.deliveryLabel
import com.quabtun.louji.model.elevatorShortLabel
import com.quabtun.louji.ui.components.CopyableCodeBadge
import com.quabtun.louji.ui.components.EmptyState
import com.quabtun.louji.ui.components.LouJiFilterChip
import com.quabtun.louji.ui.components.ModernEmptyState
import com.quabtun.louji.ui.components.PillTone
import com.quabtun.louji.ui.components.RiskBanner
import com.quabtun.louji.ui.components.SemanticPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.quabtun.louji.ui.components.StatusStamp
import com.quabtun.louji.ui.components.TileSegmentedSelector
import com.quabtun.louji.ui.theme.LocalLouJiStatusColors
import com.quabtun.louji.ui.theme.TabularNumbers
import com.quabtun.louji.util.formatDateTime
import com.quabtun.louji.util.formatFriendlyTime
import com.quabtun.louji.util.placeDisplay
import com.quabtun.louji.util.recordSearchSubtitle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun RecordListScreen(
    viewModel: RecordListViewModel,
    onCreate: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenBuilding: (String) -> Unit,
    onDuplicate: (Long) -> Unit,
    onQuickEdit: (text: String?, imageUri: Uri?, message: String?) -> Unit,
    focusSearch: Boolean = false,
) {
    val recordsPaged = viewModel.recordsPaged.collectAsLazyPagingItems()
    val fallbackRecords by viewModel.records.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val showArchived by viewModel.showArchived.collectAsStateWithLifecycle()
    val showTrash by viewModel.showTrash.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val dateRange by viewModel.dateRange.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val undoState by viewModel.undoState.collectAsStateWithLifecycle()
    val radar by viewModel.radar.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val autoSaveEnabled by viewModel.autoSaveEnabled.collectAsStateWithLifecycle()
    val quickSaving by viewModel.quickSaving.collectAsStateWithLifecycle()
    val quickEvent by viewModel.quickEvent.collectAsStateWithLifecycle()

    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val searchFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var zoomedImage by remember { mutableStateOf<String?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val usePaged = sort == RecordSort.UPDATED && !showTrash
    val activeConditionCount = listOf(
        showArchived,
        showTrash,
        dateRange.isActive,
        sort != RecordSort.UPDATED,
    ).count { it }

    LaunchedEffect(undoState) {
        if (undoState == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "已移入回收站",
            actionLabel = "撤销",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.restoreDeleted() else viewModel.consumeUndo()
    }
    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            runCatching { searchFocus.requestFocus() }
        }
    }

    val quickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            if (autoSaveEnabled) {
                viewModel.quickSaveImage(uri)
            } else {
                onQuickEdit(null, uri, null)
            }
        }
    }

    fun quickPaste() {
        val text = readClipboard(context)
        if (text.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("剪贴板没有订单文字") }
            return
        }
        if (autoSaveEnabled) {
            viewModel.quickSaveText(text)
        } else {
            onQuickEdit(text, null, null)
        }
    }

    LaunchedEffect(quickEvent) {
        when (val event = quickEvent) {
            is QuickSaveEvent.Saved -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(8_000)
                if ((viewModel.quickEvent.value as? QuickSaveEvent.Saved)?.recordId ==
                    event.recordId
                ) {
                    viewModel.consumeQuickEvent()
                }
            }
            is QuickSaveEvent.Fallback -> {
                viewModel.consumeQuickEvent()
                onQuickEdit(event.text, event.imageUri, event.message)
            }
            null -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = 96.dp),
                )
            },
            topBar = {
                TopAppBar(
                    title = {
                        if (selectionMode) {
                            Text(
                                text = "已选 ${selectedIds.size} 项",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color(0xFF0F172A)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    androidx.compose.foundation.Image(
                                        painter = painterResource(R.drawable.ic_louji_mark),
                                        contentDescription = null,
                                        modifier = Modifier.size(width = 22.dp, height = 14.dp),
                                    )
                                }
                                Spacer(Modifier.width(9.dp))
                                Column {
                                    Text(
                                        text = "楼记",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                    )
                                    Text(
                                        text = "本月 ${stats.total} 单 · 免爬 ${stats.rate()}%",
                                        modifier = Modifier
                                            .testTag("stats_entry")
                                            .clickable(onClick = onOpenInsights),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        if (selectionMode) {
                            IconButton(
                                onClick = viewModel::exitSelectionMode,
                                modifier = Modifier.testTag("selection_cancel"),
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "退出多选")
                            }
                        }
                    },
                    actions = {
                        if (selectionMode) {
                            TextButton(
                                onClick = { viewModel.selectAll(loadedRecords(usePaged, recordsPaged, fallbackRecords)) },
                                modifier = Modifier.testTag("select_all"),
                            ) { Text("全选") }
                        } else {
                            TextButton(
                                onClick = viewModel::enterSelectionMode,
                                modifier = Modifier.testTag("selection_mode"),
                            ) { Text("多选") }
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.testTag("settings_button"),
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = "设置")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(modifier = Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag("record_list"),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 4.dp,
                            bottom = 104.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (!showTrash) {
                            item {
                                SearchField(
                                    query = query,
                                    focusRequester = searchFocus,
                                    onQueryChange = viewModel::setQuery,
                                    focusManager = focusManager,
                                    onPasteSearch = {
                                        val text = readClipboard(context)
                                        if (text.isBlank()) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("剪贴板没有文字")
                                            }
                                        } else {
                                            viewModel.setQuery(text.trim().take(40))
                                        }
                                    },
                                )
                            }
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    QuickActionTile(
                                        title = "截屏秒存",
                                        caption = if (autoSaveEnabled) "OCR · 自动入库" else "OCR · 填入表单",
                                        icon = R.drawable.ic_gallery,
                                        modifier = Modifier.weight(1f).testTag("quick_ocr"),
                                        onClick = {
                                            quickImageLauncher.launch(
                                                PickVisualMediaRequest(
                                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                                ),
                                            )
                                        },
                                    )
                                    QuickActionTile(
                                        title = "粘贴秒存",
                                        caption = if (autoSaveEnabled) "文本 · 自动入库" else "文本 · 填入表单",
                                        icon = R.drawable.ic_export,
                                        modifier = Modifier.weight(1f).testTag("quick_paste"),
                                        onClick = { quickPaste() },
                                    )
                                }
                            }
                            item {
                                ChipRow(
                                    filter = filter,
                                    dateRange = dateRange,
                                    activeConditionCount = activeConditionCount,
                                    onFilter = viewModel::setFilter,
                                    onOpenSheet = { showFilterSheet = true },
                                )
                            }
                            radar?.let { hit ->
                                item { RadarCard(hit = hit, onOpenBuilding = onOpenBuilding) }
                            }
                        }
                        if (showTrash) {
                            item {
                                Text(
                                    text = "回收站里的记录会保留 7 天，之后自动彻底删除。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (usePaged) {
                            val refresh = recordsPaged.loadState.refresh
                            if (refresh is androidx.paging.LoadState.Loading && recordsPaged.itemCount == 0) {
                                item { LoadingBlock() }
                            } else if (recordsPaged.itemCount == 0) {
                                item {
                                    EmptyBlock(
                                        showTrash = showTrash,
                                        query = query,
                                        filter = filter,
                                        onCreate = onCreate,
                                        onClear = {
                                            viewModel.setQuery("")
                                            viewModel.setFilter(RecordFilter.ALL)
                                        },
                                    )
                                }
                            } else {
                                items(
                                    count = recordsPaged.itemCount,
                                    key = recordsPaged.itemKey { it.record.id },
                                ) { index ->
                                    val item = recordsPaged[index] ?: return@items
                                    SwipeableRecordCard(
                                        item = item,
                                        selectionMode = selectionMode,
                                        selected = item.record.id in selectedIds,
                                        onToggleSelect = { viewModel.toggleSelection(item.record.id) },
                                        onOpen = { onOpenDetail(item.record.id) },
                                        onOpenThumbnail = { zoomedImage = it },
                                        onDuplicate = { onDuplicate(item.record.id) },
                                        onTrash = { viewModel.trashOne(item.record.id) },
                                    )
                                }
                            }
                        } else if (fallbackRecords.isEmpty() && isLoading) {
                            item { LoadingBlock() }
                        } else if (fallbackRecords.isEmpty()) {
                            item {
                                EmptyBlock(
                                    showTrash = showTrash,
                                    query = query,
                                    filter = filter,
                                    onCreate = onCreate,
                                    onClear = {
                                        viewModel.setQuery("")
                                        viewModel.setFilter(RecordFilter.ALL)
                                    },
                                )
                            }
                        } else {
                            items(fallbackRecords, key = { it.record.id }) { item ->
                                SwipeableRecordCard(
                                    item = item,
                                    selectionMode = selectionMode,
                                    selected = item.record.id in selectedIds,
                                    onToggleSelect = { viewModel.toggleSelection(item.record.id) },
                                    onOpen = { onOpenDetail(item.record.id) },
                                    onOpenThumbnail = { zoomedImage = it },
                                    onDuplicate = { onDuplicate(item.record.id) },
                                    onTrash = { viewModel.trashOne(item.record.id) },
                                )
                            }
                        }
                    }

                    if (selectionMode) {
                        SelectionActionBar(
                            count = selectedIds.size,
                            showArchive = !showTrash,
                            archived = showArchived,
                            onArchive = { viewModel.archiveSelected(!showArchived) },
                            onTrash = viewModel::trashSelected,
                            onCancel = viewModel::exitSelectionMode,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    } else {
                        FloatingActionButton(
                            onClick = onCreate,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 20.dp, bottom = 20.dp)
                                .testTag("create_record"),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "记一单")
                        }
                    }
                }
            }
        }

        zoomedImage?.let { path ->
            OrderScreenshotOverlay(path = path, onDismiss = { zoomedImage = null })
        }

        val savedEvent = quickEvent as? QuickSaveEvent.Saved
        if (savedEvent != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 96.dp)
                    .testTag("quick_save_bar"),
                actionOnNewLine = true,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.inversePrimary,
                            ),
                            onClick = {
                                viewModel.consumeQuickEvent()
                                onOpenDetail(savedEvent.recordId)
                            },
                        ) { Text("查看详情") }
                        TextButton(
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.inversePrimary,
                            ),
                            onClick = {
                                viewModel.undoQuickSave(savedEvent.recordId)
                                viewModel.consumeQuickEvent()
                                scope.launch { snackbarHostState.showSnackbar("已撤销自动保存") }
                            },
                        ) { Text("撤销") }
                    }
                },
            ) {
                Text("✅ 已自动保存：${savedEvent.label}")
            }
        }

        if (showFilterSheet) {
            FilterSheet(
                showArchived = showArchived,
                showTrash = showTrash,
                sort = sort,
                dateRange = dateRange,
                onDismiss = { showFilterSheet = false },
                onView = { archived, trash ->
                    if (trash) viewModel.setShowTrash(true) else viewModel.setShowArchived(archived)
                },
                onSort = viewModel::setSort,
                onDate = viewModel::setDateRange,
                onReset = {
                    viewModel.setShowArchived(false)
                    viewModel.setSort(RecordSort.UPDATED)
                    viewModel.clearDateRange()
                },
            )
        }

        if (quickSaving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .testTag("quick_saving"),
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
}

@Composable
private fun QuickActionTile(
    title: String,
    caption: String,
    icon: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun RecordStats.rate(): Int = if (total > 0) noStairs * 100 / total else 0

private fun loadedRecords(
    paged: Boolean,
    lazy: androidx.paging.compose.LazyPagingItems<RecordWithAttachments>,
    fallback: List<RecordWithAttachments>,
): List<RecordWithAttachments> = if (paged) {
    (0 until lazy.itemCount).mapNotNull { lazy[it] }
} else {
    fallback
}

private fun readClipboard(context: Context): String = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val clip = clipboard?.primaryClip
    if (clip != null && clip.itemCount > 0) {
        clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
    } else {
        ""
    }
}.getOrDefault("")

@Composable
private fun LoadingBlock() {
    Box(
        modifier = Modifier.fillMaxWidth().height(180.dp),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}

@Composable
private fun EmptyBlock(
    showTrash: Boolean,
    query: String,
    filter: RecordFilter,
    onCreate: () -> Unit,
    onClear: () -> Unit,
) {
    if (showTrash) {
        EmptyState(
            title = "回收站是空的",
            body = "删除的记录会在这里保留 7 天，之后自动彻底删除。",
        )
    } else {
        ModernEmptyState(
            title = if (query.isBlank() && filter == RecordFilter.ALL) "还没有记录" else "没有匹配的记录",
            body = if (query.isBlank() && filter == RecordFilter.ALL) {
                "记下第一单，下次到楼下就知道要不要爬、会不会下来拿。"
            } else {
                "换个关键词，或把筛选切回全部。"
            },
            action = {
                if (query.isBlank() && filter == RecordFilter.ALL) {
                    FilledTonalButton(
                        onClick = onCreate,
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Text("记下第一单")
                    }
                } else {
                    TextButton(onClick = onClear) { Text("清除搜索和筛选") }
                }
            },
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    focusManager: FocusManagerAlias,
    onPasteSearch: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .semantics { contentDescription = "搜索位置、订单号、尾号、房号" }
            .testTag("search_field"),
        placeholder = { Text("搜位置、尾号、房号、门禁密码") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "清除搜索")
                    }
                }
                TextButton(
                    onClick = onPasteSearch,
                    modifier = Modifier.testTag("paste_search"),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text("📋", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

private typealias FocusManagerAlias = androidx.compose.ui.focus.FocusManager

@Composable
private fun ChipRow(
    filter: RecordFilter,
    dateRange: DateRange,
    activeConditionCount: Int,
    onFilter: (RecordFilter) -> Unit,
    onOpenSheet: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            RecordFilter.ALL to "全部",
            RecordFilter.STAIRS to "要爬楼",
            RecordFilter.NO_STAIRS to "免爬",
            RecordFilter.WILL_COME_DOWN to "会下楼",
            RecordFilter.WARNING to "⚠️ 雷区",
        ).forEach { (value, label) ->
            LouJiFilterChip(
                selected = filter == value,
                onClick = { onFilter(value) },
                label = { Text(label) },
                modifier = Modifier.testTag("filter_${value.name}"),
            )
        }
        LouJiFilterChip(
            selected = activeConditionCount > 0 || dateRange.isActive,
            onClick = onOpenSheet,
            label = {
                Text(
                    if (activeConditionCount > 0) {
                        "筛选·排序 ${activeConditionCount}"
                    } else {
                        "筛选·排序"
                    },
                )
            },
            modifier = Modifier.testTag("filter_sheet"),
        )
    }
}

@Composable
private fun RadarCard(
    hit: RecordListViewModel.RadarHit,
    onOpenBuilding: (String) -> Unit,
) {
    val status = LocalLouJiStatusColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("radar_card"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, status.info.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_building),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = status.info,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "秒查结论",
                    style = MaterialTheme.typography.labelMedium,
                    color = status.info,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                if (hit.building != null) {
                    TextButton(onClick = { onOpenBuilding(hit.building.location) }) {
                        Text("楼栋攻略", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            hit.building?.let { building ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = building.location,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (building.elevatorType != ElevatorType.UNKNOWN) {
                        SemanticPill(
                            text = building.elevatorType.elevatorShortLabel(),
                            tone = if (building.elevatorType == ElevatorType.STAIRS_ONLY) {
                                PillTone.CLIMB
                            } else {
                                PillTone.INFO
                            },
                        )
                    }
                    if (building.accessCode.isNotBlank()) {
                        CopyableCodeBadge(code = building.accessCode)
                    }
                    SemanticPill(
                        text = "免爬率 ${hit.buildingStats.noStairRate}%",
                        tone = PillTone.PASS,
                    )
                    if (building.accessTags.isNotEmpty()) {
                        SemanticPill(text = building.accessTags.first(), tone = PillTone.DANGER)
                    }
                }
            }
            hit.customer?.let { customer ->
                Spacer(Modifier.height(10.dp))
                if (hit.building != null) HorizontalDivider(color = status.rule)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "尾号 ${hit.tailLabel} · 已送 ${customer.total} 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SemanticPill(text = "会下楼 ${customer.willComeDown}", tone = PillTone.PASS)
                    SemanticPill(text = "免爬 ${customer.noStairs}", tone = PillTone.PASS)
                }
                if (customer.hasRisk) {
                    Spacer(Modifier.height(8.dp))
                    RiskBanner(
                        title = "历史雷区预警",
                        body = buildString {
                            if (customer.tags.isNotEmpty()) append(customer.tags.joinToString(" / "))
                            if (customer.lastRemark.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(customer.lastRemark)
                            }
                        }.ifBlank { "该客户曾被标记为雷区" },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSheet(
    showArchived: Boolean,
    showTrash: Boolean,
    sort: RecordSort,
    dateRange: DateRange,
    onDismiss: () -> Unit,
    onView: (Boolean, Boolean) -> Unit,
    onSort: (RecordSort) -> Unit,
    onDate: (DateRange) -> Unit,
    onReset: () -> Unit,
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var startPicked by remember { mutableStateOf<Long?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("视图", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            TileSegmentedSelector(
                options = listOf("active" to "活跃", "archived" to "已归档", "trash" to "回收站"),
                selected = when {
                    showTrash -> "trash"
                    showArchived -> "archived"
                    else -> "active"
                },
                onSelect = { value ->
                    onView(value == "archived", value == "trash")
                },
                testTagPrefix = "sheet_view",
            )
            Spacer(Modifier.height(16.dp))
            Text("排序", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            TileSegmentedSelector(
                options = listOf(
                    RecordSort.UPDATED to "最近更新",
                    RecordSort.FLOOR to "楼层高低",
                    RecordSort.LOCATION to "位置名称",
                ),
                selected = sort,
                onSelect = onSort,
                testTagPrefix = "sheet_sort",
            )
            Spacer(Modifier.height(16.dp))
            Text("日期范围", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LouJiFilterChip(
                    selected = !dateRange.isActive,
                    onClick = { onDate(DateRange()) },
                    label = { Text("全部") },
                )
                listOf(7 to "近7天", 30 to "近30天", 90 to "近90天").forEach { (days, label) ->
                    LouJiFilterChip(
                        selected = false,
                        onClick = {
                            onDate(DateRangePresets.lastDays(System.currentTimeMillis(), days))
                        },
                        label = { Text(label) },
                    )
                }
                LouJiFilterChip(
                    selected = false,
                    onClick = { showStartPicker = true },
                    label = { Text("自定义") },
                )
            }
            if (dateRange.isActive) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = dateRangeLabel(dateRange),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onReset) { Text("一键重置") }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("完成") }
            }
        }
    }

    if (showStartPicker) {
        val state = androidx.compose.material3.rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        startPicked = state.selectedDateMillis
                        showStartPicker = false
                        if (state.selectedDateMillis != null) showEndPicker = true
                    },
                ) { Text("下一步") }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("取消") } },
        ) { DatePicker(state = state) }
    }
    if (showEndPicker) {
        val state = androidx.compose.material3.rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEndPicker = false
                        val end = state.selectedDateMillis
                        if (end != null) {
                            val start = startPicked?.let { DateRangePresets.startOfDay(it) } ?: 0L
                            onDate(
                                DateRange(
                                    start = start,
                                    end = DateRangePresets.nextDayStart(
                                        DateRangePresets.startOfDay(end),
                                    ),
                                ),
                            )
                        }
                    },
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("取消") } },
        ) { DatePicker(state = state) }
    }
}

private fun dateRangeLabel(range: DateRange): String {
    if (!range.isActive) return "全部"
    val formatter = SimpleDateFormat("MM/dd", Locale.getDefault())
    return if (range.end == 0L) {
        "自 ${formatter.format(Date(range.start))}"
    } else {
        "${formatter.format(Date(range.start))} – ${formatter.format(Date(range.end - 1))}"
    }
}

@Composable
private fun SwipeableRecordCard(
    item: RecordWithAttachments,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onOpen: () -> Unit,
    onOpenThumbnail: (String) -> Unit,
    onDuplicate: () -> Unit,
    onTrash: () -> Unit,
) {
    if (selectionMode) {
        RecordCard(
            item = item,
            selectionMode = true,
            selected = selected,
            onToggleSelect = onToggleSelect,
            onOpen = onOpen,
            onOpenThumbnail = onOpenThumbnail,
        )
        return
    }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onDuplicate()
                SwipeToDismissBoxValue.EndToStart -> onTrash()
                else -> Unit
            }
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val toEnd = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val status = LocalLouJiStatusColors.current
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        when (dismissState.dismissDirection) {
                            SwipeToDismissBoxValue.StartToEnd -> status.infoContainer
                            SwipeToDismissBoxValue.EndToStart -> status.dangerContainer
                            else -> Color.Transparent
                        },
                    )
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toEnd) Arrangement.Start else Arrangement.End,
            ) {
                Text(
                    text = if (toEnd) "再来一单" else "移到回收站",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (toEnd) status.info else status.danger,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    ) {
        RecordCard(
            item = item,
            selectionMode = false,
            selected = false,
            onToggleSelect = {},
            onOpen = onOpen,
            onOpenThumbnail = onOpenThumbnail,
        )
    }
}

@Composable
private fun RecordCard(
    item: RecordWithAttachments,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onOpen: () -> Unit,
    onOpenThumbnail: (String) -> Unit,
) {
    val record = item.record
    val status = LocalLouJiStatusColors.current
    val orderShot = remember(item.attachments) { item.orderScreenshotPath() }
    val hasPlace = record.floor.isNotBlank() || record.roomNo.isNotBlank()
    Surface(
        onClick = { if (selectionMode) onToggleSelect() else onOpen() },
        modifier = Modifier.fillMaxWidth().testTag("record_card_${record.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            when {
                selected -> MaterialTheme.colorScheme.primary
                record.isWarning -> status.danger.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelect() },
                        modifier = Modifier.testTag("select_${record.id}"),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                StatusStamp(noStairs = record.noStairs)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.location,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = recordSearchSubtitle(item),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFeatureSettings = TabularNumbers.fontFeatureSettings,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (orderShot != null) {
                    Spacer(Modifier.width(10.dp))
                    OrderScreenshotThumbnail(
                        model = orderShot,
                        recordId = record.id,
                        onClick = { onOpenThumbnail(orderShot) },
                    )
                }
            }

            if (hasPlace) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = placeDisplay(record.floor, record.roomNo),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFeatureSettings = TabularNumbers.fontFeatureSettings,
                    ),
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val pills = buildList {
                if (record.accessCode.isNotBlank()) add(("code" to record.accessCode))
                if (record.elevatorType != ElevatorType.UNKNOWN) {
                    add(("elevator" to record.elevatorType.elevatorShortLabel()))
                }
                if (record.willComeDown == TriState.YES) add(("down" to "会下楼"))
                if (record.deliveryMethod != DeliveryMethod.UNKNOWN) {
                    add(("delivery" to record.deliveryMethod.deliveryLabel()))
                }
                if (record.isWarning) add(("warning" to "雷区"))
                if (record.doorIntact == TriState.NO) add(("door" to "门损坏"))
            }
            if (pills.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    pills.forEach { (kind, value) ->
                        when (kind) {
                            "code" -> CopyableCodeBadge(code = value)
                            "elevator" -> SemanticPill(
                                text = value,
                                tone = if (value == "步梯") PillTone.CLIMB else PillTone.NEUTRAL,
                                iconRes = R.drawable.ic_elevator,
                            )
                            "down" -> SemanticPill(text = value, tone = PillTone.PASS)
                            "delivery" -> SemanticPill(text = value, tone = PillTone.NEUTRAL)
                            "warning" -> SemanticPill(
                                text = value,
                                tone = PillTone.DANGER,
                                iconRes = R.drawable.ic_warning,
                            )
                            "door" -> SemanticPill(text = value, tone = PillTone.DANGER)
                        }
                    }
                }
            }

            val summary = record.climbNote.ifBlank { record.remark }
            if (summary.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.attachments.isNotEmpty()) {
                    Icon(
                        painter = painterResource(R.drawable.ic_gallery),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = item.attachments.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = formatFriendlyTime(record.createdAt),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFeatureSettings = TabularNumbers.fontFeatureSettings,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!selectionMode) {
                    TextButton(
                        onClick = onOpen,
                        modifier = Modifier.testTag("details_${record.id}"),
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("详细信息", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionActionBar(
    count: Int,
    showArchive: Boolean,
    archived: Boolean,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .fillMaxWidth()
            .testTag("selection_bar"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("已选 $count 项", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            if (showArchive) {
                TextButton(onClick = onArchive, modifier = Modifier.testTag("batch_archive")) {
                    Text(if (archived) "取消归档" else "归档")
                }
            }
            TextButton(onClick = onTrash, modifier = Modifier.testTag("batch_trash")) {
                Text("移到回收站")
            }
            TextButton(onClick = onCancel, modifier = Modifier.testTag("batch_cancel")) { Text("取消") }
        }
    }
}

private fun RecordWithAttachments.orderScreenshotPath(): String? {
    val preferred = attachments.firstOrNull {
        it.type == MediaType.IMAGE && it.category == AttachmentCategory.ORDER_SCREENSHOT
    }
    val image = preferred ?: attachments.firstOrNull { it.type == MediaType.IMAGE } ?: return null
    return image.thumbPath?.takeIf { File(it).isFile } ?: image.filePath.takeIf { File(it).isFile }
}

@Composable
private fun OrderScreenshotThumbnail(model: String, recordId: Long, onClick: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 84.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "订单截屏缩略图，点击放大" }
            .testTag("order_thumb_$recordId"),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(File(model)).crossfade(true).build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun OrderScreenshotOverlay(path: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (previousStatus != null) controller.isAppearanceLightStatusBars = previousStatus
            if (previousNav != null) controller.isAppearanceLightNavigationBars = previousNav
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .testTag("order_zoom"),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(File(path)).crossfade(true).build(),
            contentDescription = "订单截屏大图，点击关闭",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 56.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .testTag("order_zoom_image"),
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
                .testTag("order_zoom_close"),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "关闭大图", tint = Color.White)
        }
        Text(
            text = "点图片关闭",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.72f),
        )
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
