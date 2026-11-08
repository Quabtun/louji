package com.quabtun.louji.ui.list

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.quabtun.louji.data.DeleteUndoController
import com.quabtun.louji.data.OrderAutoSaver
import com.quabtun.louji.data.OrderTextRecognizer
import com.quabtun.louji.data.PendingDelete
import com.quabtun.louji.data.RecordRepository
import com.quabtun.louji.data.SettingsStore
import com.quabtun.louji.data.db.BuildingProfileEntity
import com.quabtun.louji.data.db.RecordWithAttachments
import com.quabtun.louji.model.BuildingStats
import com.quabtun.louji.model.CustomerProfile
import com.quabtun.louji.model.DateRange
import com.quabtun.louji.model.RecordFilter
import com.quabtun.louji.model.RecordSort
import com.quabtun.louji.model.RecordSorter
import com.quabtun.louji.model.RecordStats
import com.quabtun.louji.util.ClipboardOrderParser
import com.quabtun.louji.util.ParsedOrder
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class RecordListViewModel(
    private val repository: RecordRepository,
    private val deleteUndo: DeleteUndoController,
    private val orderAutoSaver: OrderAutoSaver,
    private val recognizer: OrderTextRecognizer,
    settings: SettingsStore,
) : ViewModel() {
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

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

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

    /** Keyset-paginated source for the time-ordered (最近更新) view — the default and most data-heavy path. */
    val recordsPaged: Flow<PagingData<RecordWithAttachments>> = combine(
        _query.debounce(220),
        _filter,
        _showArchived,
        _dateRange,
    ) { query, filter, showArchived, dateRange ->
        PagedRequest(query, filter, showArchived, dateRange)
    }
        .flatMapLatest { (query, filter, showArchived, dateRange) ->
            Pager(
                config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                pagingSourceFactory = {
                    repository.recordPagingSource(query, filter, showArchived, dateRange)
                },
            ).flow
        }

    val stats: StateFlow<RecordStats> = repository.observeStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordStats())

    val recentLocations: StateFlow<List<String>> = repository.observeRecentLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val undoState: StateFlow<PendingDelete?> = deleteUndo.pending

    /** Whether one-tap order parsing should commit straight to the database. */
    val autoSaveEnabled: StateFlow<Boolean> = settings.settings
        .map { it.autoSaveOrders }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _quickSaving = MutableStateFlow(false)
    val quickSaving: StateFlow<Boolean> = _quickSaving.asStateFlow()

    private val _quickEvent = MutableStateFlow<QuickSaveEvent?>(null)
    val quickEvent: StateFlow<QuickSaveEvent?> = _quickEvent.asStateFlow()

    /**
     * Live "second-look radar": while the rider types, surface the matched building profile and/or
     * a returning customer's history before they even reach the door.
     */
    val radar: StateFlow<RadarHit?> = _query
        .debounce(260)
        .mapLatest { value -> buildRadar(value) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun buildRadar(rawQuery: String): RadarHit? {
        val value = rawQuery.trim()
        if (value.length < 2) return null
        val building = repository.findBuildingMatch(value)
        val stats = building?.let { repository.buildingStats(it.location) } ?: BuildingStats()
        val customer = if (value.matches(Regex("""\d{4}"""))) {
            repository.customerProfile(value, "", "")
        } else {
            CustomerProfile()
        }
        if (building == null && !customer.hasHistory) return null
        return RadarHit(
            building = building,
            buildingStats = stats,
            customer = customer.takeIf { it.hasHistory },
            tailLabel = value.takeIf { customer.hasHistory }.orEmpty(),
        )
    }

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

    fun restoreDeleted() {
        val pending = deleteUndo.pending.value ?: return
        deleteUndo.consume()
        viewModelScope.launch { repository.restore(pending.recordId) }
    }

    fun consumeUndo() {
        deleteUndo.consume()
    }

    fun quickSaveText(raw: String) {
        if (_quickSaving.value) return
        val text = raw.trim()
        if (text.isEmpty()) {
            _quickEvent.value = QuickSaveEvent.Fallback("剪贴板没有订单文字")
            return
        }
        val parsed = ClipboardOrderParser.parse(text)
        if (parsed.isEmpty) {
            _quickEvent.value = QuickSaveEvent.Fallback(
                message = "没识别到订单信息，可手动填写",
                text = text,
            )
            return
        }
        viewModelScope.launch {
            _quickSaving.value = true
            try {
                val saved = orderAutoSaver.save(
                    parsed = parsed,
                    sourceLabel = "剪贴板",
                    rawText = text,
                )
                _quickEvent.value = QuickSaveEvent.Saved(saved.recordId, saved.label())
            } catch (error: Throwable) {
                _quickEvent.value = QuickSaveEvent.Fallback(
                    message = error.message ?: "保存失败，可手动填写",
                    text = text,
                )
            } finally {
                _quickSaving.value = false
            }
        }
    }

    fun quickSaveImage(uri: Uri) {
        if (_quickSaving.value) return
        viewModelScope.launch {
            _quickSaving.value = true
            try {
                val text = runCatching { recognizer.recognize(uri) }.getOrDefault("")
                val parsed: ParsedOrder = if (text.isBlank()) {
                    ParsedOrder()
                } else {
                    ClipboardOrderParser.parse(text)
                }
                if (parsed.isEmpty) {
                    _quickEvent.value = QuickSaveEvent.Fallback(
                        message = "未识别出有效订单信息，已为你打开手动建单",
                        imageUri = uri,
                    )
                    return@launch
                }
                val saved = orderAutoSaver.save(
                    parsed = parsed,
                    screenshotUri = uri,
                    sourceLabel = "订单截图",
                    rawText = text,
                )
                _quickEvent.value = QuickSaveEvent.Saved(saved.recordId, saved.label())
            } catch (error: Throwable) {
                _quickEvent.value = QuickSaveEvent.Fallback(
                    message = error.message ?: "识别或保存失败，已为你打开手动建单",
                    imageUri = uri,
                )
            } finally {
                _quickSaving.value = false
            }
        }
    }

    fun undoQuickSave(recordId: Long) {
        viewModelScope.launch { repository.softDelete(recordId) }
    }

    fun consumeQuickEvent() {
        _quickEvent.value = null
    }

    fun enterSelectionMode() {
        _selectionMode.value = true
    }

    fun toggleSelection(id: Long) {
        _selectedIds.value = _selectedIds.value.let { if (id in it) it - id else it + id }
    }

    fun selectAll(records: List<RecordWithAttachments>) {
        _selectedIds.value = records.map { it.record.id }.toSet()
    }

    fun exitSelectionMode() {
        _selectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun archiveSelected(archived: Boolean) {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.setArchivedBatch(ids, archived)
            exitSelectionMode()
        }
    }

    fun trashOne(id: Long) {
        viewModelScope.launch {
            repository.softDelete(id)
            deleteUndo.announce(id)
        }
    }

    fun trashSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.softDeleteBatch(ids)
            exitSelectionMode()
        }
    }

    private data class RecordsRequest(
        val query: String,
        val filter: RecordFilter,
        val showArchived: Boolean,
        val showTrash: Boolean,
        val dateRange: DateRange,
    )

    private data class PagedRequest(
        val query: String,
        val filter: RecordFilter,
        val showArchived: Boolean,
        val dateRange: DateRange,
    )

    data class RadarHit(
        val building: BuildingProfileEntity? = null,
        val buildingStats: BuildingStats = BuildingStats(),
        val customer: CustomerProfile? = null,
        val tailLabel: String = "",
    )
}

/** One-shot feedback for the ledger's one-tap order savers. */
sealed interface QuickSaveEvent {
    data class Saved(val recordId: Long, val label: String) : QuickSaveEvent

    data class Fallback(
        val message: String,
        val text: String? = null,
        val imageUri: Uri? = null,
    ) : QuickSaveEvent
}
