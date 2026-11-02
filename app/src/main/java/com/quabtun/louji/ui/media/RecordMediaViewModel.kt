package com.quabtun.louji.ui.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quabtun.louji.data.RecordRepository
import com.quabtun.louji.data.db.RecordWithAttachments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class RecordMediaViewModel(private val repository: RecordRepository) : ViewModel() {
    private val recordId = MutableStateFlow<Long?>(null)

    val record: StateFlow<RecordWithAttachments?> = recordId
        .filterNotNull()
        .flatMapLatest { repository.observeRecord(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(id: Long) {
        recordId.value = id
    }
}
