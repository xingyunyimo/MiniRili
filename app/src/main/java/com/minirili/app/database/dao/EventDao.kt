package com.minirili.app.database.dao

import androidx.room.*
import com.minirili.app.database.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Query("SELECT * FROM events ORDER BY gregorianDate ASC, sortOrder ASC, reminderTime ASC")
    fun getAllEvents(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events ORDER BY gregorianDate ASC, sortOrder ASC, reminderTime ASC")
    suspend fun getAllEventsOnce(): List<EventEntity>

    @Query("SELECT * FROM events WHERE id = :eventId")
    suspend fun getEventById(eventId: Long): EventEntity?

    @Query("SELECT * FROM events WHERE gregorianDate = :date ORDER BY sortOrder ASC, reminderTime ASC")
    fun getEventsByDate(date: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE gregorianDate BETWEEN :startDate AND :endDate ORDER BY gregorianDate ASC, sortOrder ASC, reminderTime ASC")
    fun getEventsBetween(startDate: String, endDate: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE gregorianDate = :date AND completed = 0 ORDER BY priority DESC, sortOrder ASC, reminderTime ASC")
    fun getActiveEventsByDate(date: String): Flow<List<EventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventEntity>)

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("DELETE FROM events WHERE gregorianDate = :date")
    suspend fun deleteByDate(date: String)

    @Query("SELECT COUNT(*) FROM events")
    fun getTotalEventCount(): Flow<Int>

    // 搜索功能（P2-SCH-01）
    @Query("SELECT * FROM events WHERE title LIKE :query OR description LIKE :query ORDER BY sortOrder ASC")
    fun searchEvents(query: String): Flow<List<EventEntity>>

    // 类型过滤（P2-SCH-03）
    @Query("SELECT * FROM events WHERE type = :eventType ORDER BY sortOrder ASC, reminderTime ASC")
    fun getEventsByType(eventType: String): Flow<List<EventEntity>>

    // 标签过滤（P2-SCH-02）
    @Query("SELECT * FROM events WHERE tags LIKE :tag ORDER BY sortOrder ASC, reminderTime ASC")
    fun getEventsByTag(tag: String): Flow<List<EventEntity>>

    // 标记完成/未完成
    @Query("UPDATE events SET completed = :completed WHERE id = :eventId")
    suspend fun setCompleted(eventId: Long, completed: Boolean)

    // 获取完成的日期列表（用于显示已完成事件）
    @Query("SELECT DISTINCT gregorianDate FROM events WHERE completed = 1 ORDER BY gregorianDate DESC")
    fun getCompletedDates(): Flow<List<String>>

    // UI-04: 更新排序顺序
    @Query("UPDATE events SET sortOrder = :sortOrder WHERE id = :eventId")
    suspend fun updateSortOrder(eventId: Long, sortOrder: Long)
}