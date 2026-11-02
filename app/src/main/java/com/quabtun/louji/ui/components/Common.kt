package com.quabtun.louji.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.quabtun.louji.R
import com.quabtun.louji.model.MediaType
import com.quabtun.louji.model.TriState
import com.quabtun.louji.ui.theme.LocalLouJiStatusColors
import com.quabtun.louji.ui.theme.StampShape
import java.io.File

/** Keeps long forms readable on tablets and landscape without stretching the phone layout. */
@Composable
fun ContentColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
fun StatusStamp(
    noStairs: TriState,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
) {
    val status = LocalLouJiStatusColors.current
    val background: Color
    val foreground: Color
    val label: String
    val icon: Painter?
    when (noStairs) {
        TriState.YES -> {
            background = status.noStairContainer
            foreground = status.onNoStairContainer
            label = if (compact) "免爬" else "不用爬楼"
            icon = null
        }
        TriState.NO -> {
            background = status.stairContainer
            foreground = status.onStairContainer
            label = if (compact) "要爬" else "要爬楼"
            icon = painterResource(R.drawable.ic_stairs)
        }
        TriState.UNKNOWN -> {
            background = status.unknownContainer
            foreground = status.onUnknownContainer
            label = "待确认"
            icon = null
        }
    }
    Surface(
        modifier = modifier,
        color = background,
        contentColor = foreground,
        shape = StampShape,
        border = BorderStroke(1.5.dp, foreground.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            } else if (noStairs == TriState.YES) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun ClimbDecisionSelector(
    selected: TriState,
    onSelect: (TriState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "这单要爬楼吗？",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DecisionOption(
                label = "不用爬楼",
                caption = "省一趟",
                selected = selected == TriState.YES,
                accent = LocalLouJiStatusColors.current.noStair,
                container = LocalLouJiStatusColors.current.noStairContainer,
                onContainer = LocalLouJiStatusColors.current.onNoStairContainer,
                modifier = Modifier.weight(1f),
                testTag = "decision_no_stairs",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelect(TriState.YES)
                },
            )
            DecisionOption(
                label = "要爬楼",
                caption = "带好门禁",
                selected = selected == TriState.NO,
                accent = LocalLouJiStatusColors.current.stair,
                container = LocalLouJiStatusColors.current.stairContainer,
                onContainer = LocalLouJiStatusColors.current.onStairContainer,
                modifier = Modifier.weight(1f),
                testTag = "decision_stairs",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelect(TriState.NO)
                },
            )
            DecisionOption(
                label = "待确认",
                caption = "下次再看",
                selected = selected == TriState.UNKNOWN,
                accent = LocalLouJiStatusColors.current.unknown,
                container = LocalLouJiStatusColors.current.unknownContainer,
                onContainer = LocalLouJiStatusColors.current.onUnknownContainer,
                modifier = Modifier.weight(1f),
                testTag = "decision_unknown",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelect(TriState.UNKNOWN)
                },
            )
        }
    }
}

@Composable
private fun DecisionOption(
    label: String,
    caption: String,
    selected: Boolean,
    accent: Color,
    container: Color,
    onContainer: Color,
    modifier: Modifier = Modifier,
    testTag: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = modifier
            .heightIn(min = 74.dp)
            .clip(shape)
            .testTag(testTag)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        color = if (selected) container else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) onContainer else MaterialTheme.colorScheme.onSurface,
        shape = shape,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) onContainer.copy(alpha = 0.78f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun LouJiFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
fun TriStateSelector(
    label: String,
    value: TriState,
    onSelect: (TriState) -> Unit,
    modifier: Modifier = Modifier,
    yesLabel: String = "是",
    noLabel: String = "否",
    unknownLabel: String = "未知",
    supportingText: String? = null,
    testTagPrefix: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                TriState.YES to yesLabel,
                TriState.NO to noLabel,
                TriState.UNKNOWN to unknownLabel,
            ).forEach { (state, text) ->
                LouJiFilterChip(
                    selected = value == state,
                    onClick = { onSelect(state) },
                    label = { Text(text) },
                    modifier = if (testTagPrefix != null) {
                        Modifier.testTag(
                            "${testTagPrefix}_" + when (state) {
                                TriState.YES -> "yes"
                                TriState.NO -> "no"
                                TriState.UNKNOWN -> "unknown"
                            },
                        )
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(2.dp),
                    ),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = LocalLouJiStatusColors.current.rule)
    }
}

@Composable
fun LedgerRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(104.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top,
        ) {
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(16.dp),
                    tint = valueColor,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = valueColor,
            )
        }
    }
}

@Composable
fun MediaTile(
    type: MediaType,
    filePath: String,
    thumbPath: String?,
    durationMs: Long,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val model = thumbPath
        ?.takeIf { File(it).isFile }
        ?: filePath.takeIf { type == MediaType.IMAGE }
    Box(
        modifier = modifier
            .size(92.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (model != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(model))
                    .crossfade(true)
                    .build(),
                contentDescription = if (type == MediaType.VIDEO) "视频" else "照片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                painter = painterResource(
                    if (type == MediaType.VIDEO) R.drawable.ic_video else R.drawable.ic_gallery,
                ),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (type == MediaType.VIDEO) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(30.dp)
                    .background(Color.Black.copy(alpha = 0.48f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = com.quabtun.louji.util.formatDuration(durationMs),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(30.dp)
                    .background(Color.Black.copy(alpha = 0.52f), RoundedCornerShape(15.dp)),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "移除附件",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: Painter? = painterResource(R.drawable.ic_stairs),
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(14.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
fun ThinRule(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = LocalLouJiStatusColors.current.rule,
    )
}

@Composable
fun UnknownHint(text: String = "未填写") {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun WarnIcon(description: String = "注意") {
    Icon(
        Icons.Filled.Warning,
        contentDescription = description,
        tint = MaterialTheme.colorScheme.error,
    )
}

@Composable
fun InfoIcon(description: String = "说明") {
    Icon(
        Icons.Filled.Info,
        contentDescription = description,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
