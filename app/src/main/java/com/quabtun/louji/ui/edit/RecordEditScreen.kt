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
    var pendingPaste by remember { mutableStateOf<ParsedOrder?>(null) }
    var pendingFillLabel by remember { mutableStateOf("剪贴板") }
    var showCategorySheetFor by remember { mutableStateOf<Long?>(null) }
    var showPendingCategoryFor by remember { mutableStateOf<Int?>(null) }
    var voiceFailed by remember { mutableStateOf(false) }
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
                is LocationHelper.Result.Success -> viewModel.setLocation(result.place)
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
    ) { uri -> uri?.let { viewModel.recognizeImage(it, attach = true) } }
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                viewModel.applyVoiceText(text)
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
        val hasExisting = ui.draft.location.isNotBlank() ||
            ui.draft.orderNo.isNotBlank() ||
            ui.draft.phoneTail.isNotBlank() ||
            ui.draft.floor.isNotBlank() ||
            ui.draft.roomNo.isNotBlank()
        if (hasExisting) {
            pendingFillLabel = "剪贴板"
            pendingPaste = parsed
        } else {
            viewModel.applyParsed(parsed, overwrite = true)
            scope.launch { snackbar.showSnackbar("已从剪贴板填入：${parsed.labels().joinToString("、")}") }
        }
    }

    LaunchedEffect(autoPaste) {
        if (autoPaste && !autoPasted && ui.initialized) {
            autoPasted = true
            pasteOrder()
        }
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
                                label = if (ui.recognizing) "识别中…" else "识图填单",
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

                if (buildingMatch != null && ui.draft.location.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    val profile = buildingMatch!!
                    InfoBanner(
                        text = buildString {
                            append("已有该楼栋攻略：")
                            append(profile.elevatorType.elevatorLabel())
                            if (profile.accessCode.isNotBlank()) append(" · 门禁 #${profile.accessCode}")
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
                            TriState.YES to "不用爬楼",
                            TriState.NO to "要爬楼",
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

                Spacer(Modifier.height(16.dp))
                // ---- Card 3: customer ----
                FormCard(title = "客户与交付") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ui.draft.phoneTail,
                            onValueChange = viewModel::setPhoneTail,
                            modifier = Modifier.weight(1f).testTag("field_tail"),
                            label = { Text("手机尾号") },
                            singleLine = true,
                            isError = ui.errors.phoneTail != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                        )
                        OutlinedTextField(
                            value = ui.draft.phoneExt,
                            onValueChange = viewModel::setPhoneExt,
                            modifier = Modifier.weight(1f).testTag("field_ext"),
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

                Spacer(Modifier.height(16.dp))
                // ---- Card 4: order + remark ----
                FormCard(title = "订单与备注") {
                    OutlinedTextField(
                        value = ui.draft.orderNo,
                        onValueChange = viewModel::setOrderNo,
                        modifier = Modifier.fillMaxWidth().testTag("field_order"),
                        label = { Text("订单号") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(10.dp))
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
                                    Box {
                                        MediaTile(
                                            type = attachment.type,
                                            filePath = attachment.filePath,
                                            thumbPath = attachment.thumbPath,
                                            durationMs = attachment.durationMs,
                                            onRemove = { viewModel.removeExisting(attachment.id) },
                                        )
                                        if (attachment.type == com.quabtun.louji.model.MediaType.IMAGE) {
                                            IconButton(
                                                onClick = {
                                                    showCategorySheetFor = attachment.id
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .size(30.dp)
                                                    .background(
                                                        androidx.compose.ui.graphics.Color.Black
                                                            .copy(alpha = 0.55f),
                                                        RoundedCornerShape(15.dp),
                                                    ),
                                            ) {
                                                Icon(
                                                    Icons.Filled.Create,
                                                    contentDescription = "标注",
                                                    tint = androidx.compose.ui.graphics.Color.White,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = attachment.category.categoryLabel(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
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
                                    TextButton(
                                        onClick = { showPendingCategoryFor = index },
                                        contentPadding = PaddingValues(2.dp),
                                    ) {
                                        Text(
                                            text = media.category.categoryLabel(),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
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
                    Text("从相册选截图 / 照片 / 视频，或直接拍照录像。")
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
                    TextButton(
                        onClick = {
                            showMediaSheet = false
                            cameraVideo = true
                            showCamera = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("media_video"),
                    ) { Text("录像") }
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
            onDismissRequest = { pendingPaste = null },
            title = { Text("用${pendingFillLabel}填入？") },
            text = { Text("会覆盖已填写的对应字段：\n\n${parsed.summary()}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingPaste = null
                        viewModel.applyParsed(parsed, overwrite = true)
                        scope.launch {
                            snackbar.showSnackbar("已从${pendingFillLabel}填入")
                        }
                    },
                ) { Text("填入") }
            },
            dismissButton = { TextButton(onClick = { pendingPaste = null }) { Text("取消") } },
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
        modifier = modifier.height(78.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
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
