package com.minirili.app.utils

import com.minirili.app.database.entity.EventEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * ICS 格式工具类
 * 支持 .ics 标准格式导入导出（RFC 5545）
 * 对应 DAT-02
 */
object IcsUtils {

    private const val ICS_HEADER = """BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//MiniRili//MiniRili 1.0//CN
CALSCALE:GREGORIAN
METHOD:PUBLISH
X-WR-CALNAME:迷历
X-WR-TIMEZONE:Asia/Shanghai
"""

    private const val ICS_FOOTER = "END:VCALENDAR"

    /**
     * 生成 ICS 内容
     * 包含当前 EventEntity 所有字段（以 X-MINIRILI-* 非标属性存储）
     */
    fun generateICS(events: List<EventEntity>): String {
        val sb = StringBuilder(ICS_HEADER)

        events.forEach { event ->
            sb.append("BEGIN:VEVENT\n")
            sb.append("UID:${event.id}@calendar.app\n")
            sb.append("DTSTAMP:${formatICSDateTime(System.currentTimeMillis())}\n")
            sb.append("DTSTART;VALUE=DATE:${formatICSDate(event.gregorianDate)}\n")
            sb.append("SUMMARY:${escapeICSValue(event.title)}\n")
            sb.append("DESCRIPTION:${escapeICSValue(event.description)}\n")
            sb.append("CLASS:PUBLIC\n")
            sb.append("STATUS:${if (event.completed) "COMPLETED" else "CONFIRMED"}\n")

            // 标准 ICS 字段
            val typeMap = mapOf(
                "生日" to "Birthday",
                "纪念日" to "Anniversary",
                "普通" to "Personal"
            )
            sb.append("CATEGORIES:${typeMap[event.type] ?: event.type}\n")
            if (event.priority != 1) {
                // ICS 优先级 1=最高, 5=中, 9=最低
                val icsPriority = when (event.priority) { 0 -> 9; 2 -> 1; else -> 5 }
                sb.append("PRIORITY:$icsPriority\n")
            }

            // 自定义 X-MINIRILI-* 字段
            writeXProp(sb, "X-MINIRILI-TAGS", event.tags)
            writeXProp(sb, "X-MINIRILI-COLOR", event.color.toString())
            writeXProp(sb, "X-MINIRILI-COMPLETED", event.completed.toString())
            writeXProp(sb, "X-MINIRILI-PRIORITY", event.priority.toString())
            writeXProp(sb, "X-MINIRILI-REMINDERTIME", event.reminderTime.toString())
            writeXProp(sb, "X-MINIRILI-REMINDEROFFSET", event.reminderOffset.toString())
            writeXProp(sb, "X-MINIRILI-REPEATTYPE", event.repeatType)
            writeXProp(sb, "X-MINIRILI-LUNARDATE", event.lunarDate)
            writeXProp(sb, "X-MINIRILI-USELUNAR", event.useLunar.toString())
            writeXProp(sb, "X-MINIRILI-SKIPDATES", event.skipDates)
            writeXProp(sb, "X-MINIRILI-NOTIFYNOTIFICATION", event.notifyNotification.toString())
            writeXProp(sb, "X-MINIRILI-NOTIFYALARM", event.notifyAlarm.toString())
            writeXProp(sb, "X-MINIRILI-CREATEDAT", event.createdAt.toString())
            writeXProp(sb, "X-MINIRILI-UPDATEDAT", event.updatedAt.toString())
            writeXProp(sb, "X-MINIRILI-SORTORDER", event.sortOrder.toString())
            writeXProp(sb, "X-MINIRILI-ATTACHMENTS", event.attachments)

            // VALARM（提醒偏移 — offset 存储为分钟，转成秒写 ICS）
            if (event.reminderOffset > 0) {
                val offsetSec = -(event.reminderOffset * 60)
                sb.append("BEGIN:VALARM\n")
                sb.append("TRIGGER:-PT${offsetSec}S\n")
                sb.append("ACTION:DISPLAY\n")
                sb.append("DESCRIPTION:提醒\n")
                sb.append("END:VALARM\n")
            }

            sb.append("END:VEVENT\n")
        }

        sb.append(ICS_FOOTER)
        return sb.toString()
    }

    /** 写 X- 属性，仅跳过空串；"0"/"false" 是合法值，必须写出，否则导入 fallback 会把用户关闭的开关错置为默认开启 */
    private fun writeXProp(sb: StringBuilder, key: String, value: String) {
        if (value.isBlank()) return
        sb.append("$key:$value\n")
    }

    /**
     * 从 ICS 内容解析事件
     * 解析 X-MINIRILI-* 自定义字段，兼容不含自定义字段的标准 ICS
     */
    fun parseICS(icsContent: String): List<EventEntity> {
        val events = mutableListOf<EventEntity>()
        val unfolded = unfoldICS(icsContent)
        val lines = unfolded.lines()

        var currentEvent = mutableMapOf<String, String>()
        var inEvent = false
        var inAlarm = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            when {
                trimmed == "BEGIN:VEVENT" -> {
                    inEvent = true; inAlarm = false
                    currentEvent.clear()
                }
                trimmed == "BEGIN:VALARM" -> { inAlarm = true }
                trimmed == "END:VALARM" -> { inAlarm = false }
                trimmed == "END:VEVENT" -> {
                    inEvent = false; inAlarm = false
                    if (currentEvent.isNotEmpty()) {
                        events.add(parseEvent(currentEvent))
                    }
                }
                inEvent && !inAlarm && trimmed.isNotBlank() -> {
                    val colonIdx = trimmed.indexOf(':')
                    if (colonIdx > 0) {
                        val rawKey = trimmed.substring(0, colonIdx)
                        val value = trimmed.substring(colonIdx + 1)
                        // 去掉 property 参数（如 DTSTART;VALUE=DATE → DTSTART）
                        val key = rawKey.split(";").first()
                        currentEvent[key] = value
                    }
                }
            }
        }

        return events
    }

    /** ICS 行续：以空格/tab 开头的行是上一行的续行 */
    private fun unfoldICS(content: String): String {
        return content.lines().fold(mutableListOf<String>()) { acc, line ->
            if (line.startsWith(" ") || line.startsWith("\t")) {
                if (acc.isNotEmpty()) {
                    acc[acc.lastIndex] = acc.last() + line.trimStart()
                }
            } else {
                acc.add(line)
            }
            acc
        }.joinToString("\n")
    }

    /**
     * 从文件路径加载 ICS
     */
    fun loadICSFromFile(filePath: String): List<EventEntity> {
        return try {
            val content = java.io.File(filePath).readText()
            parseICS(content)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 保存事件到 ICS 文件
     */
    fun saveICSToFile(events: List<EventEntity>, filePath: String): Boolean {
        return try {
            java.io.File(filePath).writeText(generateICS(events))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 解析 ICS 事件数据
     * 识别 X-MINIRILI-* 自定义字段，缺失时从标准 ICS 字段推算
     */
    private fun parseEvent(data: Map<String, String>): EventEntity {
        val summary = data["SUMMARY"] ?: ""
        val description = data["DESCRIPTION"] ?: ""
        val dtStart = data["DTSTART"] ?: ""
        val date = parseICSDate(dtStart)

        // 标准字段映射
        val typeIcs = data["CATEGORIES"] ?: "Personal"
        val revTypeMap = mapOf("Birthday" to "生日", "Anniversary" to "纪念日", "Personal" to "普通")
        val eventType = revTypeMap[typeIcs] ?: typeIcs

        // ICS PRIORITY → 内部 priority
        val icsPriority = data["PRIORITY"]?.toIntOrNull() ?: 5
        val priority = when (icsPriority) { 9 -> 0; 1 -> 2; else -> 1 }

        val completed = data["STATUS"] == "COMPLETED"

        return EventEntity(
            title = unescapeICSValue(summary),
            description = unescapeICSValue(description),
            gregorianDate = DateUtils.formatGregorian(date),
            type = eventType,
            tags = data["X-MINIRILI-TAGS"] ?: "",
            color = data["X-MINIRILI-COLOR"]?.toIntOrNull() ?: 0,
            priority = data["X-MINIRILI-PRIORITY"]?.toIntOrNull() ?: priority,
            completed = data["X-MINIRILI-COMPLETED"]?.toBoolean() ?: completed,
            reminderTime = data["X-MINIRILI-REMINDERTIME"]?.toLongOrNull() ?: 0L,
            reminderOffset = data["X-MINIRILI-REMINDEROFFSET"]?.toIntOrNull() ?: 0,
            repeatType = data["X-MINIRILI-REPEATTYPE"] ?: "none",
            lunarDate = data["X-MINIRILI-LUNARDATE"] ?: "",
            useLunar = data["X-MINIRILI-USELUNAR"]?.toBoolean() ?: false,
            skipDates = data["X-MINIRILI-SKIPDATES"] ?: "",
            notifyNotification = data["X-MINIRILI-NOTIFYNOTIFICATION"]?.toBoolean() ?: true,
            notifyAlarm = data["X-MINIRILI-NOTIFYALARM"]?.toBoolean() ?: true,
            createdAt = data["X-MINIRILI-CREATEDAT"]?.toLongOrNull() ?: System.currentTimeMillis(),
            updatedAt = data["X-MINIRILI-UPDATEDAT"]?.toLongOrNull() ?: System.currentTimeMillis(),
            sortOrder = data["X-MINIRILI-SORTORDER"]?.toLongOrNull() ?: 0L,
            attachments = data["X-MINIRILI-ATTACHMENTS"] ?: ""
        )
    }

    /**
     * 解析 ICS 日期格式（YYYYMMDD 或 YYYYMMDDTHHMMSSZ）
     */
    private fun parseICSDate(dateStr: String): Calendar {
        val calendar = Calendar.getInstance()
        calendar.clear() // 清除时间字段，避免继承当前时间的时/分/秒
        val year = dateStr.substring(0, 4).toInt()
        val month = dateStr.substring(4, 6).toInt() - 1
        val day = dateStr.substring(6, 8).toInt()
        calendar.set(year, month, day)
        return calendar
    }

    /**
     * 解析 ICS 日期时间格式
     * 格式: YYYYMMDDTHHMMSSZ
     */
    private fun parseICSDateTime(dateTimeStr: String): Calendar {
        val calendar = Calendar.getInstance()
        val year = dateTimeStr.substring(0, 4).toInt()
        val month = dateTimeStr.substring(4, 6).toInt() - 1
        val day = dateTimeStr.substring(6, 8).toInt()
        val hour = dateTimeStr.substring(9, 11).toInt()
        val minute = dateTimeStr.substring(11, 13).toInt()

        calendar.set(year, month, day, hour, minute, 0)
        return calendar
    }

    /**
     * 格式化 ICS 日期（YYYYMMDD），用于 DTSTART;VALUE=DATE
     */
    internal fun formatICSDate(dateStr: String): String {
        val parts = dateStr.split("-")
        return "${parts[0]}${parts[1]}${parts[2]}"
    }

    /**
     * 格式化 ICS 日期时间
     * 格式: YYYYMMDDTHHMMSSZ
     */
    internal fun formatICSDateTime(timestamp: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        return String.format("%04d%02d%02dT%02d%02d%02dZ",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            calendar.get(Calendar.SECOND)
        )
    }

    /**
     * 转义 ICS 特殊字符
     */
    internal fun escapeICSValue(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace(",", "\\,")
            .replace(";", "\\;")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    /**
     * 解码 ICS 特殊字符
     */
    private fun unescapeICSValue(value: String): String {
        return value
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\\\", "\\")
    }
}
