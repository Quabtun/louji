@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.quabtun.louji.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quabtun.louji.R
import com.quabtun.louji.data.db.RecordWithAttachments
import com.quabtun.louji.model.AttachmentCategory
import com.quabtun.louji.model.DeliveryMethod
import com.quabtun.louji.model.ElevatorType
import com.quabtun.louji.model.TriState
import com.quabtun.louji.model.categoryLabel
import com.quabtun.louji.model.deliveryLabel
import com.quabtun.louji.model.elevatorLabel
import com.quabtun.louji.ui.components.ConfirmDialog
import com.quabtun.louji.ui.components.CopyableCodeBadge
import com.quabtun.louji.ui.components.LedgerRow
import com.quabtun.louji.ui.components.MediaTile
import com.quabtun.louji.ui.components.PillTone
import com.quabtun.louji.ui.components.RiskBanner
import com.quabtun.louji.ui.components.SectionHeader
import com.quabtun.louji.ui.components.SemanticPill
import com.quabtun.louji.ui.components.StatusStamp
import com.quabtun.louji.ui.theme.LocalLouJiStatusColors
import com.quabtun.louji.ui.theme.TabularNumbers
import com.quabtun.louji.util.canOpenLabel
import com.quabtun.louji.util.doorLabel
import com.quabtun.louji.util.formatFullDateTime
import com.quabtun.louji.util.label
import com.quabtun.louji.util.placeDisplay
import com.quabtun.louji.util.shareText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordDetailScreen(
    viewModel: RecordDetailViewModel,
    recordId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onArchive: (Boolean) -> Unit,
    onOpenMedia: (Long, Int) -> Unit,
    onOpenBuilding: (String) -> Unit,
    onOpenAnnotate: (Long) -> Unit,
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPurgeDialog by remember { mutableStateOf(false) }
    var waited by remember { mutableStateOf(false) }

    LaunchedEffect(recordId) {
        viewModel.load(recordId)
        delay(700)
        waited = true
    }

    Scaffold(
        modifier = Modifier.testTag("record_detail").semantics { testTagsAsResourceId = true },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = record?.record?.location ?: "记录",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    record?.let { item ->
                        if (item.record.deletedAt == 0L) {
                            IconButton(
                                onClick = { onEdit(item.record.id) },
                                modifier = Modifier.testTag("detail_edit"),
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "编辑")
                            }
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                if (item.record.deletedAt > 0L) {
                                    DropdownMenuItem(
                                        text = { Text("恢复") },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.restore(onRestored = onBack)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text("彻底删除", color = MaterialTheme.colorScheme.error)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            showPurgeDialog = true
                                        },
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("再来一单") },
                                        onClick = {
                                            menuExpanded = false
                                            onDuplicate(item.record.id)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(if (item.record.archived) "取消归档" else "归档")
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onArchive(!item.record.archived)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("复制摘要") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Share, contentDescription = null)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            clipboard.setText(AnnotatedString(item.shareText()))
                                            scope.launch {
                                                snackbar.showSnackbar("摘要已复制")
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("复制脱敏摘要") },
                                        onClick = {
                                            menuExpanded = false
                                            clipboard.setText(
                                                AnnotatedString(item.desensitisedShareText()),
                                            )
                                            scope.launch {
                                                snackbar.showSnackbar("脱敏摘要已复制")
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "移到回收站",
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            showDeleteDialog = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            record == null && !waited -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            record == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("记录不存在", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "这条记录可能已经被删除。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onBack) { Text("返回") }
                }
            }

            else -> {
                val item = record!!
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
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
                        HeroCard(item = item)
                        if (item.record.isWarning || item.record.doorIntact == TriState.NO) {
                            Spacer(Modifier.height(12.dp))
                            RiskBanner(
                                title = if (item.record.isWarning) "避坑 / 雷区记录" else "门有问题",
                                body = buildString {
                                    if (item.record.doorIntact == TriState.NO) append("门已损坏。")
                                    if (item.record.isWarning) append("这单曾被标记为雷区，注意沟通。")
                                },
                            )
                        }

                        Spacer(Modifier.height(22.dp))
                        SectionHeader("通行与门禁")
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = { onOpenBuilding(item.record.location) },
                                modifier = Modifier.testTag("open_building"),
                            ) { Text("所属楼栋攻略 ›", style = MaterialTheme.typography.labelMedium) }
                        }
                        LedgerRow(label = "位置", value = item.record.location.ifBlank { "未填写" })
                        LedgerRow(
                            label = "楼层 / 房号",
                            value = if (item.record.floor.isBlank() && item.record.roomNo.isBlank()) {
                                "未填写"
                            } else {
                                placeDisplay(item.record.floor, item.record.roomNo)
                            },
                        )
                        LedgerRow(
                            label = "电梯类型",
                            value = item.record.elevatorType.takeIf { it != ElevatorType.UNKNOWN }
                                ?.elevatorLabel() ?: "未填写",
                        )
                        LedgerRow(
                            label = "门禁密码",
                            value = item.record.accessCode.ifBlank { "未填写" },
                        )
                        LedgerRow(
                            label = "门是否完好",
                            value = item.record.doorIntact.doorLabel(),
                            valueColor = if (item.record.doorIntact == TriState.NO) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        LedgerRow(
                            label = "上面能开门",
                            value = item.record.canOpenFromAbove.canOpenLabel(),
                            valueColor = if (item.record.canOpenFromAbove == TriState.NO) {
                                LocalLouJiStatusColors.current.stair
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        LedgerRow(
                            label = "不用爬楼",
                            value = when (item.record.noStairs) {
                                TriState.YES -> "是"
                                TriState.NO -> "否"
                                TriState.UNKNOWN -> "待确认"
                            },
                        )
                        LedgerRow(
                            label = "会下来拿",
                            value = when (item.record.willComeDown) {
                                TriState.YES -> "会"
                                TriState.NO -> "不会"
                                TriState.UNKNOWN -> "未知"
                            },
                            valueColor = if (item.record.willComeDown == TriState.YES) {
                                LocalLouJiStatusColors.current.noStair
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        LedgerRow(
                            label = "爬楼说明",
                            value = item.record.climbNote.ifBlank { "未填写" },
                        )

                        Spacer(Modifier.height(20.dp))
                        SectionHeader("订单")
                        Spacer(Modifier.height(4.dp))
                        LedgerRow(
                            label = "订单号",
                            value = item.record.orderNo.ifBlank { "未填写" },
                        )
                        LedgerRow(label = "性别", value = item.record.gender.label())
                        LedgerRow(
                            label = "手机尾号",
                            value = item.record.phoneTail.ifBlank { "未填写" },
                        )
                        LedgerRow(
                            label = "分机号",
                            value = item.record.phoneExt.ifBlank { "未填写" },
                        )

                        Spacer(Modifier.height(20.dp))
                        SectionHeader("客户")
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SemanticPill(
                                text = if (item.record.willComeDown == TriState.YES) "会下楼" else "不会下楼",
                                tone = if (item.record.willComeDown == TriState.YES) {
                                    PillTone.PASS
                                } else {
                                    PillTone.NEUTRAL
                                },
                                iconRes = R.drawable.ic_elevator,
                            )
                            if (item.record.deliveryMethod != DeliveryMethod.UNKNOWN) {
                                SemanticPill(
                                    text = item.record.deliveryMethod.deliveryLabel(),
                                    tone = PillTone.NEUTRAL,
                                )
                            }
                            item.record.personalityTags.forEach { tag ->
                                SemanticPill(text = tag, tone = PillTone.NEUTRAL)
                            }
                        }
                        if (item.record.personalityNote.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(item.record.personalityNote, style = MaterialTheme.typography.bodyLarge)
                        }

                        Spacer(Modifier.height(20.dp))
                        SectionHeader("备注")
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = item.record.remark.ifBlank { "还没有备注" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (item.record.remark.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )

                        Spacer(Modifier.height(20.dp))
                        SectionHeader("现场（${item.attachments.size}）")
                        Spacer(Modifier.height(10.dp))
                        if (item.attachments.isEmpty()) {
                            Text(
                                text = "还没有现场附件",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                maxItemsInEachRow = 3,
                            ) {
                                item.attachments.forEachIndexed { index, attachment ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        MediaTile(
                                            type = attachment.type,
                                            filePath = attachment.filePath,
                                            thumbPath = attachment.thumbPath,
                                            durationMs = attachment.durationMs,
                                            modifier = Modifier.testTag("media_tile_$index"),
                                            onClick = { onOpenMedia(item.record.id, index) },
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = attachment.category.categoryLabel(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (attachment.type ==
                                                com.quabtun.louji.model.MediaType.IMAGE
                                            ) {
                                                TextButton(
                                                    onClick = { onOpenAnnotate(attachment.id) },
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                                        4.dp,
                                                    ),
                                                ) {
                                                    Text(
                                                        "标注",
                                                        style = MaterialTheme.typography.labelSmall,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        if (item.record.deletedAt > 0L) {
                            OutlinedButton(
                                onClick = { viewModel.restore(onRestored = onBack) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            ) { Text("恢复记录") }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { onEdit(item.record.id) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                ) { Text("编辑记录") }
                                OutlinedButton(
                                    onClick = { onDuplicate(item.record.id) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                ) { Text("再来一单") }
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider(color = LocalLouJiStatusColors.current.rule)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "创建于 ${formatFullDateTime(item.record.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (item.record.updatedAt != item.record.createdAt) {
                            Text(
                                text = "更新于 ${formatFullDateTime(item.record.updatedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "移到回收站？",
            text = "记录和它的附件会一起移入回收站，保留 7 天，之后自动彻底删除。",
            confirmLabel = "移到回收站",
            destructive = false,
            onConfirm = {
                showDeleteDialog = false
                viewModel.trash(onTrashed = onBack)
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
    if (showPurgeDialog) {
        ConfirmDialog(
            title = "彻底删除这条记录？",
            text = "记录和它的附件会立即删除，无法恢复。",
            confirmLabel = "彻底删除",
            destructive = true,
            onConfirm = {
                showPurgeDialog = false
                viewModel.purge(onPurged = onBack)
            },
            onDismiss = { showPurgeDialog = false },
        )
    }
}

@Composable
private fun HeroCard(item: RecordWithAttachments) {
    val record = item.record
    val status = LocalLouJiStatusColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (record.isWarning) status.danger.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusStamp(noStairs = record.noStairs, compact = false)
                Spacer(Modifier.weight(1f))
                if (record.accessCode.isNotBlank()) {
                    CopyableCodeBadge(code = record.accessCode)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = record.location,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            val hasPlace = record.floor.isNotBlank() || record.roomNo.isNotBlank()
            Text(
                text = if (hasPlace) placeDisplay(record.floor, record.roomNo) else "楼层 / 房号未填",
                style = if (hasPlace) {
                    MaterialTheme.typography.displayLarge.copy(
                        fontFeatureSettings = TabularNumbers.fontFeatureSettings,
                    )
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = FontWeight.Black,
                color = if (hasPlace) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (record.elevatorType != ElevatorType.UNKNOWN) {
                    SemanticPill(
                        text = record.elevatorType.elevatorLabel(),
                        tone = if (record.elevatorType == ElevatorType.STAIRS_ONLY) {
                            PillTone.CLIMB
                        } else {
                            PillTone.NEUTRAL
                        },
                        iconRes = R.drawable.ic_elevator,
                    )
                }
                if (record.deliveryMethod != DeliveryMethod.UNKNOWN) {
                    SemanticPill(
                        text = record.deliveryMethod.deliveryLabel(),
                        tone = PillTone.NEUTRAL,
                    )
                }
                if (record.willComeDown == TriState.YES) {
                    SemanticPill(text = "会下楼", tone = PillTone.PASS)
                }
                if (record.isWarning) {
                    SemanticPill(text = "雷区", tone = PillTone.DANGER, iconRes = R.drawable.ic_warning)
                }
            }
        }
    }
}

private fun RecordWithAttachments.desensitisedShareText(): String {
    val record = this.record
    return buildString {
        appendLine("【楼记】${record.location}")
        if (record.floor.isNotBlank()) appendLine("楼层：${record.floor}")
        if (record.accessCode.isNotBlank()) appendLine("门禁：${record.accessCode}")
        appendLine("爬楼：${if (record.noStairs == TriState.YES) "免爬" else "要爬"}")
        if (record.climbNote.isNotBlank()) appendLine("说明：${record.climbNote}")
        appendLine("（已隐去订单号、手机尾号与房号）")
    }.trim()
}
