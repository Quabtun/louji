package com.quabtun.louji.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.quabtun.louji.R
import com.quabtun.louji.data.db.AttachmentEntity
import com.quabtun.louji.data.db.RecordWithAttachments
import com.quabtun.louji.model.MediaType
import com.quabtun.louji.ui.components.EmptyState
import com.quabtun.louji.ui.components.StatusStamp
import com.quabtun.louji.ui.theme.TabularNumbers
import com.quabtun.louji.util.formatDuration
import com.quabtun.louji.util.label
import com.quabtun.louji.util.placeDisplay
import java.io.File
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun RecordMediaScreen(
    viewModel: RecordMediaViewModel,
    recordId: Long,
    onBack: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenMedia: (Int) -> Unit,
    onEdit: () -> Unit,
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    var waited by remember { mutableStateOf(false) }

    LaunchedEffect(recordId) {
        viewModel.load(recordId)
        delay(700)
        waited = true
    }

    Scaffold(
        modifier = Modifier
            .testTag("record_media")
            .semantics { testTagsAsResourceId = true },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = record?.record?.location?.ifBlank { "现场" } ?: "现场",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("media_back")) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenDetail,
                        modifier = Modifier.testTag("media_details"),
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = "详细信息")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when {
            record == null && !waited -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            record == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("记录不存在", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "这条记录可能已经被删除。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onBack) { Text("返回") }
                    }
                }
            }

            else -> {
                val item = record!!
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        MediaHeader(item = item)
                    }
                    if (item.attachments.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(
                                icon = painterResource(R.drawable.ic_gallery),
                                title = "还没有现场图片或视频",
                                body = "拍一张门口、楼层或门锁，下次到楼下就能先认出来。",
                                action = {
                                    Button(
                                        onClick = onEdit,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        ),
                                        modifier = Modifier.testTag("media_add"),
                                    ) {
                                        Text("添加现场附件")
                                    }
                                },
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = item.attachments,
                            key = { _, attachment -> attachment.id },
                        ) { index, attachment ->
                            GalleryTile(
                                attachment = attachment,
                                index = index,
                                onClick = { onOpenMedia(index) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaHeader(item: RecordWithAttachments) {
    val record = item.record
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            StatusStamp(noStairs = record.noStairs, compact = false)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.location,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
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
                    Text(
                        text = meta.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFeatureSettings = TabularNumbers.fontFeatureSettings,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        val hasPlace = record.floor.isNotBlank() || record.roomNo.isNotBlank()
        Text(
            text = if (hasPlace) placeDisplay(record.floor, record.roomNo) else "—",
            style = if (hasPlace) {
                MaterialTheme.typography.displayMedium.copy(
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
        Text(
            text = if (hasPlace) "楼层 / 房号" else "楼层 / 房号未填",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (record.remark.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "备注：${record.remark}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (item.attachments.isEmpty()) "现场" else "${item.attachments.size} 个现场附件",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GalleryTile(
    attachment: AttachmentEntity,
    index: Int,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val model = attachment.thumbPath?.takeIf { File(it).isFile }
        ?: attachment.filePath.takeIf { attachment.type == MediaType.IMAGE }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .testTag("media_gallery_item_$index"),
    ) {
        if (model != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(model))
                    .crossfade(true)
                    .build(),
                contentDescription = if (attachment.type == MediaType.VIDEO) "视频" else "照片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                painter = painterResource(
                    if (attachment.type == MediaType.VIDEO) R.drawable.ic_video else R.drawable.ic_gallery,
                ),
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (attachment.type == MediaType.VIDEO) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = formatDuration(attachment.durationMs),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(
                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                        RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = androidx.compose.ui.graphics.Color.White,
            )
        }
    }
}
