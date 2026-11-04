package com.quabtun.louji.ui.detail

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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quabtun.louji.data.db.RecordWithAttachments
import com.quabtun.louji.model.TriState
import com.quabtun.louji.ui.components.ConfirmDialog
import com.quabtun.louji.ui.components.LedgerRow
import com.quabtun.louji.ui.components.MediaTile
import com.quabtun.louji.ui.components.SectionHeader
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun RecordDetailScreen(
    viewModel: RecordDetailViewModel,
    recordId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onArchive: (Boolean) -> Unit,
    onOpenMedia: (Long, Int) -> Unit,
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
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
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back")) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    record?.let { item ->
                        IconButton(
                            onClick = { onEdit(item.record.id) },
                            modifier = Modifier.testTag("detail_edit"),
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑")
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
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
                                            snackbarHostState.showSnackbar("摘要已复制")
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "删除记录",
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            record == null && !waited -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            record == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
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
            }

            else -> {
                val item = record!!
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
                        DetailHeader(item = item)
                        Spacer(Modifier.height(22.dp))
                        SectionHeader("订单")
                        Spacer(Modifier.height(4.dp))
                        LedgerRow(
                            label = "位置",
                            value = item.record.location.ifBlank { "未填写" },
                        )
                        LedgerRow(
                            label = "订单号",
                            value = item.record.orderNo.ifBlank { "未填写" },
                        )
                        LedgerRow(
                            label = "性别",
                            value = item.record.gender.label(),
                        )
                        LedgerRow(
                            label = "手机尾号",
                            value = item.record.phoneTail.ifBlank { "未填写" },
                        )
                        LedgerRow(
                            label = "楼层",
                            value = item.record.floor.ifBlank { "未填写" },
                        )
                        LedgerRow(
                            label = "房号",
                            value = item.record.roomNo.ifBlank { "未填写" },
                        )

                        Spacer(Modifier.height(20.dp))
                        SectionHeader("门与进入")
                        Spacer(Modifier.height(4.dp))
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
                            value = item.record.noStairs.let {
                                when (it) {
                                    TriState.YES -> "是"
                                    TriState.NO -> "否"
                                    TriState.UNKNOWN -> "待确认"
                                }
                            },
                        )
                        LedgerRow(
                            label = "爬楼说明",
                            value = item.record.climbNote.ifBlank { "未填写" },
                        )
                        LedgerRow(
                            label = "会下来拿",
                            value = item.record.willComeDown.let {
                                when (it) {
                                    TriState.YES -> "会"
                                    TriState.NO -> "不会"
                                    TriState.UNKNOWN -> "未知"
                                }
                            },
                            valueColor = if (item.record.willComeDown == TriState.YES) {
                                LocalLouJiStatusColors.current.noStair
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )

                        Spacer(Modifier.height(20.dp))
                        SectionHeader("客户")
                        Spacer(Modifier.height(14.dp))
                        if (item.record.personalityTags.isEmpty() &&
                            item.record.personalityNote.isBlank()
                        ) {
                            Text(
                                text = "还没有记录客户性格",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            if (item.record.personalityTags.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    item.record.personalityTags.forEach { tag ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            shape = RoundedCornerShape(8.dp),
                                        ) {
                                            Text(
                                                text = tag,
                                                modifier = Modifier.padding(
                                                    horizontal = 10.dp,
                                                    vertical = 5.dp,
                                                ),
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        }
                                    }
                                }
                                if (item.record.personalityNote.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                            if (item.record.personalityNote.isNotBlank()) {
                                Text(
                                    text = item.record.personalityNote,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        SectionHeader("备注")
                        Spacer(Modifier.height(14.dp))
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
                        SectionHeader("现场")
                        Spacer(Modifier.height(14.dp))
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
                                    MediaTile(
                                        type = attachment.type,
                                        filePath = attachment.filePath,
                                        thumbPath = attachment.thumbPath,
                                        durationMs = attachment.durationMs,
                                        modifier = Modifier.testTag("media_tile_$index"),
                                        onClick = { onOpenMedia(item.record.id, index) },
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { onEdit(item.record.id) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("编辑记录")
                            }
                            OutlinedButton(
                                onClick = { onDuplicate(item.record.id) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("再来一单")
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider(
                            color = LocalLouJiStatusColors.current.rule,
                        )
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
            title = "删除这条记录？",
            text = "记录和它的附件会一起删除，无法恢复。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete(onDeleted = onBack)
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun DetailHeader(item: RecordWithAttachments) {
    val record = item.record
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = record.location,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(12.dp))
            StatusStamp(noStairs = record.noStairs, compact = false)
        }
        Spacer(Modifier.height(16.dp))
        val hasPlace = record.floor.isNotBlank() || record.roomNo.isNotBlank()
        Text(
            text = if (hasPlace) "楼层 / 房号" else "楼层 / 房号未填",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (hasPlace) placeDisplay(record.floor, record.roomNo) else "—",
            style = if (hasPlace) {
                MaterialTheme.typography.displayLarge.copy(
                    fontFeatureSettings = TabularNumbers.fontFeatureSettings,
                )
            } else {
                MaterialTheme.typography.displaySmall
            },
            fontWeight = FontWeight.Black,
            color = if (hasPlace) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = buildList {
            if (record.gender != com.quabtun.louji.model.Gender.UNKNOWN) {
                add(record.gender.label())
            }
            if (record.phoneTail.isNotBlank()) add("尾号 ${record.phoneTail}")
            if (record.orderNo.isNotBlank()) add("订单 ${record.orderNo}")
        }
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = meta.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFeatureSettings = TabularNumbers.fontFeatureSettings,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
