package com.quabtun.louji.ui.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quabtun.louji.R
import com.quabtun.louji.data.db.RecordWithAttachments
import com.quabtun.louji.model.RecordFilter
import com.quabtun.louji.model.RecordStats
import com.quabtun.louji.model.TriState
import com.quabtun.louji.ui.components.EmptyState
import com.quabtun.louji.ui.components.LouJiFilterChip
import com.quabtun.louji.ui.components.StatusStamp
import com.quabtun.louji.ui.theme.LocalLouJiStatusColors
import com.quabtun.louji.ui.theme.TabularNumbers
import com.quabtun.louji.util.canOpenLabel
import com.quabtun.louji.util.doorLabel
import com.quabtun.louji.util.formatDateTime
import com.quabtun.louji.util.placeDisplay
import com.quabtun.louji.util.recordSearchSubtitle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun RecordListScreen(
    viewModel: RecordListViewModel,
    onCreate: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "楼记",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                },
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("settings_button"),
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
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
            Box(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("record_list"),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 4.dp,
                        bottom = 112.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                item { StatsStrip(stats) }
                item {
                    SearchField(
                        query = query,
                        onQueryChange = viewModel::setQuery,
                        focusManager = focusManager,
                    )
                }
                item {
                    FilterRow(
                        selected = filter,
                        onSelect = viewModel::setFilter,
                    )
                }
                if (records.isEmpty()) {
                    item {
                        val clearFilters = {
                            viewModel.setQuery("")
                            viewModel.setFilter(RecordFilter.ALL)
                            focusManager.clearFocus()
                        }
                        EmptyState(
                            title = if (query.isBlank() && filter == RecordFilter.ALL) {
                                "还没有记录"
                            } else {
                                "没有匹配的记录"
                            },
                            body = if (query.isBlank() && filter == RecordFilter.ALL) {
                                "记下第一单，下次到楼下就知道要不要爬、会不会下来拿。"
                            } else {
                                "换个关键词，或把筛选切回全部。"
                            },
                            action = {
                                if (query.isBlank() && filter == RecordFilter.ALL) {
                                    FilledTonalButton(onClick = onCreate) {
                                        Text("记下第一单")
                                    }
                                } else {
                                    TextButton(onClick = clearFilters) {
                                        Text("清除搜索和筛选")
                                    }
                                }
                            },
                        )
                    }
                } else {
                    items(records, key = { it.record.id }) { record ->
                        RecordCard(record = record, onClick = { onOpenRecord(record.record.id) })
                    }
                }
                }
                FloatingActionButton(
                    onClick = onCreate,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 20.dp)
                        .testTag("create_record"),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "记一单")
                }
            }
        }
    }
}

@Composable
private fun StatsStrip(stats: RecordStats) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = LocalLouJiStatusColors.current.rule)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatCell("本月记录", stats.total, Modifier.weight(1f))
            StatDivider()
            StatCell("免爬", stats.noStairs, Modifier.weight(1f))
            StatDivider()
            StatCell("会下楼", stats.willComeDown, Modifier.weight(1f))
        }
        HorizontalDivider(color = LocalLouJiStatusColors.current.rule)
    }
}

@Composable
private fun StatCell(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFeatureSettings = TabularNumbers.fontFeatureSettings,
            ),
            fontWeight = FontWeight.Black,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(30.dp)
            .background(LocalLouJiStatusColors.current.rule),
    )
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusManager: FocusManager,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_field"),
        placeholder = { Text("搜位置、订单号、尾号、房号") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "清除搜索")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
private fun FilterRow(selected: RecordFilter, onSelect: (RecordFilter) -> Unit) {
    val options = listOf(
        RecordFilter.ALL to "全部",
        RecordFilter.STAIRS to "要爬楼",
        RecordFilter.NO_STAIRS to "免爬",
        RecordFilter.WILL_COME_DOWN to "会下来",
        RecordFilter.WITH_MEDIA to "有媒体",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (filter, label) ->
            LouJiFilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(label) },
                modifier = Modifier.testTag("filter_${filter.name}"),
            )
        }
    }
}

@Composable
private fun RecordCard(record: RecordWithAttachments, onClick: () -> Unit) {
    val item = record.record
    val status = LocalLouJiStatusColors.current
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("record_card_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                StatusStamp(noStairs = item.noStairs)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.location,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = recordSearchSubtitle(record),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFeatureSettings = TabularNumbers.fontFeatureSettings,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                val hasPlace = item.floor.isNotBlank() || item.roomNo.isNotBlank()
                Text(
                    text = if (hasPlace) placeDisplay(item.floor, item.roomNo) else "未填",
                    modifier = Modifier.weight(1f),
                    style = if (hasPlace) {
                        MaterialTheme.typography.displaySmall.copy(
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
                if (item.willComeDown == TriState.YES) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = status.noStair,
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "会下来拿",
                            style = MaterialTheme.typography.labelMedium,
                            color = status.noStair,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (item.floor.isBlank() && item.roomNo.isBlank()) "楼层 / 房号未填" else "楼层 / 房号",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val doorLine = buildList {
                if (item.doorIntact != TriState.UNKNOWN) add("门${item.doorIntact.doorLabel()}")
                if (item.canOpenFromAbove != TriState.UNKNOWN) {
                    add("上面${item.canOpenFromAbove.canOpenLabel()}")
                }
            }
            if (doorLine.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = doorLine.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.doorIntact == TriState.NO) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (record.attachments.isNotEmpty()) {
                    Icon(
                        painter = painterResource(R.drawable.ic_gallery),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = record.attachments.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = formatDateTime(item.createdAt),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFeatureSettings = TabularNumbers.fontFeatureSettings,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
