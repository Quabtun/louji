package com.quabtun.louji.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quabtun.louji.data.RecordRepository
import com.quabtun.louji.data.db.RecordWithAttachments
import com.quabtun.louji.model.RecordFilter
import com.quabtun.louji.model.RecordStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val records: StateFlow<List<RecordWithAttachments>> = combine(
        _query.debounce(220),
        _filter,
    ) { query, filter -> query to filter }
        .flatMapLatest { (query, filter) -> repository.observeRecords(query, filter) }
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
}
