package com.quabtun.louji.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quabtun.louji.data.RecordRepository
import com.quabtun.louji.model.RecordInsights
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class StatsViewModel(private val repository: RecordRepository) : ViewModel() {
    val insights: StateFlow<RecordInsights> = repository.observeInsights()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordInsights())
}
