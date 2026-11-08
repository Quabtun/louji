package com.quabtun.louji.ui.building

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quabtun.louji.data.LocalMediaStore
import com.quabtun.louji.data.RecordRepository
import com.quabtun.louji.data.db.BuildingProfileEntity
import com.quabtun.louji.data.db.RecordWithAttachments
import com.quabtun.louji.model.BuildingRank
import com.quabtun.louji.model.BuildingStats
import com.quabtun.louji.model.ElevatorType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class BuildingFilter { ALL, PINNED, STAIRS_ONLY, HAS_CODE, HAS_CARD }
enum class BuildingSort { ORDERS, NO_STAIR_RATE, UPDATED }

@OptIn(ExperimentalCoroutinesApi::class)
class BuildingViewModel(
    private val repository: RecordRepository,
    private val mediaStore: LocalMediaStore,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _filter = MutableStateFlow(BuildingFilter.ALL)
    private val _sort = MutableStateFlow(BuildingSort.ORDERS)
    private val _location = MutableStateFlow("")

    private val allBuildings: StateFlow<List<BuildingProfileEntity>> =
        repository.observeBuildings()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rankings: StateFlow<List<BuildingRank>> =
        repository.observeBuildingRankings()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val buildings: StateFlow<List<BuildingProfileEntity>> = combine(
        allBuildings,
        rankings,
        _query,
        _filter,
        _sort,
    ) { list, ranks, query, filter, sort ->
        val rankByLocation = ranks.associateBy { it.location }
        val filtered = list.filter { profile ->
            val matchesQuery = query.isBlank() ||
                profile.location.contains(query.trim(), ignoreCase = true) ||
                profile.accessCode.contains(query.trim()) ||
                profile.notes.contains(query.trim(), ignoreCase = true)
            val matchesFilter = when (filter) {
                BuildingFilter.ALL -> true
                BuildingFilter.PINNED -> profile.pinned
                BuildingFilter.STAIRS_ONLY -> profile.elevatorType == ElevatorType.STAIRS_ONLY
                BuildingFilter.HAS_CODE -> profile.accessCode.isNotBlank()
                BuildingFilter.HAS_CARD -> profile.elevatorType == ElevatorType.CARD_CONTROL
            }
            matchesQuery && matchesFilter
        }
        val sorted = when (sort) {
            BuildingSort.ORDERS -> filtered.sortedWith(
                compareByDescending<BuildingProfileEntity> { it.pinned }
                    .thenByDescending { rankByLocation[it.location]?.total ?: 0 }
                    .thenByDescending { it.updatedAt },
            )
            BuildingSort.NO_STAIR_RATE -> filtered.sortedWith(
                compareByDescending<BuildingProfileEntity> { it.pinned }
                    .thenByDescending {
                        val rank = rankByLocation[it.location]
                        if (rank == null || rank.total == 0) -1 else rank.noStairs * 100 / rank.total
                    },
            )
            BuildingSort.UPDATED -> filtered.sortedWith(
                compareByDescending<BuildingProfileEntity> { it.pinned }
                    .thenByDescending { it.updatedAt },
            )
        }
        sorted
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rankList: StateFlow<List<BuildingRank>> = rankings

    val profile: StateFlow<BuildingProfileEntity?> = _location
        .flatMapLatest { location ->
            if (location.isBlank()) {
                kotlinx.coroutines.flow.flowOf(null)
            } else {
                repository.observeBuilding(location)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val stats: StateFlow<BuildingStats> = _location
        .flatMapLatest { location ->
            if (location.isBlank()) {
                kotlinx.coroutines.flow.flowOf(BuildingStats())
            } else {
                repository.observeBuildingStats(location)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BuildingStats())

    val locationRecords: StateFlow<List<RecordWithAttachments>> = _location
        .flatMapLatest { location ->
            if (location.isBlank()) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                repository.observeRecordsAtLocation(location)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentLocation: StateFlow<String> = _location

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setFilter(value: BuildingFilter) {
        _filter.value = value
    }

    fun setSort(value: BuildingSort) {
        _sort.value = value
    }

    fun load(location: String) {
        _location.value = location
    }

    fun createProfile(location: String) {
        val key = location.trim()
        if (key.isEmpty()) return
        viewModelScope.launch {
            repository.saveBuilding(
                BuildingProfileEntity(
                    location = key,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            _location.value = key
        }
    }

    fun save(profile: BuildingProfileEntity) {
        viewModelScope.launch { repository.saveBuilding(profile) }
    }

    fun togglePinned() {
        val current = profile.value ?: return
        viewModelScope.launch { repository.setBuildingPinned(current.id, !current.pinned) }
    }

    fun delete() {
        val current = profile.value ?: return
        viewModelScope.launch { repository.deleteBuilding(current.id) }
    }

    fun setCover(path: String?) {
        val current = profile.value ?: return
        viewModelScope.launch { repository.saveBuilding(current.copy(coverPhotoPath = path)) }
    }
}
