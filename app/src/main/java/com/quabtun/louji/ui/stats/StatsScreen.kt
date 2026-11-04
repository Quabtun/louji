package com.quabtun.louji.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quabtun.louji.model.DecisionStats
import com.quabtun.louji.model.LocationStat
import com.quabtun.louji.ui.components.EmptyState
import com.quabtun.louji.ui.components.SectionHeader
import com.quabtun.louji.ui.theme.LocalLouJiStatusColors
import com.quabtun.louji.ui.theme.TabularNumbers

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onBack: () -> Unit,
) {
    val insights by viewModel.insights.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.testTag("stats_screen").semantics { testTagsAsResourceId = true },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("统计", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("stats_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                SectionHeader("全周期概览")
                Spacer(Modifier.height(8.dp))
                OverviewCard(insights.decision)

                Spacer(Modifier.height(22.dp))
                SectionHeader("爬楼决策")
                Spacer(Modifier.height(8.dp))
                DecisionRow(insights.decision)

                Spacer(Modifier.height(22.dp))
                SectionHeader("楼栋洞察")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "按记录数排序，最多 20 个常用位置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (insights.locations.isEmpty()) {
                    EmptyState(
                        title = "还没有可统计的位置",
                        body = "记下几单后，这里会显示各楼栋的免爬率和下楼比例。",
                    )
                } else {
                    insights.locations.forEach { location ->
                        LocationInsightRow(location)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(decision: DecisionStats) {
    val total = decision.total
    val noStairRate = if (total > 0) decision.noStairs * 100 / total else 0
    val comeDownRate = if (total > 0) decision.willComeDown * 100 / total else 0
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RateCell("总记录", total.toString(), null, Modifier.weight(1f))
            RateDivider()
            RateCell("免爬率", "$noStairRate%", LocalLouJiStatusColors.current.noStair, Modifier.weight(1f))
            RateDivider()
            RateCell("会下来", "$comeDownRate%", LocalLouJiStatusColors.current.noStair, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DecisionRow(decision: DecisionStats) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RateCell("要爬楼", decision.stairs.toString(), LocalLouJiStatusColors.current.stair, Modifier.weight(1f))
            RateDivider()
            RateCell("免爬", decision.noStairs.toString(), LocalLouJiStatusColors.current.noStair, Modifier.weight(1f))
            RateDivider()
            RateCell("待确认", decision.unknown.toString(), null, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RateCell(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFeatureSettings = TabularNumbers.fontFeatureSettings,
            ),
            fontWeight = FontWeight.Black,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RateDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(40.dp)
            .background(LocalLouJiStatusColors.current.rule),
    )
}

@Composable
private fun LocationInsightRow(location: LocationStat) {
    val noStairRate = if (location.total > 0) location.noStairs * 100 / location.total else 0
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("location_stat_${location.location}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = location.location,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${location.total} 条",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFeatureSettings = TabularNumbers.fontFeatureSettings,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Fact("免爬", "${location.noStairs} · $noStairRate%", LocalLouJiStatusColors.current.noStair)
                Fact("会下来", "${location.willComeDown}", LocalLouJiStatusColors.current.noStair)
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFeatureSettings = TabularNumbers.fontFeatureSettings,
            ),
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
