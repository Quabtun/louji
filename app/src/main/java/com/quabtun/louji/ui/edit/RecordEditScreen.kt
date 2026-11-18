package com.quabtun.louji.ui.edit

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quabtun.louji.R
import com.quabtun.louji.data.LocalMediaStore
import com.quabtun.louji.data.ShareIntake
import com.quabtun.louji.model.AttachmentCategory
import com.quabtun.louji.model.ClimbReasons
import com.quabtun.louji.model.DeliveryMethod
import com.quabtun.louji.model.ElevatorType
import com.quabtun.louji.model.Gender
import com.quabtun.louji.model.PersonalityTags
import com.quabtun.louji.model.RecordDraft
import com.quabtun.louji.model.TriState
import com.quabtun.louji.model.categoryLabel
import com.quabtun.louji.model.deliveryLabel
import com.quabtun.louji.model.elevatorLabel
import com.quabtun.louji.ui.camera.CameraCaptureScreen
import com.quabtun.louji.ui.components.ConfirmDialog
import com.quabtun.louji.ui.components.CopyableCodeBadge
import com.quabtun.louji.ui.components.InfoBanner
import com.quabtun.louji.ui.components.MediaTile
import com.quabtun.louji.ui.components.PillTone
import com.quabtun.louji.ui.components.RiskBanner
import com.quabtun.louji.ui.components.SectionHeader
import com.quabtun.louji.ui.components.SemanticPill
import com.quabtun.louji.ui.components.TileSegmentedSelector
import com.quabtun.louji.ui.theme.LocalLouJiStatusColors
import com.quabtun.louji.util.ClipboardOrderParser
import com.quabtun.louji.util.LocationHelper
import com.quabtun.louji.util.ParsedOrder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun RecordEditScreen(
    viewModel: RecordEditViewModel,
    mediaStore: LocalMediaStore,
    recordId: Long?,
    duplicateId: Long?,
    initialLocation: String?,
    shareIntake: ShareIntake,
    autoPaste: Boolean,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onOpenAnnotate: (Long) -> Unit = {},
    fullFormFields: Boolean = true,
    mediaProTools: Boolean = true,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val recentLocations by viewModel.recentLocations.collectAsStateWithLifecycle()
    val buildingMatch by viewModel.buildingMatch.collectAsStateWithLifecycle()
    val customerMatch by viewModel.customerMatch.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var cameraVideo by remember { mutableStateOf(false) }
    var locationRequestId by remember { mutableIntStateOf(0) }
    var isLocating by remember { mutableStateOf(false) }
    var coordinateHint by remember { mutableStateOf<String?>(null) }
    var pendingPaste by remember { mutableStateOf<ParsedOrder?>(null) }
    var pendingFillLabel by remember { mutableStateOf("剪贴板") }
    var pendingDiff by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingAfterApply by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showCategorySheetFor by remember { mutableStateOf<Long?>(null) }
    var showPendingCategoryFor by remember { mutableStateOf<Int?>(null) }
    var voiceFailed by remember { mutableStateOf(false) }
    var expandedThisTime by remember { mutableStateOf(false) }
    // "展开更多不常用字段（本次生效）": one-off escape hatch from simplified mode.
    val effectiveFullFields = fullFormFields || expandedThisTime
    var annotatingPending by remember { mutableStateOf<com.quabtun.louji.model.MediaDraft?>(null) }
    val formScrollState = rememberScrollState()
    var autoPasted by remember { mutableStateOf(false) }

    LaunchedEffect(recordId, duplicateId, initialLocation) {
        viewModel.initialize(recordId, duplicateId, initialLocation)
    }
    LaunchedEffect(ui.message) {
        val message = ui.message
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(locationRequestId) {
        if (locationRequestId == 0) return@LaunchedEffect
        isLocating = true
        try {
            when (val result = LocationHelper.currentPlace(context)) {
                is LocationHelper.Result.Success -> {
                    coordinateHint = null
                    viewModel.setLocation(result.place)
                }
                is LocationHelper.Result.CoordinatesOnly -> {
                    // Keep the coordinates visible as a reference without polluting the field.
                    coordinateHint = result.label
                    snackbar.showSnackbar("暂未获取到中文地名，请手动输入或选常用位置")
                }
                LocationHelper.Result.PermissionMissing ->
                    snackbar.showSnackbar("没有定位权限，手动填写位置即可")
                LocationHelper.Result.LocationDisabled ->
                    snackbar.showSnackbar("系统定位已关闭，请打开定位或手动填写位置")
                LocationHelper.Result.Timeout ->
                    snackbar.showSnackbar("当前没有定位信号，手动填写位置即可")
                is LocationHelper.Result.Failed ->
                    snackbar.showSnackbar(result.message ?: "定位失败，手动填写位置即可")
            }
        } finally {
            isLocating = false
        }
    }

    /**
     * One shared write path for clipboard, OCR and voice. Existing fields only trigger the
     * overwrite dialog when the incoming value actually differs, so the rider sees a real diff.
     */
    fun requestApply(
        parsed: ParsedOrder,
        label: String,
        afterApply: (() -> Unit)? = null,
    ) {
        val diff = ui.draft.pendingDiff(parsed)
        if (diff.isEmpty()) {
            viewModel.applyParsed(parsed, overwrite = true)
            afterApply?.invoke()
            scope.launch {
                snackbar.showSnackbar("已从${label}填入：${parsed.labels().joinToString("、")}")
            }
            return
        }
        pendingFillLabel = label
        pendingDiff = diff
        pendingAfterApply = afterApply
        pendingPaste = parsed
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            locationRequestId++
        } else {
            scope.launch { snackbar.showSnackbar("没有定位权限，手动填写位置即可") }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20),
    ) { uris -> viewModel.importUris(uris) }
    val ocrLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let {
            viewModel.recognizeImage(
                uri = it,
                attach = true,
                onParsed = { parsed -> requestApply(parsed, "识图") },
            )
        }
    }
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                val parsed = ClipboardOrderParser.parse(text)
                if (parsed.isEmpty) {
                    // Pure free-form note: nothing to overwrite, just keep the spoken text.
                    viewModel.applyVoiceRemarks(text)
                    scope.launch { snackbar.showSnackbar("已记录语音内容") }
                } else {
                    requestApply(parsed, "语音") { viewModel.applyVoiceRemarks(text) }
                }
            }
        }
    }

    val attemptBack: () -> Unit = {
        if (ui.saving) {
            scope.launch { snackbar.showSnackbar("正在保存，请稍候") }
        } else if (viewModel.hasUnsavedChanges()) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    fun pasteOrder() {
        val text = runCatching {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
            } else {
                ""
            }
        }.getOrDefault("")
        if (text.isBlank()) {
            scope.launch { snackbar.showSnackbar("剪贴板没有文字或无法读取") }
            return
        }
        val parsed = ClipboardOrderParser.parse(text)
        if (parsed.isEmpty) {
            scope.launch { snackbar.showSnackbar("没识别到订单信息，可手动填写") }
            return
        }
        requestApply(parsed, "剪贴板")
    }

    LaunchedEffect(autoPaste, ui.initialized) {
        if (autoPaste && !autoPasted && ui.initialized) {
            autoPasted = true
            pasteOrder()
        }
    }

    // Annotate a not-yet-saved draft in place; the annotated image replaces the draft and keeps
    // its category (e.g. ORDER_SCREENSHOT) without touching the database.
    annotatingPending?.let { draft ->
        val container = (context.applicationContext as com.quabtun.louji.LouJiApp).container
        val annotationFactory = remember(container) {
            com.quabtun.louji.ui.AppViewModelFactory(container)
        }
        val annotationViewModel: com.quabtun.louji.ui.media.AnnotationViewModel =
            androidx.lifecycle.viewmodel.compose.viewModel(factory = annotationFactory)
        com.quabtun.louji.ui.media.ImageAnnotationScreen(
            viewModel = annotationViewModel,
            attachmentId = -1L,
            onClose = { annotatingPending = null },
            pendingSource = draft,
            onPendingAnnotated = { annotated ->
                viewModel.replacePending(draft, annotated)
                annotatingPending = null
            },
        )
        return
    }

    if (showCamera) {
        CameraCaptureScreen(
            mediaStore = mediaStore,
            initialVideo = cameraVideo,
            onClose = { showCamera = false },
            onCaptured = { draft ->
                viewModel.addCaptured(draft)
                showCamera = false
            },
        )
        return
    }

    Scaffold(
        modifier = Modifier
            .testTag("record_edit")
            .semantics { testTagsAsResourceId = true },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (ui.isEditing) "编辑记录" else "新记录",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = attemptBack,
                        modifier = Modifier.testTag("close_edit"),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(modifier = Modifier.navigationBarsPadding().imePadding()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Simplified mode still lets the rider reach the pro fields for one
                        // session without leaving for Settings.
                        if (!fullFormFields && !expandedThisTime) {
                            TextButton(
                                onClick = { expandedThisTime = true },
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .testTag("expand_more_fields"),
                            ) {
                                Text("展开更多不常用字段（本次生效）")
                            }
                        }
                        Button(
                            onClick = { viewModel.save(onSaved) },
                            enabled = !ui.saving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                                .height(52.dp)
                            .testTag("save_record"),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (ui.saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("保存记录", fontWeight = FontWeight.SemiBold)
                        }
                        }
                    }
                }
            }
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .verticalScroll(formScrollState)
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 24.dp)
                    .testTag("form_scroll"),
            ) {
                // ---- 3-tool workbench ----
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "10 秒填单",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToolTile(
                                label = "粘贴运单",
                                caption = "剪贴板",
                                icon = R.drawable.ic_export,
                                modifier = Modifier.weight(1f).testTag("paste_order"),
                                onClick = { pasteOrder() },
                            )
                            ToolTile(
                                label = "识图填单",
                                caption = "OCR + 挂图",
                                icon = R.drawable.ic_gallery,
                                modifier = Modifier.weight(1f).testTag("ocr_order"),
                                onClick = {
                                    ocrLauncher.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                },
                            )
                            // Voice dictation is a low-frequency power tool: only in full mode,
                            // where it fills the advanced field set too.
                            if (effectiveFullFields) {
                                ToolTile(
                                    label = "语音速记",
                                    caption = "说出房号",
                                    icon = R.drawable.ic_mic,
                                    modifier = Modifier.weight(1f).testTag("voice_input"),
                                    onClick = {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(
                                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                            )
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "说出位置、楼层和房号")
                                        }
                                        try {
                                            voiceLauncher.launch(intent)
                                        } catch (_: ActivityNotFoundException) {
                                            voiceFailed = true
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                if (ui.recognizedFields.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    RecognitionSummary(fields = ui.recognizedFields, draft = ui.draft)
                }

                if (ui.ocrLines.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    OcrLinePicker(
                        lines = ui.ocrLines,
                        onPick = viewModel::applyOcrLine,
                        onDismiss = viewModel::clearOcrLines,
                    )
                }

                if (buildingMatch != null && ui.draft.location.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    val profile = buildingMatch!!
                    InfoBanner(
                        text = buildString {
                            append("已有该楼栋攻略：")
                            append(profile.elevatorType.elevatorLabel())
                            if (profile.accessCode.isNotBlank()) append(" · 门禁 #${profile.accessCode}")
                            if (profile.lockerInfo.isNotBlank()) {
                                append(" · 柜点 ${profile.lockerInfo.take(20)}")
                            }
                            if (profile.accessTags.isNotEmpty()) {
                                append(" · ").append(profile.accessTags.take(3).joinToString("/"))
                            }
                            if (profile.notes.isNotBlank()) {
                                append(" · ").append(profile.notes.take(24))
                            }
                        },
                        actionLabel = "一键带入",
                        onAction = { viewModel.applyBuilding(profile) },
                    )
                }
                customerMatch?.let { customer ->
                    Spacer(Modifier.height(12.dp))
                    if (customer.hasRisk) {
                        RiskBanner(
                            title = "历史雷区预警",
                            body = buildString {
                                append("已送 ${customer.total} 次")
                                if (customer.tags.isNotEmpty()) {
                                    append(" · ").append(customer.tags.joinToString(" / "))
                                }
                                if (customer.lastRemark.isNotBlank()) {
                                    append(" · ").append(customer.lastRemark)
                                }
                            },
                        )
                    } else {
                        InfoBanner(
                            text = "熟客：已送 ${customer.total} 次 · 会下楼 ${customer.willComeDown} 次",
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                // ---- Card 1: decision ----
                FormCard(title = "爬楼决策") {
                    TileSegmentedSelector(
                        options = listOf(
                            TriState.YES to "免爬",
                            TriState.NO to "要爬",
                            TriState.UNKNOWN to "待确认",
                        ),
                        selected = ui.draft.noStairs,
                        onSelect = viewModel::setNoStairs,
                        testTagPrefix = "decision_tile",
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val presets = when (ui.draft.noStairs) {
                            TriState.YES -> ClimbReasons.noStairs
                            TriState.NO -> ClimbReasons.stairs
                            else -> ClimbReasons.uncertain
                        }
                        presets.forEach { reason ->
                            FilterChip(
                                selected = ui.draft.climbNote.contains(reason),
                                onClick = {
                                    val current = ui.draft.climbNote
                                    val next = if (current.isBlank()) {
                                        reason
                                    } else if (current.contains(reason)) {
                                        current.replace("、$reason", "").replace(reason, "")
                                    } else {
                                        "$current、$reason"
                                    }
                                    viewModel.setClimbNote(next.trim('、', ' '))
                                },
                                label = { Text(reason) },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ui.draft.climbNote,
                        onValueChange = viewModel::setClimbNote,
                        modifier = Modifier.fillMaxWidth().testTag("field_climb_note"),
                        label = { Text(climbNoteLabel(ui.draft.noStairs)) },
                        placeholder = { Text(climbNotePlaceholder(ui.draft.noStairs)) },
                        supportingText = { Text("选填 · ${ui.draft.climbNote.length}/300") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))
                // ---- Card 2: place + access ----
                FormCard(title = "位置、楼层与门禁") {
                    OutlinedTextField(
                        value = ui.draft.location,
                        onValueChange = viewModel::setLocation,
                        modifier = Modifier.fillMaxWidth().testTag("field_location"),
                        label = { Text("位置 *") },
                        placeholder = { Text("小区 / 楼栋，如 阳光花园 3 栋") },
                        isError = ui.errors.location != null,
                        supportingText = ui.errors.location?.let { { Text(it) } },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (isLocating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                IconButton(onClick = {
                                    val fine = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    val coarse = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (fine || coarse) {
                                        locationRequestId++
                                    } else {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                            ),
                                        )
                                    }
                                }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_link),
                                        contentDescription = "使用当前位置",
                                    )
                                }
                            }
                        },
                    )
                    if (recentLocations.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            recentLocations.take(6).forEach { place ->
                                FilterChip(
                                    selected = ui.draft.location == place,
                                    onClick = { viewModel.setLocation(place) },
                                    label = { Text(place, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                )
                            }
                        }
                    }
                    coordinateHint?.let { hint ->
                        Spacer(Modifier.height(8.dp))
                        InfoBanner(
                            text = "暂未获取到中文地名，坐标参考 $hint；可手动填写或点上方常用位置。",
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ui.draft.floor,
                            onValueChange = viewModel::setFloor,
                            modifier = Modifier.weight(1f).testTag("field_floor"),
                            label = { Text("楼层") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )
                        OutlinedTextField(
                            value = ui.draft.roomNo,
                            onValueChange = viewModel::setRoomNo,
                            modifier = Modifier.weight(1.4f).testTag("field_room"),
                            label = { Text("房号") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("B1", "1", "2", "3", "4", "5", "6", "7").forEach { floor ->
                            FilterChip(
                                selected = ui.draft.floor == floor,
                                onClick = { viewModel.setFloor(floor) },
                                label = { Text(floor) },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ui.draft.accessCode,
                        onValueChange = viewModel::setAccessCode,
                        modifier = Modifier.fillMaxWidth().testTag("field_access_code"),
                        label = { Text("门禁密码") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_key),
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            if (ui.draft.accessCode.isNotBlank()) {
                                CopyableCodeBadge(code = ui.draft.accessCode)
                            }
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    // 手机尾号 is high-frequency: always visible, even in simplified mode.
                    OutlinedTextField(
                        value = ui.draft.phoneTail,
                        onValueChange = viewModel::setPhoneTail,
                        modifier = Modifier.fillMaxWidth().testTag("field_tail"),
                        label = { Text("手机尾号") },
                        singleLine = true,
                        isError = ui.errors.phoneTail != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                    )
                    // 电梯 / 门锁 / 远程开门 / 楼栋同步 are hardware-profiling fields: they only
                    // matter when the rider maintains the full building guide.
                    if (effectiveFullFields) {
                        Text("电梯类型", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        TileSegmentedSelector(
                            options = ElevatorType.entries.map { it to it.elevatorLabel() },
                            selected = ui.draft.elevatorType,
                            onSelect = viewModel::setElevatorType,
                            columns = 2,
                            testTagPrefix = "elevator",
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("门是否完好", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        TileSegmentedSelector(
                            options = listOf(
                                TriState.YES to "完好",
                                TriState.NO to "有问题",
                                TriState.UNKNOWN to "未知",
                            ),
                            selected = ui.draft.doorIntact,
                            onSelect = viewModel::setDoorIntact,
                            testTagPrefix = "door",
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("上面能否开门", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        TileSegmentedSelector(
                            options = listOf(
                                TriState.YES to "能开门",
                                TriState.NO to "不能开",
                                TriState.UNKNOWN to "未知",
                            ),
                            selected = ui.draft.canOpenFromAbove,
                            onSelect = viewModel::setCanOpenFromAbove,
                            testTagPrefix = "open_above",
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "保存时同步更新楼栋攻略",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Switch(
                                checked = ui.syncBuildingProfile,
                                onCheckedChange = viewModel::setSyncBuildingProfile,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                // ---- Card 3: customer (advanced; hidden in simplified mode) ----
                if (effectiveFullFields) {
                    FormCard(title = "客户与交付") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ui.draft.phoneExt,
                            onValueChange = viewModel::setPhoneExt,
                            modifier = Modifier.fillMaxWidth().testTag("field_ext"),
                            label = { Text("分机号") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("性别", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    TileSegmentedSelector(
                        options = listOf(Gender.MALE to "男", Gender.FEMALE to "女", Gender.UNKNOWN to "未知"),
                        selected = ui.draft.gender,
                        onSelect = viewModel::setGender,
                        testTagPrefix = "gender",
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("客户会下楼吗", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    TileSegmentedSelector(
                        options = listOf(
                            TriState.YES to "会下楼",
                            TriState.NO to "不会",
                            TriState.UNKNOWN to "未知",
                        ),
                        selected = ui.draft.willComeDown,
                        onSelect = viewModel::setWillComeDown,
                        testTagPrefix = "come_down",
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("交付偏好", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    TileSegmentedSelector(
                        options = listOf(
                            DeliveryMethod.DOORSTEP to "放门口",
                            DeliveryMethod.COME_DOWN to "下楼拿",
                            DeliveryMethod.LOCKER to "外卖柜",
                            DeliveryMethod.HAND_TO_HAND to "当面交",
                        ),
                        selected = ui.draft.deliveryMethod,
                        onSelect = viewModel::setDeliveryMethod,
                        columns = 2,
                        testTagPrefix = "delivery",
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = LocalLouJiStatusColors.current.dangerContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_warning),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = LocalLouJiStatusColors.current.danger,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "标记为避坑 / 雷区记录",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalLouJiStatusColors.current.onDangerContainer,
                            )
                            Switch(
                                checked = ui.draft.isWarning,
                                onCheckedChange = viewModel::setWarning,
                                modifier = Modifier.testTag("warning_switch"),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PersonalityTags.presets.forEach { tag ->
                            FilterChip(
                                selected = tag in ui.draft.personalityTags,
                                onClick = { viewModel.toggleTag(tag) },
                                label = { Text(tag) },
                                modifier = Modifier.testTag("tag_$tag"),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = ui.draft.personalityNote,
                        onValueChange = viewModel::setPersonalityNote,
                        modifier = Modifier.fillMaxWidth().testTag("field_note"),
                        label = { Text("性格备注") },
                        supportingText = { Text("选填 · ${ui.draft.personalityNote.length}/200") },
                        minLines = 2,
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                    )
                    }
                }

                Spacer(Modifier.height(16.dp))
                // ---- Card 4: order + remark ----
                FormCard(title = "订单与备注") {
                    if (effectiveFullFields) {
                        OutlinedTextField(
                            value = ui.draft.orderNo,
                            onValueChange = viewModel::setOrderNo,
                            modifier = Modifier.fillMaxWidth().testTag("field_order"),
                            label = { Text("订单号") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    OutlinedTextField(
                        value = ui.draft.remark,
                        onValueChange = viewModel::setRemark,
                        modifier = Modifier.fillMaxWidth().testTag("field_remark"),
                        label = { Text("通用备注") },
                        supportingText = { Text("选填 · ${ui.draft.remark.length}/300") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))
                // ---- Card 5: media ----
                FormCard(title = "现场影像") {
                    if (ui.existingMedia.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ui.existingMedia.forEach { attachment ->
                                if (attachment.id in ui.removedMediaIds) return@forEach
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    MediaTile(
                                        type = attachment.type,
                                        filePath = attachment.filePath,
                                        thumbPath = attachment.thumbPath,
                                        durationMs = attachment.durationMs,
                                        onRemove = { viewModel.removeExisting(attachment.id) },
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    // 48dp text buttons keep the two actions separate and tappable,
                                    // instead of two tiny overlays fighting for the same thumbnail.
                                    // Both are pro tools; simplified mode keeps only the remove (×).
                                    if (mediaProTools) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (attachment.type ==
                                                com.quabtun.louji.model.MediaType.IMAGE
                                            ) {
                                                TextButton(
                                                    onClick = { onOpenAnnotate(attachment.id) },
                                                    modifier = Modifier.testTag(
                                                        "annotate_${attachment.id}",
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_brush),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                    Spacer(Modifier.width(2.dp))
                                                    Text("标注", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                            TextButton(
                                                onClick = { showCategorySheetFor = attachment.id },
                                                contentPadding = PaddingValues(horizontal = 6.dp),
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_tag),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                )
                                                Spacer(Modifier.width(2.dp))
                                                Text(
                                                    text = attachment.category.categoryLabel(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (ui.pendingMedia.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ui.pendingMedia.forEachIndexed { index, media ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    MediaTile(
                                        type = media.type,
                                        filePath = media.filePath,
                                        thumbPath = media.thumbPath,
                                        durationMs = media.durationMs,
                                        modifier = Modifier.testTag("pending_media_$index"),
                                        onRemove = { viewModel.removePending(media) },
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    if (mediaProTools) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (media.type ==
                                                com.quabtun.louji.model.MediaType.IMAGE
                                            ) {
                                                TextButton(
                                                    onClick = { annotatingPending = media },
                                                    modifier = Modifier.testTag(
                                                        "pending_annotate_$index",
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_brush),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                    Spacer(Modifier.width(2.dp))
                                                    Text("标注", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                            TextButton(
                                                onClick = { showPendingCategoryFor = index },
                                                contentPadding = PaddingValues(horizontal = 6.dp),
                                                modifier = Modifier.testTag("pending_category_$index"),
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_tag),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                )
                                                Spacer(Modifier.width(2.dp))
                                                Text(
                                                    text = media.category.categoryLabel(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    OutlinedButton(
                        onClick = { showMediaSheet = true },
                        modifier = Modifier.fillMaxWidth().testTag("add_media"),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("添加现场附件")
                    }
                }
            }
        }
    }

    if (showMediaSheet) {
        AlertDialog(
            onDismissRequest = { showMediaSheet = false },
            title = { Text("添加现场附件") },
            text = {
                Column {
                    Text(
                        if (mediaProTools) {
                            "从相册选截图 / 照片 / 视频，或直接拍照录像。"
                        } else {
                            "从相册选截图 / 照片，或直接拍照。"
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = {
                            showMediaSheet = false
                            galleryLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("从相册选择") }
                    TextButton(
                        onClick = {
                            showMediaSheet = false
                            cameraVideo = false
                            showCamera = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("media_photo"),
                    ) { Text("拍照") }
                    // 录像 is a pro tool: simplified mode keeps the picker to album + photo.
                    if (mediaProTools) {
                        TextButton(
                            onClick = {
                                showMediaSheet = false
                                cameraVideo = true
                                showCamera = true
                            },
                            modifier = Modifier.fillMaxWidth().testTag("media_video"),
                        ) { Text("录像") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMediaSheet = false }) { Text("取消") } },
        )
    }

    if (ui.loadFailed) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("记录不存在") },
            text = { Text("这条记录可能已经被删除。") },
            confirmButton = { TextButton(onClick = onBack) { Text("返回") } },
        )
    }

    if (showDiscardDialog) {
        ConfirmDialog(
            title = "放弃这单？",
            text = "已填写的内容和刚拍的照片不会保存。",
            confirmLabel = "放弃",
            destructive = true,
            onConfirm = {
                showDiscardDialog = false
                viewModel.discard()
                onBack()
            },
            onDismiss = { showDiscardDialog = false },
        )
    }

    if (voiceFailed) {
        AlertDialog(
            onDismissRequest = { voiceFailed = false },
            title = { Text("没有可用的语音识别") },
            text = { Text("这台设备没有语音识别引擎，请改用粘贴或手动填写。") },
            confirmButton = { TextButton(onClick = { voiceFailed = false }) { Text("知道了") } },
        )
    }

    pendingPaste?.let { parsed ->
        AlertDialog(
            onDismissRequest = {
                pendingPaste = null
                pendingAfterApply = null
            },
            title = { Text("用${pendingFillLabel}覆盖已填字段？") },
            text = {
                Column {
                    Text(
                        text = "以下字段会被替换：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    pendingDiff.forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val after = pendingAfterApply
                        pendingPaste = null
                        pendingAfterApply = null
                        pendingDiff = emptyList()
                        viewModel.applyParsed(parsed, overwrite = true)
                        after?.invoke()
                        scope.launch {
                            snackbar.showSnackbar("已从${pendingFillLabel}填入")
                        }
                    },
                ) { Text("覆盖填入") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingPaste = null
                        pendingAfterApply = null
                    },
                ) { Text("取消") }
            },
        )
    }

    showCategorySheetFor?.let { attachmentId ->
        CategoryDialog(
            onDismiss = { showCategorySheetFor = null },
            onPick = { category ->
                showCategorySheetFor = null
                viewModel.setExistingCategory(attachmentId, category)
            },
        )
    }
    showPendingCategoryFor?.let { index ->
        val media = ui.pendingMedia.getOrNull(index)
        CategoryDialog(
            onDismiss = { showPendingCategoryFor = null },
            onPick = { category ->
                showPendingCategoryFor = null
                media?.let { viewModel.setPendingCategory(it, category) }
            },
        )
    }
}

@Composable
private fun ToolTile(
    label: String,
    caption: String,
    icon: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        // min height instead of a fixed one, so 2.0× font can wrap instead of clipping.
        modifier = modifier.heightIn(min = 68.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FormCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 3.dp, height = 14.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun CategoryDialog(onDismiss: () -> Unit, onPick: (AttachmentCategory) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("附件分类") },
        text = {
            Column {
                AttachmentCategory.entries.forEach { category ->
                    TextButton(
                        onClick = { onPick(category) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(category.categoryLabel(), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun climbNoteLabel(noStairs: TriState): String = when (noStairs) {
    TriState.YES -> "为什么不用爬楼"
    TriState.NO -> "为什么要爬楼"
    TriState.UNKNOWN -> "爬楼情况说明"
}

private fun climbNotePlaceholder(noStairs: TriState): String = when (noStairs) {
    TriState.YES -> "如：电梯直达 / 客户下楼拿"
    TriState.NO -> "如：老小区无电梯 / 电梯坏了"
    TriState.UNKNOWN -> "先去楼下看看，回来再补"
}

/** Human-readable diff of filled fields the incoming parse would replace. */
internal fun RecordDraft.pendingDiff(parsed: ParsedOrder): List<String> = buildList {
    fun cmp(label: String, current: String, incoming: String?) {
        val next = incoming?.trim().orEmpty()
        if (next.isNotEmpty() && current.isNotBlank() && current.trim() != next) {
            add("· $label：${current.trim()} → $next")
        }
    }
    cmp("位置", location, parsed.location)
    cmp("订单号", orderNo, parsed.orderNo)
    cmp("尾号", phoneTail, parsed.phoneTail)
    cmp("分机号", phoneExt, parsed.phoneExt)
    cmp("楼层", floor, parsed.floor)
    cmp("房号", roomNo, parsed.roomNo)
    parsed.deliveryMethod?.takeIf { it != DeliveryMethod.UNKNOWN }?.let { incoming ->
        if (deliveryMethod != DeliveryMethod.UNKNOWN && deliveryMethod != incoming) {
            add("· 交付方式：${deliveryMethod.deliveryLabel()} → ${incoming.deliveryLabel()}")
        }
    }
}

/** "已提取：位置 · 房号 · 尾号" plus a red reminder for required fields the parse missed. */@Composable
private fun RecognitionSummary(fields: List<String>, draft: RecordDraft) {
    val status = LocalLouJiStatusColors.current
    val missing = buildList {
        if (draft.location.isBlank()) add("位置")
        if (draft.floor.isBlank()) add("楼层")
        if (draft.roomNo.isBlank()) add("房号")
    }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("recognition_summary"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (missing.isEmpty()) {
                MaterialTheme.colorScheme.outlineVariant
            } else {
                status.danger.copy(alpha = 0.5f)
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = status.noStair,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "识别结果",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "已提取 ${fields.size} 项",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                fields.forEach { label ->
                    SemanticPill(text = label, tone = PillTone.INFO)
                }
            }
            if (missing.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "待补：${missing.joinToString("、")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = status.danger,
                )
            }
        }
    }
}

/**
 * Shows the raw OCR rows when the parser could not recognise order fields, so the rider can tap a
 * row and copy it into the field it belongs to instead of retyping the screenshot by hand.
 */
@Composable
private fun OcrLinePicker(
    lines: List<String>,
    onPick: (String, OcrField) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<String?>(null) }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("ocr_line_picker"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_gallery),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "OCR 识别原文",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("收起") }
            }
            Text(
                text = "点一行，选择要填入的字段",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            lines.take(12).forEachIndexed { index, line ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selected = line }
                        .testTag("ocr_line_$index"),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Text(
                        text = line,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    selected?.let { line ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("填入哪个字段？") },
            text = {
                Column {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    OcrField.entries.forEach { field ->
                        TextButton(
                            onClick = {
                                onPick(line, field)
                                selected = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ocr_field_${field.name}"),
                        ) {
                            Text(field.label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { selected = null }) { Text("取消") } },
        )
    }
}
