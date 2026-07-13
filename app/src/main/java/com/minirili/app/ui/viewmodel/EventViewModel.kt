package com.minirili.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minirili.app.database.dao.EventDao
import com.minirili.app.database.entity.EventEntity
import com.minirili.app.repository.EventRepository
import com.minirili.app.ui.screens.calendar.JsonUtils
import com.minirili.app.utils.IcsUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventViewModel @Inject constructor(
    private val repository: EventRepository
) : ViewModel() {
    private val _selectedDate = MutableStateFlow("")
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** 用户首次创建带提醒的事件时 emit，MainActivity 收集后弹电池白名单引导 */
    private val _batteryGuideTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val batteryGuideTrigger: SharedFlow<Unit> = _batteryGuideTrigger.asSharedFlow()

    val currentEvents: StateFlow<List<EventEntity>> = _selectedDate
        .flatMapLatest { date -> repository.getEventsByDate(date) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allEvents: StateFlow<List<EventEntity>> = repository.getAllEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    suspend fun getEventById(eventId: Long): EventEntity? {
        return repository.getEventById(eventId)
    }

    fun insertEvent(event: EventEntity) {
        viewModelScope.launch {
            // sortOrder 默认设为 createdAt 时戳，保证同一日期多个事件 sortOrder 互异、Bug7 上下移动按钮才有实际效果
            val now = System.currentTimeMillis()
            repository.insert(event.copy(
                createdAt = now,
                updatedAt = now,
                sortOrder = if (event.sortOrder == 0L) now else event.sortOrder
            ))
            // 新建事件设置了提醒 → 检查是否需要弹电池引导
            if (event.reminderTime > 0 || event.repeatType != "none") {
                _batteryGuideTrigger.tryEmit(Unit)
            }
        }
    }

    fun updateEvent(event: EventEntity) {
        viewModelScope.launch {
            // 检查旧事件是否有提醒：仅当"之前无提醒 → 现在有提醒"时才触发引导
            val oldEvent = repository.getEventById(event.id)
            val hadReminder = oldEvent?.let { it.reminderTime > 0 || it.repeatType != "none" } ?: false
            repository.update(event.copy(updatedAt = System.currentTimeMillis()))
            if (!hadReminder && (event.reminderTime > 0 || event.repeatType != "none")) {
                _batteryGuideTrigger.tryEmit(Unit)
            }
        }
    }

    fun deleteEvent(event: EventEntity) {
        viewModelScope.launch {
            repository.delete(event)
        }
    }

    /** EVT-10: 跳过周期事件某次触发（不影响后续触发） */
    fun skipOccurrence(eventId: Long, date: String) {
        viewModelScope.launch {
            repository.skipOccurrence(eventId, date)
        }
    }

    fun setCompleted(eventId: Long, completed: Boolean) {
        viewModelScope.launch {
            repository.setCompleted(eventId, completed)
        }
    }

    // UI-04: 事件上移/下移
    fun moveEventUp(eventId: Long, date: String) {
        viewModelScope.launch { repository.moveEventUp(eventId, date) }
    }
    fun moveEventDown(eventId: Long, date: String) {
        viewModelScope.launch { repository.moveEventDown(eventId, date) }
    }

    // P2-SCH-01 搜索
    val searchResults = _searchQuery
        .debounce(300)
        .flatMapLatest { query -> if (query.isBlank()) flowOf(emptyList()) else repository.searchEvents("%$query%") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ICS 导入（带去重）
    fun importICS(icsContent: String) {
        viewModelScope.launch {
            val incoming = IcsUtils.parseICS(icsContent)
            val existing = repository.getAllEventsSnapshot()
            val existingKeys = existing.mapTo(mutableSetOf()) { dupKey(it) }
            var added = 0
            incoming.forEach { event ->
                val key = dupKey(event)
                if (key !in existingKeys) {
                    insertEvent(event)
                    existingKeys.add(key)  // 避免单次导入内批次重复
                    added++
                }
            }
        }
    }

    // JSON 导入（带去重）
    fun importJSON(jsonContent: String) {
        viewModelScope.launch {
            val incoming = JsonUtils.parseJson(jsonContent)
            val existing = repository.getAllEventsSnapshot()
            val existingKeys = existing.mapTo(mutableSetOf()) { dupKey(it) }
            var added = 0
            incoming.forEach { event ->
                val key = dupKey(event)
                if (key !in existingKeys) {
                    insertEvent(event)
                    existingKeys.add(key)
                    added++
                }
            }
        }
    }

    /** 判定两条记录是否为同一事件的键 */
    private fun dupKey(e: EventEntity): String =
        "${e.title}|${e.gregorianDate}|${e.reminderTime}|${e.description}"
}
