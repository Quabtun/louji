package com.quabtun.louji.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quabtun.louji.data.DeleteUndoController
import com.quabtun.louji.data.RecordRepository
import com.quabtun.louji.data.db.RecordWithAttachments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecordDetailViewModel(
    private val repository: RecordRepository,
    private val deleteUndo: DeleteUndoController,
) : ViewModel() {
    private val recordId = MutableStateFlow<Long?>(null)

    val record: StateFlow<RecordWithAttachments?> = recordId
        .filterNotNull()
        .flatMapLatest { repository.observeRecord(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(id: Long) {
        recordId.value = id
    }

    fun delete(onDeleted: () -> Unit) {
        val id = recordId.value ?: return
        viewModelScope.launch {
            repository.deleteRecord(id)
            onDeleted()
        }
    }

    fun trash(onTrashed: () -> Unit) {
        val id = recordId.value ?: return
        viewModelScope.launch {
            repository.softDelete(id)
            deleteUndo.announce(id)
            onTrashed()
        }
    }

    fun restore(onRestored: () -> Unit) {
        val id = recordId.value ?: return
        viewModelScope.launch {
            repository.restore(id)
            onRestored()
        }
    }

    fun purge(onPurged: () -> Unit) {
        val id = recordId.value ?: return
        viewModelScope.launch {
            repository.deleteRecord(id)
            onPurged()
        }
    }

    fun setArchived(archived: Boolean) {
        val id = recordId.value ?: return
        viewModelScope.launch { repository.setArchived(id, archived) }
    }

    fun deleteAttachment(attachmentId: Long) {
        viewModelScope.launch { repository.deleteAttachment(attachmentId) }
    }
}
