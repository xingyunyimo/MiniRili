package com.minirili.app.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 事件实体类
 * 对应 P0-EVT-01~03, EVT-11, EVT-12
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // 事件标题
    val title: String,

    // 事件描述/内容
    val description: String = "",

    // 事件类型（生日/纪念日/普通）
    val type: String = "普通",

    // 事件标签（逗号分隔）
    val tags: String = "",

    // 颜色标记
    val color: Int = 0,

    // 优先级（高/中/低）
    val priority: Int = 1, // 0=低, 1=中, 2=高

    // 完成状态
    val completed: Boolean = false,

    // 公历日期（YYYY-MM-DD）
    val gregorianDate: String,

    // 农历日期（YYYY-MM-DD，用于农历事件）
    val lunarDate: String = "",

    // 是否使用农历日期判断
    val useLunar: Boolean = false,

    // 提醒时间（Unix 时间戳）
    val reminderTime: Long = 0,

    // 提醒提前量（分钟）
    val reminderOffset: Int = 0,

    // 重复类型（none/daily/weekly/monthly/yearly/workday/weekend；农历模式下 monthly/yearly 含"每月(农历)"/"每年(农历)"）
    val repeatType: String = "none",

    // 通知方式：通知栏（默认 true）
    val notifyNotification: Boolean = true,

    // 通知方式：闹钟（默认 true）
    val notifyAlarm: Boolean = true,

    // 周期事件例外的触发日期集合（逗号分隔的公历日期 YYYY-MM-DD）。闹钟触发时命中则跳过，不影响后续 resume
    val skipDates: String = "",

    // 排序顺序（UI-04 拖拽排序，值越小越靠前）
    val sortOrder: Long = 0L,

    // 附件 JSON 数组（EVT-08）：[{"name":"xxx","uri":"file://...","type":"image/png"}]
    val attachments: String = "",

    // 创建时间
    val createdAt: Long = System.currentTimeMillis(),

    // 更新时间
    val updatedAt: Long = System.currentTimeMillis()
)
