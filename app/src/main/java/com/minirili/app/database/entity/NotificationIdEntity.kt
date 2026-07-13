package com.minirili.app.database.entity

import androidx.room.PrimaryKey

/**
 * 通知 ID 实体
 * 用于追踪已发送的通知，避免重复发送
 */
data class NotificationIdEntity(
    @PrimaryKey
    val id: Long = System.currentTimeMillis(),

    // 关联的事件 ID
    val eventId: Long,

    // 通知 ID
    val notificationId: Int
)
