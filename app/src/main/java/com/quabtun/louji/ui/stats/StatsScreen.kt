package com.quabtun.louji.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quabtun.louji.model.BuildingRank
import com.quabtun.louji.model.DayBucket
import com.quabtun.louji.model.FloorMath
import com.quabtun.louji.ui.components.ModernEmptyState
import com.quabtun.louji.ui.components.SectionHeader
import com.quabtun.louji.ui.components.TileSegmentedSelector
import com.quabtun.louji.ui.theme.LocalLouJiStatusColors
import com.quabtun.louji.ui.theme.TabularNumbers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onOpenBuilding: (String) -> Unit,
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val rankings by viewModel.rankings.collectAsStateWithLifecycle()
    val heatmap by viewModel.heatmap.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    var leaderboardTab by remember { mutableStateOf(0) }

    Scaffold(
        modifier = Modifier.testTag("stats_screen").semantics { testTagsAsResourceId = true },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("洞察", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
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
                TileSegmentedSelector(
                    options = listOf(
                        StatsPeriod.TODAY to "今日",
                        StatsPeriod.WEEK to "本周",
                        StatsPeriod.MONTH to "本月",
                        StatsPeriod.ALL to "全部",
                    ),
                    selected = period,
                    onSelect = viewModel::setPeriod,
                    testTagPrefix = "period",
                )
                Spacer(Modifier.height(16.dp))

                MilestoneCard(data = data)
                Spacer(Modifier.height(20.dp))

                SectionHeader("爬楼决策分布")
                Spacer(Modifier.height(12.dp))
                DonutCard(data = data)
                Spacer(Modifier.height(20.dp))

                SectionHeader("近 14 天趋势")
                Spacer(Modifier.height(12.dp))
                TrendCard(buckets = data.buckets.takeLast(14))
                Spacer(Modifier.height(20.dp))

                SectionHeader("跑单活跃度（近 12 周）")
                Spacer(Modifier.height(12.dp))
                HeatmapCard(buckets = heatmap)
                Spacer(Modifier.height(22.dp))

                SectionHeader("楼栋红黑榜")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { leaderboardTab = 0 }) {
                        Text(
                            "💚 神仙免爬榜",
                            fontWeight = if (leaderboardTab == 0) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    TextButton(onClick = { leaderboardTab = 1 }) {
                        Text(
                            "🔥 硬核爬楼榜",
                            fontWeight = if (leaderboardTab == 1) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
                val list = if (leaderboardTab == 0) {
                    rankings.sortedWith(
                        compareByDescending<BuildingRank> { it.noStairs * 100 / maxOf(it.total, 1) }
                            .thenByDescending { it.total },
                    )
                } else {
                    rankings.sortedWith(
                        compareByDescending<BuildingRank> { it.stairs }
                            .thenByDescending { it.total },
                    )
                }.take(10)
                if (list.isEmpty()) {
                    ModernEmptyState(
                        title = "还没有足够的数据",
                        body = "多记几单后，这里会排出常跑楼栋的免爬率与爬楼榜。",
                        icon = null,
                    )
                } else {
                    list.forEachIndexed { index, rank ->
                        RankRow(index = index, rank = rank, onClick = { onOpenBuilding(rank.location) })
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MilestoneCard(data: StatsData) {
    val status = LocalLouJiStatusColors.current
    val totals = data.totals
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "🏔️ 骑手爬楼里程碑",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = totals.floorsClimbed.toString(),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFeatureSettings = TabularNumbers.fontFeatureSettings,
                    ),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "层",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "≈ ${"%.1f".format(FloorMath.kilometres(totals.floorsClimbed))} km 垂直",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = FloorMath.achievement(totals.floorsClimbed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = status.rule)
            Spacer(Modifier.height(12.dp))
            Row {
                MiniStat("总单数", totals.recordCount.toString(), Modifier.weight(1f))
                MiniStat(
                    "免爬率",
                    if (totals.recordCount > 0) "${totals.noStairs * 100 / totals.recordCount}%" else "0%",
                    Modifier.weight(1f),
                    status.noStair,
                )
                MiniStat(
                    "会下楼",
                    if (totals.recordCount > 0) "${totals.willComeDown * 100 / totals.recordCount}%" else "0%",
                    Modifier.weight(1f),
                    status.noStair,
                )
                MiniStat(
                    "约消耗",
                    "${FloorMath.kilocalories(totals.floorsClimbed)} kcal",
                    Modifier.weight(1.2f),
                )
            }
        }
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFeatureSettings = TabularNumbers.fontFeatureSettings,
            ),
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DonutCard(data: StatsData) {
    val status = LocalLouJiStatusColors.current
    val decision = data.decision
    val total = maxOf(decision.total, 1)
    val slices = listOf(
        decision.noStairs to status.noStair,
        decision.stairs to status.stair,
        decision.unknown to status.unknown,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    var start = -90f
                    val stroke = size.minDimension * 0.16f
                    slices.forEach { (count, color) ->
                        val sweep = count.toFloat() / total * 360f
                        if (sweep > 0f) {
                            drawArc(
                                color = color,
                                startAngle = start,
                                sweepAngle = sweep - 2f,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                                topLeft = Offset(stroke / 2, stroke / 2),
                                size = Size(
                                    size.width - stroke,
                                    size.height - stroke,
                                ),
                            )
                        }
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = decision.total.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "总单数",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Legend(status.noStair, "免爬", decision.noStairs, decision.total)
                Legend(status.stair, "要爬楼", decision.stairs, decision.total)
                Legend(status.unknown, "待确认", decision.unknown, decision.total)
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, count: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$label $count",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${if (total > 0) count * 100 / total else 0}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrendCard(buckets: List<DayBucket>) {
    val status = LocalLouJiStatusColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (buckets.isEmpty()) {
                Text(
                    text = "这个周期还没有记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val maxTotal = maxOf(1, buckets.maxOf { it.total })
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    buckets.forEach { bucket ->
                        val noStairH = bucket.noStairs.toFloat() / maxTotal * 120f
                        val stairH = bucket.stairs.toFloat() / maxTotal * 120f
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            if (stairH > 0f) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(stairH.dp)
                                        .background(status.stair, RoundedCornerShape(2.dp)),
                                )
                            }
                            if (noStairH > 0f) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(noStairH.dp)
                                        .background(status.noStair, RoundedCornerShape(2.dp)),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legend(status.noStair, "免爬", 0, 0)
                    Legend(status.stair, "要爬楼", 0, 0)
                }
            }
        }
    }
}

@Composable
private fun HeatmapCard(buckets: List<DayBucket>) {
    val status = LocalLouJiStatusColors.current
    val byDay = remember(buckets) { buckets.associateBy { it.dayStart } }
    val formatter = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }
    val count = 84
    val today = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val dayMs = 24L * 60 * 60 * 1000
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (week in 0 until 12) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (day in 0 until 7) {
                            val index = week * 7 + day
                            val dayStart = today - (count - 1 - index) * dayMs
                            val value = byDay[dayStart]?.total ?: 0
                            val alpha = when {
                                value <= 0 -> 0.10f
                                value == 1 -> 0.30f
                                value == 2 -> 0.50f
                                value <= 4 -> 0.72f
                                else -> 1f
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .background(
                                        status.noStair.copy(alpha = alpha),
                                        RoundedCornerShape(3.dp),
                                    ),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "从 ${formatter.format(Date(today - (count - 1) * dayMs))} 到今天 · 颜色越深出勤越多",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RankRow(index: Int, rank: BuildingRank, onClick: () -> Unit) {
    val status = LocalLouJiStatusColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${index + 1}",
                modifier = Modifier.width(26.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = rank.location,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(
                            if (rank.total > 0) rank.noStairs.toFloat() / rank.total else 0f,
                        )
                        .height(6.dp)
                        .background(status.noStair, RoundedCornerShape(3.dp)),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (rank.total > 0) "${rank.noStairs * 100 / rank.total}%" else "—",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFeatureSettings = TabularNumbers.fontFeatureSettings,
                ),
                color = status.noStair,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
