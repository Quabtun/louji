package com.quabtun.louji.ui.building

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quabtun.louji.R
import com.quabtun.louji.data.db.BuildingProfileEntity
import com.quabtun.louji.data.db.RecordWithAttachments
import com.quabtun.louji.model.AccessTagPresets
import com.quabtun.louji.model.ElevatorType
import com.quabtun.louji.model.FloorMath
import com.quabtun.louji.model.TriState
import com.quabtun.louji.model.elevatorLabel
import com.quabtun.louji.ui.components.CopyableCodeBadge
import com.quabtun.louji.ui.components.InfoBanner
import com.quabtun.louji.ui.components.PillTone
import com.quabtun.louji.ui.components.SectionHeader
import com.quabtun.louji.ui.components.SemanticPill
import com.quabtun.louji.ui.components.StatusStamp
import com.quabtun.louji.ui.components.TileSegmentedSelector
import com.quabtun.louji.ui.theme.LocalLouJiStatusColors
import com.quabtun.louji.ui.theme.TabularNumbers
import com.quabtun.louji.util.canOpenLabel
import com.quabtun.louji.util.doorLabel
import com.quabtun.louji.util.placeDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun BuildingDetailScreen(
    viewModel: BuildingViewModel,
    location: String,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    onCreateRecordAt: (String) -> Unit,
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val records by viewModel.locationRecords.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showEdit by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var includeCode by remember { mutableStateOf(false) }

    LaunchedEffect(location) { viewModel.load(location) }

    Scaffold(
        modifier = Modifier.testTag("building_detail").semantics { testTagsAsResourceId = true },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(location, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("building_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.togglePinned() },
                        modifier = Modifier.testTag("building_pin"),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_pin),
                            contentDescription = "置顶常跑",
                            tint = if (profile?.pinned == true) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(
                        onClick = { showShare = true },
                        modifier = Modifier.testTag("building_share"),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "分享攻略卡")
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
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 32.dp),
            ) {
                val current = profile
                if (current == null) {
                    InfoBanner("这个楼栋还没有档案，先建一份固定通行攻略。")
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.createProfile(location) },
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("创建楼栋攻略") }
                } else {

                SectionHeader("楼栋通行档案")
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SemanticPill(
                                text = current.elevatorType.elevatorLabel(),
                                tone = if (current.elevatorType == ElevatorType.STAIRS_ONLY) {
                                    PillTone.CLIMB
                                } else {
                                    PillTone.NEUTRAL
                                },
                                iconRes = R.drawable.ic_elevator,
                            )
                            Spacer(Modifier.width(6.dp))
                            if (current.accessCode.isNotBlank()) {
                                CopyableCodeBadge(code = current.accessCode)
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { showEdit = true }) { Text("编辑") }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (current.doorIntact != TriState.UNKNOWN) {
                                SemanticPill(
                                    text = "门${current.doorIntact.doorLabel()}",
                                    tone = if (current.doorIntact == TriState.NO) {
                                        PillTone.DANGER
                                    } else {
                                        PillTone.PASS
                                    },
                                )
                            }
                            if (current.canOpenFromAbove != TriState.UNKNOWN) {
                                SemanticPill(
                                    text = "上面${current.canOpenFromAbove.canOpenLabel()}",
                                    tone = if (current.canOpenFromAbove == TriState.YES) {
                                        PillTone.PASS
                                    } else {
                                        PillTone.NEUTRAL
                                    },
                                )
                            }
                        }
                        if (current.lockerInfo.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "外卖柜 / 存放点：${current.lockerInfo}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (current.accessTags.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                current.accessTags.forEach { tag ->
                                    SemanticPill(text = tag, tone = PillTone.DANGER)
                                }
                            }
                        }
                        if (current.notes.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = current.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "累计 ${stats.total} 单",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFeatureSettings = TabularNumbers.fontFeatureSettings,
                                ),
                            )
                            Text(
                                text = "免爬 ${stats.noStairRate}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = LocalLouJiStatusColors.current.noStair,
                            )
                            Text(
                                text = "雷区 ${stats.warnings}",
                                style = MaterialTheme.typography.labelMedium,
                                color = LocalLouJiStatusColors.current.danger,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onCreateRecordAt(location) },
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("record_at_building"),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("在此楼栋记一单")
                }

                Spacer(Modifier.height(22.dp))
                SectionHeader("本栋历史房号（${records.size}）")
                Spacer(Modifier.height(8.dp))
                if (records.isEmpty()) {
                    Text(
                        text = "还没有在这栋楼下过单。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val sorted = records.sortedWith(
                        compareByDescending<RecordWithAttachments> {
                            FloorMath.climbedFloor(it.record.floor) ?: -1
                        }.thenByDescending { it.record.createdAt },
                    )
                    sorted.forEach { item ->
                        HouseholdRow(item = item, onClick = { onOpenRecord(item.record.id) })
                        Spacer(Modifier.height(8.dp))
                    }
                }
                }
            }
        }
    }

    if (showEdit && profile != null) {
        BuildingEditDialog(
            profile = profile!!,
            onDismiss = { showEdit = false },
            onSave = { updated ->
                showEdit = false
                viewModel.save(updated)
            },
        )
    }

    if (showShare && profile != null) {
        val current = profile!!
        AlertDialog(
            onDismissRequest = { showShare = false },
            title = { Text("分享楼栋攻略卡") },
            text = {
                Column {
                    Text(
                        "只包含楼栋公共通行经验，不包含订单号、手机尾号、房号与客户评价。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("包含门禁密码", modifier = Modifier.weight(1f))
                        Switch(
                            checked = includeCode,
                            onCheckedChange = { includeCode = it },
                            modifier = Modifier.testTag("share_include_code"),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showShare = false
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                BuildingShareCard.render(context, current, stats, includeCode)
                            }
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file,
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(intent, "分享楼栋攻略"),
                                )
                            }
                        }
                    },
                ) { Text("生成并分享") }
            },
            dismissButton = {
                TextButton(onClick = { showShare = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun HouseholdRow(item: RecordWithAttachments, onClick: () -> Unit) {
    val record = item.record
    val status = LocalLouJiStatusColors.current
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("household_${record.id}"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (record.isWarning) status.danger.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (record.floor.isBlank() && record.roomNo.isBlank()) {
                    "未填"
                } else {
                    placeDisplay(record.floor, record.roomNo)
                },
                modifier = Modifier.width(120.dp),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFeatureSettings = TabularNumbers.fontFeatureSettings,
                ),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusStamp(noStairs = record.noStairs)
            Spacer(Modifier.width(10.dp))
            Text(
                text = record.climbNote.ifBlank { record.remark },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (record.isWarning) {
                Spacer(Modifier.width(6.dp))
                SemanticPill(text = "雷区", tone = PillTone.DANGER)
            }
        }
    }
}

@Composable
private fun BuildingEditDialog(
    profile: BuildingProfileEntity,
    onDismiss: () -> Unit,
    onSave: (BuildingProfileEntity) -> Unit,
) {
    var elevator by remember { mutableStateOf(profile.elevatorType) }
    var code by remember { mutableStateOf(profile.accessCode) }
    var door by remember { mutableStateOf(profile.doorIntact) }
    var above by remember { mutableStateOf(profile.canOpenFromAbove) }
    var locker by remember { mutableStateOf(profile.lockerInfo) }
    var notes by remember { mutableStateOf(profile.notes) }
    var tags by remember { mutableStateOf(profile.accessTags.toSet()) }
    var pinned by remember { mutableStateOf(profile.pinned) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑楼栋攻略") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("电梯类型", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                TileSegmentedSelector(
                    options = ElevatorType.entries.map { it to it.elevatorLabel() },
                    selected = elevator,
                    onSelect = { elevator = it },
                    columns = 2,
                    testTagPrefix = "elevator",
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(24) },
                    label = { Text("门禁密码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("building_code_field"),
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_key),
                            contentDescription = null,
                        )
                    },
                )
                Spacer(Modifier.height(10.dp))
                Text("门是否完好", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TriState.YES to "完好", TriState.NO to "有问题", TriState.UNKNOWN to "未知")
                        .forEach { (state, label) ->
                            FilterChip(
                                selected = door == state,
                                onClick = { door = state },
                                label = { Text(label) },
                            )
                        }
                }
                Spacer(Modifier.height(10.dp))
                Text("上面能否开门", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TriState.YES to "能", TriState.NO to "不能", TriState.UNKNOWN to "未知")
                        .forEach { (state, label) ->
                            FilterChip(
                                selected = above == state,
                                onClick = { above = state },
                                label = { Text(label) },
                            )
                        }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = locker,
                    onValueChange = { locker = it.take(200) },
                    label = { Text("外卖柜 / 存放点") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text("通行提醒", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AccessTagPresets.presets.forEach { tag ->
                        FilterChip(
                            selected = tag in tags,
                            onClick = {
                                tags = if (tag in tags) tags - tag else tags + tag
                            },
                            label = { Text(tag) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(500) },
                    label = { Text("楼栋攻略备注") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("置顶常跑", modifier = Modifier.weight(1f))
                    Switch(checked = pinned, onCheckedChange = { pinned = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        profile.copy(
                            elevatorType = elevator,
                            accessCode = code.trim(),
                            doorIntact = door,
                            canOpenFromAbove = above,
                            lockerInfo = locker.trim(),
                            accessTags = tags.toList(),
                            notes = notes.trim(),
                            pinned = pinned,
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
