package com.quabtun.louji.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quabtun.louji.data.RecordRepository
import com.quabtun.louji.model.BuildingRank
import com.quabtun.louji.model.ClimbTotals
import com.quabtun.louji.model.DayBucket
import com.quabtun.louji.model.DecisionStats
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class StatsPeriod { TODAY, WEEK, MONTH, ALL }

data class StatsData(
    val totals: ClimbTotals = ClimbTotals(),
    val decision: DecisionStats = DecisionStats(),
    val buckets: List<DayBucket> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(private val repository: RecordRepository) : ViewModel() {
    private val _period = MutableStateFlow(StatsPeriod.MONTH)
    val period: StateFlow<StatsPeriod> = _period.asStateFlow()

    private val dayMs = 24L * 60 * 60 * 1000

    val data: StateFlow<StatsData> = _period
        .flatMapLatest { period ->
            val (since, until) = rangeOf(period)
            combine(
                repository.observeClimbTotals(since, until),
                repository.observeDayBuckets(since),
                repository.observeInsights(),
            ) { totals, buckets, insights ->
                StatsData(
                    totals = totals,
                    decision = insights.decision,
                    buckets = buckets,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsData())

    val rankings: StateFlow<List<BuildingRank>> = repository.observeBuildingRankings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 84-day heatmap buckets, independent of the selected period. */
    val heatmap: StateFlow<List<DayBucket>> = repository.observeDayBuckets(startOfToday() - 83 * dayMs)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setPeriod(value: StatsPeriod) {
        _period.value = value
    }

    private fun rangeOf(period: StatsPeriod): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val zone = TimeZone.getDefault()
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = now }
        return when (period) {
            StatsPeriod.TODAY -> startOfToday() to now + 1
            StatsPeriod.WEEK -> {
                val start = (calendar.clone() as Calendar).apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                start to now + 1
            }
            StatsPeriod.MONTH -> {
                val start = (calendar.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                start to now + 1
            }
            StatsPeriod.ALL -> 0L to Long.MAX_VALUE
        }
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
