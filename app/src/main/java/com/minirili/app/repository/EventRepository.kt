package com.minirili.app.repository

import android.content.Context
import com.minirili.app.database.dao.EventDao
import com.minirili.app.database.entity.EventEntity
import com.minirili.app.scheduler.RecurringReminderScheduler
import com.minirili.app.scheduler.ReminderScheduler
import com.minirili.app.utils.DateUtils
import com.minirili.app.widgets.CombinedWidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val eventDao: EventDao,
    private val reminderScheduler: ReminderScheduler,
    private val recurringReminderScheduler: RecurringReminderScheduler,
    @ApplicationContext private val appContext: Context
) {

    fun getAllEvents(): Flow<List<EventEntity>> = eventDao.getAllEvents()

    suspend fun getAllEventsSnapshot(): List<EventEntity> = eventDao.getAllEventsOnce()

    suspend fun getEventById(eventId: Long): EventEntity? = eventDao.getEventById(eventId)

    fun getEventsByDate(date: String): Flow<List<EventEntity>> =
        eventDao.getEventsByDate(date)

    fun getActiveEventsByDate(date: String): Flow<List<EventEntity>> =
        eventDao.getActiveEventsByDate(date)

    suspend fun setCompleted(eventId: Long, completed: Boolean) {
        eventDao.setCompleted(eventId, completed)
        CombinedWidgetProvider.refreshWidget(appContext)
    }

    suspend fun insert(event: EventEntity) {
        val newId = eventDao.insert(event.copy(id = 0L))
        scheduleForEvent(event.copy(id = newId), rescheduleRecurring = false)
        CombinedWidgetProvider.refreshWidget(appContext)
    }

    suspend fun update(event: EventEntity) {
        eventDao.update(event)
        reminderScheduler.cancelReminder(event.id)
        scheduleForEvent(event, rescheduleRecurring = false)
        CombinedWidgetProvider.refreshWidget(appContext)
    }

    /**
     * 按事件类型排闹钟。
     * @param rescheduleRecurring true 时以"今天"为基续排（跳过/恢复操作用，锚点过旧的周期事件也能重排未来窗口）；
     *                            false 时以事件锚点日期为基（增删改常规路径）。
     */
    private suspend fun scheduleForEvent(event: EventEntity, rescheduleRecurring: Boolean) {
        if (event.repeatType != "none") {
            val base = if (rescheduleRecurring) {
                val today = DateUtils.today()
                if (today > event.gregorianDate) today else event.gregorianDate
            } else event.gregorianDate
            recurringReminderScheduler.scheduleRecurringReminder(event, base)
        } else if (event.notifyNotification || event.notifyAlarm) {
            val dateCal = DateUtils.parseGregorian(event.gregorianDate)
            val triggerTime = recurringReminderScheduler.calculateReminderTime(
                dateCal, event.reminderTime, event.reminderOffset
            )
            if (triggerTime > System.currentTimeMillis()) {
                reminderScheduler.scheduleReminder(event.id, event.gregorianDate, triggerTime)
            }
        }
    }

    suspend fun delete(event: EventEntity) {
        eventDao.delete(event)
        reminderScheduler.cancelReminder(event.id)
        CombinedWidgetProvider.refreshWidget(appContext)
    }

    /** EVT-10: 周期事件跳过指定触发日期。追加到 skipDates，不影响原 repeatType 与后续触发 */
    suspend fun skipOccurrence(eventId: Long, date: String) {
        val event = eventDao.getEventById(eventId) ?: return
        val normalized = date.trim()
        if (normalized.isEmpty()) return
        val current = event.skipDates.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (normalized !in current) {
            current.add(normalized)
            val updated = event.copy(skipDates = current.joinToString(","))
            eventDao.update(updated)
            rescheduleAfterSkipChange(updated)
        }
        CombinedWidgetProvider.refreshWidget(appContext)
    }

    /** 周期事件仅跳过指定日期的提醒，事件仍展示。追加到 skipReminderDates，不碰 skipDates */
    suspend fun skipReminderOnly(eventId: Long, date: String) {
        val event = eventDao.getEventById(eventId) ?: return
        val normalized = date.trim()
        if (normalized.isEmpty()) return
        val current = event.skipReminderDates.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (normalized !in current) {
            current.add(normalized)
            val updated = event.copy(skipReminderDates = current.joinToString(","))
            eventDao.update(updated)
            rescheduleAfterSkipChange(updated)
        }
        CombinedWidgetProvider.refreshWidget(appContext)
    }

    /** 恢复某天提醒（从 skipReminderDates 中移除日期） */
    suspend fun restoreReminderOnly(eventId: Long, date: String) {
        val event = eventDao.getEventById(eventId) ?: return
        val normalized = date.trim()
        if (normalized.isEmpty()) return
        val current = event.skipReminderDates.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (normalized in current) {
            current.remove(normalized)
            val updated = event.copy(skipReminderDates = current.joinToString(","))
            eventDao.update(updated)
            rescheduleAfterSkipChange(updated)
        }
        CombinedWidgetProvider.refreshWidget(appContext)
    }

    /**
     * 跳过/恢复提醒后重排该事件的闹钟窗口（双保险）。
     * 不重排的话，被跳过那次触发不会滚动窗口（AlarmReceiver 在 skip 命中时提前 return），
     * 连续跳过到窗口末端会导致周期提醒静默断流。
     */
    private suspend fun rescheduleAfterSkipChange(event: EventEntity) {
        if (event.repeatType == "none") return
        reminderScheduler.cancelReminder(event.id)
        scheduleForEvent(event, rescheduleRecurring = true)
    }

    // P2-SCH-01 搜索
    fun searchEvents(query: String): Flow<List<EventEntity>> =
        eventDao.searchEvents("%$query%")

    // 类型过滤（P2-SCH-03）
    fun getEventsByType(eventType: String): Flow<List<EventEntity>> =
        eventDao.getEventsByType(eventType)

    // 标签过滤（P2-SCH-02）
    fun getEventsByTag(tag: String): Flow<List<EventEntity>> =
        eventDao.getEventsByTag("%$tag%")

    // UI-04: 事件上移（减小 sortOrder）
    suspend fun moveEventUp(eventId: Long, date: String) {
        val events = eventDao.getEventsByDate(date).firstOrNull() ?: return
        val idx = events.indexOfFirst { it.id == eventId }
        if (idx <= 0) return
        swapSortOrder(events[idx], events[idx - 1])
    }

    // UI-04: 事件下移（增大 sortOrder）
    suspend fun moveEventDown(eventId: Long, date: String) {
        val events = eventDao.getEventsByDate(date).firstOrNull() ?: return
        val idx = events.indexOfFirst { it.id == eventId }
        if (idx < 0 || idx >= events.size - 1) return
        swapSortOrder(events[idx], events[idx + 1])
    }

    private suspend fun swapSortOrder(a: EventEntity, b: EventEntity) {
        // Bug7: 当 sortOrder 相同时（如旧数据全为 0），单纯 swap 等于没动；
        // 强制分配互异值，使上下移动按钮立刻生效
        if (a.sortOrder == b.sortOrder) {
            eventDao.updateSortOrder(a.id, b.sortOrder + 1)
            eventDao.updateSortOrder(b.id, b.sortOrder)
            return
        }
        val temp = a.sortOrder
        eventDao.updateSortOrder(a.id, b.sortOrder)
        eventDao.updateSortOrder(b.id, temp)
    }
}
