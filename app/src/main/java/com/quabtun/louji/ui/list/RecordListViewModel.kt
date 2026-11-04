package com.quabtun.louji.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quabtun.louji.data.RecordRepository
import com.quabtun.louji.data.db.RecordWithAttachments
import com.quabtun.louji.model.DateRange
import com.quabtun.louji.model.RecordFilter
import com.quabtun.louji.model.RecordSort
import com.quabtun.louji.model.RecordSorter
import com.quabtun.louji.model.RecordStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class RecordListViewModel(private val repository: RecordRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(RecordFilter.ALL)
    val filter: StateFlow<RecordFilter> = _filter.asStateFlow()

    private val _showArchived = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    private val _showTrash = MutableStateFlow(false)
    val showTrash: StateFlow<Boolean> = _showTrash.asStateFlow()

    private val _sort = MutableStateFlow(RecordSort.UPDATED)
    val sort: StateFlow<RecordSort> = _sort.asStateFlow()

    private val _dateRange = MutableStateFlow(DateRange())
    val dateRange: StateFlow<DateRange> = _dateRange.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val rawRecords: Flow<List<RecordWithAttachments>> = combine(
        _query.debounce(220),
        _filter,
        _showArchived,
        _showTrash,
        _dateRange,
    ) { query, filter, showArchived, showTrash, dateRange ->
        RecordsRequest(query, filter, showArchived, showTrash, dateRange)
    }
        .flatMapLatest { (query, filter, showArchived, showTrash, dateRange) ->
            if (showTrash) {
                repository.observeTrashed()
            } else {
                repository.observeRecords(query, filter, showArchived, dateRange)
            }
        }

    val records: StateFlow<List<RecordWithAttachments>> = combine(
        rawRecords,
        _sort,
        _showTrash,
    ) { records, sort, showTrash ->
        if (showTrash) records else RecordSorter.sort(records, sort)
    }
        .onEach { _isLoading.value = false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val stats: StateFlow<RecordStats> = repository.observeStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordStats())

    val recentLocations: StateFlow<List<String>> = repository.observeRecentLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setFilter(value: RecordFilter) {
        _filter.value = value
    }

    fun setShowArchived(value: Boolean) {
        _showArchived.value = value
        _showTrash.value = false
    }

    fun setShowTrash(value: Boolean) {
        _showTrash.value = value
        _showArchived.value = false
    }

    fun setSort(value: RecordSort) {
        _sort.value = value
    }

    fun setDateRange(range: DateRange) {
        _dateRange.value = range
    }

    fun clearDateRange() {
        _dateRange.value = DateRange()
    }

    private data class RecordsRequest(
        val query: String,
        val filter: RecordFilter,
        val showArchived: Boolean,
        val showTrash: Boolean,
        val dateRange: DateRange,
    )
}
