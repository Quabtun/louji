package com.quabtun.louji.ui.media

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.quabtun.louji.data.db.AttachmentEntity
import com.quabtun.louji.model.MediaType
import com.quabtun.louji.ui.components.ConfirmDialog
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(
    viewModel: MediaViewerViewModel,
    recordId: Long,
    initialIndex: Int,
    onBack: () -> Unit,
) {
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletingId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(recordId) { viewModel.load(recordId) }

    if (attachments.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(8.dp),
            ) {
                Text("返回", color = Color.White)
            }
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("附件已删除", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onBack) { Text("返回记录", color = Color.White) }
            }
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, attachments.lastIndex),
        pageCount = { attachments.size },
    )

    LaunchedEffect(attachments.size) {
        if (attachments.isEmpty()) onBack()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 16.dp,
        ) { page ->
            val attachment = attachments.getOrNull(page) ?: return@HorizontalPager
            when (attachment.type) {
                MediaType.IMAGE -> ZoomableImage(attachment, page == pagerState.currentPage)
                MediaType.VIDEO -> VideoPage(
                    attachment = attachment,
                    isCurrent = page == pagerState.currentPage,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.35f)),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.36f)),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "${pagerState.currentPage + 1} / ${attachments.size}",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    deletingId = attachments.getOrNull(pagerState.currentPage)?.id
                    showDeleteDialog = true
                },
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.36f)),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "删除附件", tint = Color.White)
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "删除这个附件？",
            text = "文件会从本机删除，无法恢复。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                showDeleteDialog = false
                deletingId?.let(viewModel::deleteAttachment)
                deletingId = null
                if (attachments.size <= 1) onBack()
            },
            onDismiss = {
                showDeleteDialog = false
                deletingId = null
            },
        )
    }
}

@Composable
private fun ZoomableImage(attachment: AttachmentEntity, isCurrent: Boolean) {
    val file = remember(attachment.filePath) { File(attachment.filePath) }
    var scale by remember(attachment.id) { mutableFloatStateOf(1f) }
    var offset by remember(attachment.id) { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current

    if (!file.isFile) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("图片文件不存在", color = Color.White)
        }
        return
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(file)
                .crossfade(true)
                .build(),
            contentDescription = "现场照片",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(attachment.id) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        },
                    )
                }
                .pointerInput(attachment.id) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, 5f)
                        scale = next
                        offset = if (next <= 1f) {
                            Offset.Zero
                        } else {
                            offset + pan
                        }
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
        if (isCurrent && scale > 1.01f) {
            Text(
                text = "双指缩放 · 双击复位",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPage(attachment: AttachmentEntity, isCurrent: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val file = remember(attachment.filePath) { File(attachment.filePath) }
    if (!file.isFile) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("视频文件不存在", color = Color.White)
        }
        return
    }
    val player = remember(attachment.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }
    DisposableEffect(player) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            player.release()
        }
    }
    LaunchedEffect(isCurrent, player) {
        player.playWhenReady = isCurrent
        if (!isCurrent) player.pause()
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
