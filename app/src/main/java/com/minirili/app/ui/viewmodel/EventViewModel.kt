package com.minirili.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minirili.app.database.dao.EventDao
import com.minirili.app.database.entity.EventEntity
import com.minirili.app.repository.EventRepository
import com.minirili.app.ui.screens.calendar.JsonUtils
import com.minirili.app.utils.IcsUtils
import com.minirili.app.utils.RecurrenceEngine
import com.minirili.app.utils.EventOccurrence
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

    val currentEvents: StateFlow<List<EventEntity>> = _selectedDate
        .flatMapLatest { date -> repository.getEventsByDate(date) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allEvents: StateFlow<List<EventEntity>> = repository.getAllEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    /** 将事件列表展开到 [startDate, endDate] 范围内（含重复事件展开） */
    fun expandForRange(
        events: List<EventEntity>,
        startDate: String,
        endDate: String
    ): List<EventOccurrence> = RecurrenceEngine.expandForRange(events, startDate, endDate)

    /** 展开单日事件 */
    fun expandForDate(events: List<EventEntity>, date: String): List<EventOccurrence> =
        RecurrenceEngine.expandForDate(events, date)

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
        }
    }

    fun updateEvent(event: EventEntity) {
        viewModelScope.launch {
            repository.update(event.copy(updatedAt = System.currentTimeMillis()))
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

    /** 周期事件仅跳过指定日期的提醒，事件仍展示 */
    fun skipReminderOnly(eventId: Long, date: String) {
        viewModelScope.launch {
            repository.skipReminderOnly(eventId, date)
        }
    }

    /** 恢复某天提醒（从 skipReminderDates 中移除日期） */
    fun restoreReminderOnly(eventId: Long, date: String) {
        viewModelScope.launch {
            repository.restoreReminderOnly(eventId, date)
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

    /** 导入结果：新增 / 更新（同键且导入侧更新）/ 跳过重复 / 解析总数 */
    data class ImportResult(val added: Int, val updated: Int, val skipped: Int, val total: Int)

    // ICS 导入（去重 + 同键按 updatedAt 择优更新）
    fun importICS(icsContent: String, onResult: (ImportResult) -> Unit = {}) {
        viewModelScope.launch {
            onResult(importEvents(IcsUtils.parseICS(icsContent)))
        }
    }

    // JSON 导入（去重 + 同键按 updatedAt 择优更新）
    fun importJSON(jsonContent: String, onResult: (ImportResult) -> Unit = {}) {
        viewModelScope.launch {
            onResult(importEvents(JsonUtils.parseJson(jsonContent)))
        }
    }

    private suspend fun importEvents(incoming: List<EventEntity>): ImportResult {
        val byKey = repository.getAllEventsSnapshot().associateBy { dupKey(it) }.toMutableMap()
        var added = 0; var updated = 0; var skipped = 0
        incoming.forEach { event ->
            val key = dupKey(event)
            val match = byKey[key]
            when {
                match == null -> {
                    // 保留备份里的 createdAt；sortOrder 为 0 时用时间戳保证互异（Bug7 排序依赖）
                    val now = System.currentTimeMillis()
                    repository.insert(event.copy(
                        createdAt = if (event.createdAt > 0) event.createdAt else now,
                        updatedAt = if (event.updatedAt > 0) event.updatedAt else now,
                        sortOrder = if (event.sortOrder == 0L) now else event.sortOrder
                    ))
                    byKey[key] = event  // 同一文件内部重复也只插一条
                    added++
                }
                // 同键且导入侧更新：覆盖本机旧版（repository.update 自带提醒重排）
                // updatedAt 缺失（外部 ICS 无该字段）时为 0，永不覆盖本机数据
                event.updatedAt > match.updatedAt -> {
                    repository.update(event.copy(
                        id = match.id,
                        createdAt = match.createdAt,
                        updatedAt = event.updatedAt
                    ))
                    byKey[key] = event
                    updated++
                }
                else -> skipped++
            }
        }
        return ImportResult(added, updated, skipped, incoming.size)
    }

    /** 判定两条记录是否为同一事件的键 */
    private fun dupKey(e: EventEntity): String =
        "${e.title}|${e.gregorianDate}|${e.reminderTime}|${e.description}"
}
