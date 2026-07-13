package com.minirili.app.database.entity

import androidx.room.PrimaryKey

/**
 * 事件提醒实体
 * P0-REM-01 一次性提醒
 *
 * 存储每个事件的提醒设置和发送状态
 */
data class EventReminderEntity(
    @PrimaryKey
    val id: Long = System.currentTimeMillis(),

    // 关联的事件 ID
    val eventId: Long,

    // 事件日期
    val eventDate: String,

    // 提醒时间（Unix 时间戳）
    val reminderTime: Long,

    // 提醒提前量（秒）
    val reminderOffset: Int,

    // 提醒已发送标记
    val sent: Boolean = false,

    // 提醒已取消标记
    val cancelled: Boolean = false,

    // 创建时间
    val createdAt: Long = System.currentTimeMillis()
)
