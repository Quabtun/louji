package com.quabtun.louji.ui.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quabtun.louji.data.RecordRepository
import com.quabtun.louji.data.db.AttachmentEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MediaViewerViewModel(private val repository: RecordRepository) : ViewModel() {
    private val recordId = MutableStateFlow<Long?>(null)

    val attachments: StateFlow<List<AttachmentEntity>> = recordId
        .filterNotNull()
        .flatMapLatest { repository.observeRecord(it) }
        .map { record -> record?.attachments.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun load(id: Long) {
        recordId.value = id
    }

    fun deleteAttachment(id: Long) {
        viewModelScope.launch { repository.deleteAttachment(id) }
    }
}
