package com.quabtun.louji.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.quabtun.louji.AppContainer
import com.quabtun.louji.ui.detail.RecordDetailViewModel
import com.quabtun.louji.ui.edit.RecordEditViewModel
import com.quabtun.louji.ui.list.RecordListViewModel
import com.quabtun.louji.ui.media.MediaViewerViewModel
import com.quabtun.louji.ui.settings.SettingsViewModel
import com.quabtun.louji.ui.update.UpdateViewModel

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(RecordListViewModel::class.java) ->
            RecordListViewModel(container.repository) as T

        modelClass.isAssignableFrom(RecordEditViewModel::class.java) ->
            RecordEditViewModel(container.repository, container.mediaStore) as T

        modelClass.isAssignableFrom(RecordDetailViewModel::class.java) ->
            RecordDetailViewModel(container.repository) as T

        modelClass.isAssignableFrom(MediaViewerViewModel::class.java) ->
            MediaViewerViewModel(container.repository) as T

        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(container) as T

        modelClass.isAssignableFrom(UpdateViewModel::class.java) ->
            UpdateViewModel(container) as T

        else -> error("Unknown ViewModel class: ${modelClass.name}")
    }
}
