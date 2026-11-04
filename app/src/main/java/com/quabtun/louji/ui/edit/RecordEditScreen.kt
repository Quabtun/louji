package com.quabtun.louji.ui.edit

import android.Manifest
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quabtun.louji.R
import com.quabtun.louji.data.LocalMediaStore
import com.quabtun.louji.data.OrderTextRecognizer
import com.quabtun.louji.model.Gender
import com.quabtun.louji.model.PersonalityTags
import com.quabtun.louji.model.TriState
import com.quabtun.louji.ui.camera.CameraCaptureScreen
import com.quabtun.louji.ui.components.ClimbDecisionSelector
import com.quabtun.louji.ui.components.ConfirmDialog
import com.quabtun.louji.ui.components.LouJiFilterChip
import com.quabtun.louji.ui.components.MediaTile
import com.quabtun.louji.ui.components.SectionHeader
import com.quabtun.louji.ui.components.TriStateSelector
import com.quabtun.louji.util.ClipboardOrderParser
import com.quabtun.louji.util.LocationHelper
import com.quabtun.louji.util.ParsedOrder
import com.quabtun.louji.util.label
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
fun RecordEditScreen(
    viewModel: RecordEditViewModel,
    mediaStore: LocalMediaStore,
    recordId: Long?,
    duplicateId: Long?,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val recentLocations by viewModel.recentLocations.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var cameraVideo by remember { mutableStateOf(false) }
    var locationRequestId by remember { mutableStateOf(0) }
    var isLocating by remember { mutableStateOf(false) }
    var pendingPaste by remember { mutableStateOf<ParsedOrder?>(null) }
    var pendingFillLabel by remember { mutableStateOf("剪贴板") }
    var isRecognizing by remember { mutableStateOf(false) }
    val ocrRecognizer = remember { OrderTextRecognizer(context) }
    val formScrollState = rememberScrollState()

    LaunchedEffect(recordId, duplicateId) {
        viewModel.initialize(recordId, duplicateId)
    }
    LaunchedEffect(ui.message) {
        val message = ui.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
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
                    snackbarHostState.showSnackbar("没有定位权限，手动填写位置即可")
                LocationHelper.Result.LocationDisabled ->
                    snackbarHostState.showSnackbar("系统定位已关闭，请打开定位或手动填写位置")
                LocationHelper.Result.Timeout ->
                    snackbarHostState.showSnackbar("当前没有定位信号，手动填写位置即可")
                is LocationHelper.Result.Failed ->
                    snackbarHostState.showSnackbar(
                        result.message ?: "定位失败，手动填写位置即可",
                    )
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
            scope.launch { snackbarHostState.showSnackbar("没有定位权限，手动填写位置即可") }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20),
    ) { uris ->
        viewModel.importUris(uris)
    }

    val attemptBack: () -> Unit = {
        if (ui.saving) {
            scope.launch { snackbarHostState.showSnackbar("正在保存，请稍候") }
        } else if (viewModel.hasUnsavedChanges()) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }
    val hasChanges = viewModel.hasUnsavedChanges()

    fun applyParsedOrder(parsed: ParsedOrder, sourceLabel: String = "剪贴板") {
        parsed.location?.let(viewModel::setLocation)
        parsed.orderNo?.let(viewModel::setOrderNo)
        parsed.phoneTail?.let(viewModel::setPhoneTail)
        parsed.floor?.let(viewModel::setFloor)
        parsed.roomNo?.let(viewModel::setRoomNo)
        parsed.gender?.let(viewModel::setGender)
        val labels = parsed.labels().joinToString("、")
        scope.launch { snackbarHostState.showSnackbar("已从${sourceLabel}填入：$labels") }
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
            scope.launch { snackbarHostState.showSnackbar("剪贴板没有文字或无法读取") }
            return
        }
        val parsed = ClipboardOrderParser.parse(text)
        if (parsed.isEmpty) {
            scope.launch { snackbarHostState.showSnackbar("没识别到订单信息，可手动填写") }
            return
        }
        val hasExisting = ui.draft.location.isNotBlank() ||
            ui.draft.orderNo.isNotBlank() ||
            ui.draft.phoneTail.isNotBlank() ||
            ui.draft.floor.isNotBlank() ||
            ui.draft.roomNo.isNotBlank() ||
            ui.draft.gender != Gender.UNKNOWN
        if (hasExisting) {
            pendingFillLabel = "剪贴板"
            pendingPaste = parsed
        } else {
            applyParsedOrder(parsed)
        }
    }

    fun recognizeOrderImage(uri: android.net.Uri) {
        scope.launch {
            isRecognizing = true
            try {
                val text = ocrRecognizer.recognize(uri)
                if (text.isBlank()) {
                    snackbarHostState.showSnackbar("没识别到文字，可手动填写")
                    return@launch
                }
                val parsed = ClipboardOrderParser.parse(text)
                if (parsed.isEmpty) {
                    snackbarHostState.showSnackbar("截图里没识别到订单信息，可手动填写")
                    return@launch
                }
                val hasExisting = ui.draft.location.isNotBlank() ||
                    ui.draft.orderNo.isNotBlank() ||
                    ui.draft.phoneTail.isNotBlank() ||
                    ui.draft.floor.isNotBlank() ||
                    ui.draft.roomNo.isNotBlank() ||
                    ui.draft.gender != Gender.UNKNOWN
                if (hasExisting) {
                    pendingFillLabel = "订单截图"
                    pendingPaste = parsed
                } else {
                    applyParsedOrder(parsed, "订单截图")
                }
            } finally {
                isRecognizing = false
            }
        }
    }

    val ocrImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { recognizeOrderImage(it) }
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

    BackHandler(enabled = hasChanges || ui.saving) { attemptBack() }

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
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
                    IconButton(onClick = attemptBack, modifier = Modifier.testTag("close_edit")) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )
                    Box(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Button(
                            onClick = { viewModel.save(onSaved) },
                            enabled = ui.initialized && !ui.saving && !ui.importing,
                            modifier = Modifier
                                .widthIn(max = 760.dp)
                                .fillMaxWidth()
                                .height(52.dp)
                                .align(Alignment.Center)
                                .testTag("save_record"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) {
                            if (ui.saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("保存中…")
                            } else {
                                Text(
                                    text = if (ui.isEditing) "保存修改" else "保存记录",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (!ui.initialized) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
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
                        .testTag("form_scroll")
                        .imePadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 4.dp, bottom = 24.dp),
                ) {
                    ClimbDecisionSelector(
                        selected = ui.draft.noStairs,
                        onSelect = viewModel::setNoStairs,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = ui.draft.climbNote,
                        onValueChange = viewModel::setClimbNote,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("field_climb_note"),
                        label = { Text(climbNoteLabel(ui.draft.noStairs)) },
                        placeholder = { Text(climbNotePlaceholder(ui.draft.noStairs)) },
                        supportingText = { Text("选填 · ${ui.draft.climbNote.length}/300") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                    SectionHeader("订单")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = { pasteOrder() },
                            modifier = Modifier.testTag("paste_order"),
                        ) {
                            Text("从剪贴板填入", style = MaterialTheme.typography.labelLarge)
                        }
                        TextButton(
                            onClick = {
                                ocrImageLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                            modifier = Modifier.testTag("ocr_order"),
                        ) {
                            Text(
                                text = if (isRecognizing) "识别中…" else "识别订单截图",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = ui.draft.location,
                        onValueChange = viewModel::setLocation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("field_location"),
                        label = { Text("位置 *") },
                        placeholder = { Text("小区 / 楼栋，如 阳光花园 3 栋") },
                        isError = ui.errors.location != null,
                        supportingText = ui.errors.location?.let { { Text(it) } },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (isLocating) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .semantics {
                                            contentDescription = "正在获取当前位置"
                                        },
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        if (LocationHelper.hasPermission(context)) {
                                            locationRequestId++
                                        } else {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                                ),
                                            )
                                        }
                                    },
                                    modifier = Modifier.testTag("use_location"),
                                ) {
                                    Icon(
                                        Icons.Filled.LocationOn,
                                        contentDescription = "使用当前位置",
                                    )
                                }
                            }
                        },
                    )
                    if (recentLocations.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            recentLocations.take(6).forEach { location ->
                                AssistChip(
                                    onClick = {
                                        viewModel.setLocation(location)
                                        focusManager.clearFocus()
                                    },
                                    label = { Text(location, maxLines = 1) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = ui.draft.orderNo,
                        onValueChange = viewModel::setOrderNo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("field_order"),
                        label = { Text("订单号") },
                        placeholder = { Text("选填") },
                        isError = ui.errors.orderNo != null,
                        supportingText = ui.errors.orderNo?.let { { Text(it) } },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = ui.draft.phoneTail,
                        onValueChange = viewModel::setPhoneTail,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("field_tail"),
                        label = { Text("手机尾号") },
                        placeholder = { Text("4 位数字，选填") },
                        isError = ui.errors.phoneTail != null,
                        supportingText = ui.errors.phoneTail?.let { { Text(it) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "性别",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Gender.MALE to "男",
                            Gender.FEMALE to "女",
                            Gender.UNKNOWN to "未知",
                        ).forEach { (gender, label) ->
                            LouJiFilterChip(
                                selected = ui.draft.gender == gender,
                                onClick = { viewModel.setGender(gender) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("gender_${gender.name}"),
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    SectionHeader("楼层与门")
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = ui.draft.floor,
                            onValueChange = viewModel::setFloor,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("field_floor"),
                            label = { Text("楼层") },
                            placeholder = { Text("7 / B1") },
                            isError = ui.errors.floor != null,
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )
                        OutlinedTextField(
                            value = ui.draft.roomNo,
                            onValueChange = viewModel::setRoomNo,
                            modifier = Modifier
                                .weight(1.35f)
                                .testTag("field_room"),
                            label = { Text("房号") },
                            placeholder = { Text("2-301") },
                            isError = ui.errors.roomNo != null,
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    TriStateSelector(
                        label = "门是否完好",
                        value = ui.draft.doorIntact,
                        onSelect = viewModel::setDoorIntact,
                        yesLabel = "完好",
                        noLabel = "有问题",
                        testTagPrefix = "door",
                    )
                    Spacer(Modifier.height(14.dp))
                    TriStateSelector(
                        label = "上面能开门",
                        value = ui.draft.canOpenFromAbove,
                        onSelect = viewModel::setCanOpenFromAbove,
                        supportingText = "楼上 / 远程能否开门",
                        yesLabel = "能",
                        noLabel = "不能",
                        testTagPrefix = "open_above",
                    )

                    Spacer(Modifier.height(24.dp))
                    SectionHeader("客户")
                    Spacer(Modifier.height(14.dp))
                    TriStateSelector(
                        label = "会下来拿",
                        value = ui.draft.willComeDown,
                        onSelect = viewModel::setWillComeDown,
                        supportingText = "客户是否愿意下楼取",
                        yesLabel = "会",
                        noLabel = "不会",
                        testTagPrefix = "come_down",
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "客户性格",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        PersonalityTags.presets.forEach { tag ->
                            LouJiFilterChip(
                                selected = tag in ui.draft.personalityTags,
                                onClick = { viewModel.toggleTag(tag) },
                                label = { Text(tag) },
                                modifier = Modifier.testTag("tag_$tag"),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ui.draft.personalityNote,
                        onValueChange = viewModel::setPersonalityNote,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("field_note"),
                        label = { Text("性格备注") },
                        placeholder = { Text("好沟通、要求放门口、电话不接…") },
                        isError = ui.errors.note != null,
                        supportingText = {
                            Text(ui.errors.note ?: "${ui.draft.personalityNote.length}/200")
                        },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp),
                    )

                    Spacer(Modifier.height(24.dp))
                    SectionHeader("备注")
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = ui.draft.remark,
                        onValueChange = viewModel::setRemark,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("field_remark"),
                        label = { Text("备注") },
                        placeholder = { Text("门禁、电梯、放门口位置，或任何要记住的事…") },
                        supportingText = { Text("${ui.draft.remark.length}/300") },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(12.dp),
                    )

                    Spacer(Modifier.height(24.dp))
                    SectionHeader("现场")
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val existing = ui.existingMedia.filter { it.id !in ui.removedMediaIds }
                        existing.forEach { attachment ->
                            MediaTile(
                                type = attachment.type,
                                filePath = attachment.filePath,
                                thumbPath = attachment.thumbPath,
                                durationMs = attachment.durationMs,
                                modifier = Modifier.testTag("existing_media_${attachment.id}"),
                                onRemove = { viewModel.removeExisting(attachment.id) },
                            )
                        }
                        ui.pendingMedia.forEachIndexed { index, media ->
                            MediaTile(
                                type = media.type,
                                filePath = media.filePath,
                                thumbPath = media.thumbPath,
                                durationMs = media.durationMs,
                                modifier = Modifier.testTag("pending_media_$index"),
                                onRemove = { viewModel.removePending(media) },
                            )
                        }
                        val count = existing.size + ui.pendingMedia.size
                        if (count < 20) {
                            AddMediaTile(
                                onClick = { showMediaSheet = true },
                                modifier = Modifier.testTag("add_media"),
                            )
                        }
                    }
                    if (ui.importing) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "正在导入附件…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "截图、照片、视频只保存在本机；一条记录最多 20 个附件。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (ui.errors.media != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = ui.errors.media!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "所有内容仅存本机，可在设置中导出备份。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showMediaSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showMediaSheet = false },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Text(
                    text = "添加现场附件",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                MediaSheetRow(
                    icon = R.drawable.ic_camera,
                    title = "拍照",
                    subtitle = "直接拍门口、楼层或门锁",
                    testTag = "media_photo",
                    onClick = {
                        showMediaSheet = false
                        cameraVideo = false
                        showCamera = true
                    },
                )
                MediaSheetRow(
                    icon = R.drawable.ic_video,
                    title = "录像",
                    subtitle = "最长 2 分钟，可带声音",
                    testTag = "media_video",
                    onClick = {
                        showMediaSheet = false
                        cameraVideo = true
                        showCamera = true
                    },
                )
                MediaSheetRow(
                    icon = R.drawable.ic_gallery,
                    title = "从相册选截图 / 照片 / 视频",
                    subtitle = "选系统截图、相册里的图片或视频",
                    testTag = "media_gallery",
                    onClick = {
                        showMediaSheet = false
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                        )
                    },
                )
            }
        }
    }

    if (showDiscardDialog) {
        ConfirmDialog(
            title = "放弃这次记录？",
            text = "还没保存的内容和刚添加的附件会丢失。",
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

    pendingPaste?.let { parsed ->
        AlertDialog(
            onDismissRequest = { pendingPaste = null },
            title = { Text("用${pendingFillLabel}填入？") },
            text = {
                Text("会覆盖已填写的对应字段：\n\n${parsed.summary()}")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingPaste = null
                        applyParsedOrder(parsed, pendingFillLabel)
                    },
                ) { Text("填入") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPaste = null }) { Text("取消") }
            },
        )
    }

    if (ui.loadFailed) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("记录不存在") },
            text = { Text("这条记录可能已经被删除。") },
            confirmButton = {
                TextButton(onClick = onBack) { Text("返回") }
            },
        )
    }
}

@Composable
private fun AddMediaTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .size(92.dp)
            .semantics { contentDescription = "添加现场附件" }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "添加",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun climbNoteLabel(noStairs: TriState): String = when (noStairs) {
    TriState.YES -> "为什么不用爬楼"
    TriState.NO -> "为什么要爬楼"
    TriState.UNKNOWN -> "爬楼情况说明"
}

private fun climbNotePlaceholder(noStairs: TriState): String = when (noStairs) {
    TriState.YES -> "例如：客户会下楼拿、电梯直达、放快递柜…"
    TriState.NO -> "例如：电梯坏了、客户要求送上门、没有电梯…"
    TriState.UNKNOWN -> "例如：还没问到会不会下楼…"
}

@Composable
private fun MediaSheetRow(
    icon: Int,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
