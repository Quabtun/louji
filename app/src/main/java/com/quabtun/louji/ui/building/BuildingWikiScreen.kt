package com.quabtun.louji.ui.building

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quabtun.louji.R
import com.quabtun.louji.data.db.BuildingProfileEntity
import com.quabtun.louji.model.ElevatorType
import com.quabtun.louji.model.elevatorLabel
import com.quabtun.louji.ui.components.CopyableCodeBadge
import com.quabtun.louji.ui.components.LouJiFilterChip
import com.quabtun.louji.ui.components.ModernEmptyState
import com.quabtun.louji.ui.components.PillTone
import com.quabtun.louji.ui.components.SemanticPill
import com.quabtun.louji.ui.theme.LocalLouJiStatusColors
import com.quabtun.louji.ui.theme.TabularNumbers

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun BuildingWikiScreen(
    viewModel: BuildingViewModel,
    onOpenBuilding: (String) -> Unit,
    onCreateRecordAt: (String) -> Unit,
) {
    val buildings by viewModel.buildings.collectAsStateWithLifecycle()
    val ranks by viewModel.rankList.collectAsStateWithLifecycle()
    val filterState = remember { mutableStateOf(BuildingFilter.ALL) }
    val sortState = remember { mutableStateOf(BuildingSort.ORDERS) }
    val filter = filterState.value
    val sort = sortState.value
    var query by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newLocation by remember { mutableStateOf("") }
    val rankMap = remember(ranks) { ranks.associateBy { it.location } }

    Scaffold(
        modifier = Modifier.testTag("building_wiki").semantics { testTagsAsResourceId = true },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("楼栋库", style = MaterialTheme.typography.titleLarge) },
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
            Box(modifier = Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("building_list"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 4.dp,
                        bottom = 112.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it; viewModel.setQuery(it) },
                            modifier = Modifier.fillMaxWidth().testTag("building_search"),
                            placeholder = { Text("搜楼栋名、门禁密码、攻略") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(Icons.Filled.Search, contentDescription = null)
                            },
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            listOf(
                                BuildingFilter.ALL to "全部",
                                BuildingFilter.PINNED to "⭐ 常跑",
                                BuildingFilter.STAIRS_ONLY to "纯步梯",
                                BuildingFilter.HAS_CODE to "有密码",
                                BuildingFilter.HAS_CARD to "有梯控",
                            ).forEach { (value, label) ->
                                LouJiFilterChip(
                                    selected = filter == value,
                                    onClick = {
                                        filterState.value = value
                                        viewModel.setFilter(value)
                                    },
                                    label = { Text(label) },
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.weight(1f))
                            Box {
                                TextButton(
                                    onClick = { showSortMenu = true },
                                    modifier = Modifier.testTag("building_sort"),
                                ) {
                                    Text(
                                        text = "排序：" + when (sort) {
                                            BuildingSort.ORDERS -> "常跑单数"
                                            BuildingSort.NO_STAIR_RATE -> "免爬率"
                                            BuildingSort.UPDATED -> "最近更新"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                ) {
                                    listOf(
                                        BuildingSort.ORDERS to "常跑单数",
                                        BuildingSort.NO_STAIR_RATE to "免爬率",
                                        BuildingSort.UPDATED to "最近更新",
                                    ).forEach { (value, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                sortState.value = value
                                                viewModel.setSort(value)
                                                showSortMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (buildings.isEmpty()) {
                        item {
                            ModernEmptyState(
                                title = "楼栋库还是空的",
                                body = "记一单后会自动生成楼栋攻略；也可以先手动建一个楼栋档案。",
                                icon = painterResource(R.drawable.ic_building),
                                action = {
                                    TextButton(onClick = { showCreateDialog = true }) {
                                        Text("新建楼栋攻略")
                                    }
                                },
                            )
                        }
                    } else {
                        items(buildings, key = { it.id }) { profile ->
                            BuildingCard(
                                profile = profile,
                                orderCount = rankMap[profile.location]?.total ?: 0,
                                noStairCount = rankMap[profile.location]?.noStairs ?: 0,
                                onOpen = { onOpenBuilding(profile.location) },
                                onTogglePin = {
                                    viewModel.save(profile.copy(pinned = !profile.pinned))
                                },
                            )
                        }
                    }
                }
                ExtendedFloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 20.dp)
                        .testTag("create_building"),
                    icon = { Icon(painterResource(R.drawable.ic_building), contentDescription = null) },
                    text = { Text("新建楼栋") },
                )
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新建楼栋攻略") },
            text = {
                OutlinedTextField(
                    value = newLocation,
                    onValueChange = { newLocation = it },
                    modifier = Modifier.fillMaxWidth().testTag("building_name_field"),
                    label = { Text("楼栋 / 小区位置") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val location = newLocation.trim()
                        showCreateDialog = false
                        newLocation = ""
                        if (location.isNotEmpty()) {
                            viewModel.createProfile(location)
                            onOpenBuilding(location)
                        }
                    },
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun BuildingCard(
    profile: BuildingProfileEntity,
    orderCount: Int,
    noStairCount: Int,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val status = LocalLouJiStatusColors.current
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag("building_card_${profile.location}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = profile.location,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(32.dp).testTag("pin_${profile.location}"),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pin),
                        contentDescription = if (profile.pinned) "取消置顶" else "置顶",
                        tint = if (profile.pinned) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    if (profile.elevatorType != ElevatorType.UNKNOWN) {
                        append(profile.elevatorType.elevatorLabel())
                    }
                    if (profile.accessCode.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append("门禁 #").append(profile.accessCode)
                    }
                }.ifBlank { "还没有固定通行信息" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (profile.accessTags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = profile.accessTags.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = status.danger,
                )
            }
            Spacer(Modifier.height(8.dp))
            val rate = if (orderCount > 0) noStairCount * 100 / orderCount else 0
            Text(
                text = "$orderCount 单 · 免爬 $rate%",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFeatureSettings = TabularNumbers.fontFeatureSettings,
                ),
                color = if (orderCount > 0) status.noStair else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
