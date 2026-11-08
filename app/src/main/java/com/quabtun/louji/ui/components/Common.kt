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
    ModernEmptyState(
        title = title,
        body = body,
        modifier = modifier,
        icon = icon,
        action = action,
    )
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

// ---------------------------------------------------------------------------
// 2.0 shared components — compact facts, copyable codes, tile selectors.
// ---------------------------------------------------------------------------

enum class PillTone { PASS, CLIMB, DANGER, INFO, NEUTRAL }

private data class PillColors(val fg: Color, val bg: Color, val border: Color)

@Composable
private fun pillColors(tone: PillTone): PillColors {
    val s = LocalLouJiStatusColors.current
    return when (tone) {
        PillTone.PASS -> PillColors(s.noStair, s.noStairContainer, s.noStair.copy(alpha = 0.35f))
        PillTone.CLIMB -> PillColors(s.stair, s.stairContainer, s.stair.copy(alpha = 0.35f))
        PillTone.DANGER -> PillColors(s.danger, s.dangerContainer, s.danger.copy(alpha = 0.35f))
        PillTone.INFO -> PillColors(s.info, s.infoContainer, s.info.copy(alpha = 0.35f))
        PillTone.NEUTRAL -> PillColors(
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/** Compact structured fact: icon + short label, replaces long plain-text lines. */
@Composable
fun SemanticPill(
    text: String,
    tone: PillTone = PillTone.NEUTRAL,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
) {
    val colors = pillColors(tone)
    Surface(
        modifier = modifier,
        color = colors.bg,
        contentColor = colors.fg,
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

/** Access-code badge: tap to copy, with haptic feedback and a snackbar hint. */
@Composable
fun CopyableCodeBadge(
    code: String,
    modifier: Modifier = Modifier,
    onCopied: (() -> Unit)? = null,
) {
    val colors = pillColors(PillTone.INFO)
    val haptics = LocalHapticFeedback.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(code))
                onCopied?.invoke()
            }
            .testTag("access_code_badge"),
        color = colors.bg,
        contentColor = colors.fg,
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_key),
                contentDescription = "门禁密码，点击复制",
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = "#$code",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFeatureSettings = "tnum",
                ),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Large, glove-friendly segmented selector used for decisions and enum fields. */
@Composable
fun <T> TileSegmentedSelector(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
    columns: Int = 0,
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val rows = if (columns > 0) options.chunked(columns) else listOf(options)
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.forEach { (value, label) ->
                    val isSelected = value == selected
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag(
                                if (testTagPrefix != null) {
                                    "${testTagPrefix}_${options.indexOfFirst { it.first == value }}"
                                } else {
                                    "tile_${options.indexOfFirst { it.first == value }}"
                                },
                            )
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSelect(value)
                                },
                            ),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            Color.Transparent
                        },
                        contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        shape = RoundedCornerShape(9.dp),
                        border = if (isSelected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                        } else {
                            null
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        }
                    }
                }
                if (columns > 0 && row.size < columns) {
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Soft double-ring empty state that never uses a mint block on amber. */
@Composable
fun ModernEmptyState(
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
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
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

/** Coral risk banner used for warning records and door faults. */
@Composable
fun RiskBanner(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    val s = LocalLouJiStatusColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = s.dangerContainer,
        contentColor = s.onDangerContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, s.danger.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_warning),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = s.danger,
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(text = body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Blue tip banner used for building-profile matches. */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val s = LocalLouJiStatusColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = s.infoContainer,
        contentColor = s.onInfoContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, s.info.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_building),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = s.info,
            )
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp)) {
                    Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
