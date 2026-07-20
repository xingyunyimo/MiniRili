package com.minirili.app.ui.screens.event

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.minirili.app.ui.viewmodel.EventViewModel
import com.minirili.app.utils.DateUtils
import com.minirili.app.utils.LunarCalendar
import kotlinx.coroutines.launch
import java.util.Calendar

private val BUILTIN_TYPES = listOf("普通", "生日", "纪念日", "工作", "学习")

private const val TYPE_PREFS = "calendar_event_types"
private const val KEY_CUSTOM_TYPES = "custom_types"

object EventTypeStore {
    fun loadAll(context: Context): List<String> {
        val prefs = context.getSharedPreferences(TYPE_PREFS, Context.MODE_PRIVATE)
        val custom = prefs.getString(KEY_CUSTOM_TYPES, "") ?: ""
        val customList = custom.split("|").map { it.trim() }
            .filter { it.isNotEmpty() && it !in BUILTIN_TYPES }
        return BUILTIN_TYPES + customList
    }

    fun add(context: Context, type: String) {
        val normalized = type.trim()
        if (normalized.isEmpty() || normalized in BUILTIN_TYPES) return
        val prefs = context.getSharedPreferences(TYPE_PREFS, Context.MODE_PRIVATE)
        val custom = prefs.getString(KEY_CUSTOM_TYPES, "") ?: ""
        val list = custom.split("|").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (normalized !in list) list.add(normalized)
        prefs.edit().putString(KEY_CUSTOM_TYPES, list.joinToString("|")).apply()
    }

    fun remove(context: Context, type: String) {
        if (type in BUILTIN_TYPES) return
        val prefs = context.getSharedPreferences(TYPE_PREFS, Context.MODE_PRIVATE)
        val custom = prefs.getString(KEY_CUSTOM_TYPES, "") ?: ""
        val list = custom.split("|").map { it.trim() }.filter { it.isNotEmpty() && it != type }
        prefs.edit().putString(KEY_CUSTOM_TYPES, list.joinToString("|")).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EventDetailScreen(
    eventId: Long,
    viewModel: EventViewModel,
    navController: NavController,
    onBack: () -> Unit = {},
    initDate: String = ""
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var title by remember { mutableStateOf(TextFieldValue("")) }
    var description by remember { mutableStateOf(TextFieldValue("")) }
    var selectedType by remember { mutableStateOf("普通") }
    var selectedDate by remember { mutableStateOf(initDate.takeIf { it.isNotBlank() } ?: DateUtils.today()) }
    var useLunar by remember { mutableStateOf(false) }
    var reminderOffset by remember { mutableStateOf(0) }
    var repeatType by remember { mutableStateOf("none") }

    var eventHour by remember { mutableStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var eventMinute by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MINUTE)) }
    var forceAllDay by remember { mutableStateOf(false) }
    var originalReminderTime by remember { mutableStateOf(0L) }
    var skipDates by remember { mutableStateOf("") }
    var notifyNotification by remember { mutableStateOf(true) }
    var notifyAlarm by remember { mutableStateOf(true) }
    var tagsText by remember { mutableStateOf("") }
    var eventColor by remember { mutableIntStateOf(0) }
    var eventPriority by remember { mutableIntStateOf(1) }
    var eventCompleted by remember { mutableStateOf(false) }
    var attachmentsJson by remember { mutableStateOf("") }
    var attachmentItems by remember { mutableStateOf<List<AttachmentItem>>(emptyList()) }

    var showDateTimeDialog by remember { mutableStateOf(false) }
    var showTypeDropdown by remember { mutableStateOf(false) }
    var showRepeatDropdown by remember { mutableStateOf(false) }
    var showReminderDropdown by remember { mutableStateOf(false) }
    var showAddTypeDialog by remember { mutableStateOf(false) }
    var showDeleteTypeDialog by remember { mutableStateOf<String?>(null) }
    var showDeleteEventDialog by remember { mutableStateOf(false) }
    var showSkipConfirmDialog by remember { mutableStateOf<String?>(null) }
    var deleteChoice by remember { mutableStateOf("all") } // "this"=仅本次, "all"=全部
    val contextDate = remember { initDate.takeIf { it.isNotBlank() } ?: selectedDate }

    var customTypesVersion by remember { mutableStateOf(0) }
    val allTypes = remember(customTypesVersion) { EventTypeStore.loadAll(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(eventId) {
        if (eventId > 0) {
            val event = viewModel.getEventById(eventId)
            event?.let {
                title = TextFieldValue(it.title)
                description = TextFieldValue(it.description)
                selectedType = it.type
                selectedDate = it.gregorianDate
                useLunar = it.useLunar
                reminderOffset = it.reminderOffset
                repeatType = it.repeatType
                skipDates = it.skipDates
                if (it.reminderTime != 0L) {
                    val c = Calendar.getInstance().apply { timeInMillis = kotlin.math.abs(it.reminderTime) }
                    eventHour = c.get(Calendar.HOUR_OF_DAY)
                    eventMinute = c.get(Calendar.MINUTE)
                    originalReminderTime = it.reminderTime
                    forceAllDay = false
                } else {
                    forceAllDay = true
                    originalReminderTime = 0L
                }
                notifyNotification = it.notifyNotification
                notifyAlarm = it.notifyAlarm
                tagsText = it.tags
                eventColor = it.color
                eventPriority = it.priority
                eventCompleted = it.completed
                attachmentsJson = it.attachments
                attachmentItems = parseAttachmentItems(it.attachments)
            }
        }
    }

    LaunchedEffect(allTypes, selectedType) {
        if (selectedType !in allTypes) selectedType = "普通"
    }

    val isEditing = eventId > 0

    val repeatTypeOptions = if (useLunar) listOf(
        "none" to "不重复", "monthly" to "每月(农历)", "yearly" to "每年(农历)"
    ) else listOf(
        "none" to "不重复", "daily" to "每天", "weekly" to "每周",
        "monthly" to "每月", "yearly" to "每年", "workday" to "工作日", "weekend" to "周末"
    )
    val repeatLabel = repeatTypeOptions.firstOrNull { it.first == repeatType }?.second
        ?: if (useLunar) "不重复" else "不重复"
    LaunchedEffect(useLunar) {
        if (useLunar && repeatType !in listOf("none", "monthly", "yearly")) {
            repeatType = "none"
        }
    }

    val reminderOptions = listOf(0 to "当日", 5 to "5分钟前", 15 to "15分钟前", 30 to "30分钟前", 60 to "1小时前", 1440 to "1天前")
    val reminderLabel = reminderOptions.firstOrNull { it.first == reminderOffset }?.second ?: "当日"

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = getFileName(context, it)
            val savedUri = copyToPrivateStorage(context, it)
            if (savedUri != null) {
                val newEntry = """{"name":"${escapeJson(fileName)}","uri":"$savedUri","type":"file"}"""
                val newJson = if (attachmentsJson.isBlank()) "[$newEntry]" else
                    attachmentsJson.replace("[", "[$newEntry,", false)
                attachmentsJson = newJson
                attachmentItems = parseAttachmentItems(newJson)
            }
        }
    }

    // 当前事件时间的显示值（编辑模式带提醒时显示原本时间，否则显示真实选择值）
    val displayedTime: Long = when {
        isEditing && originalReminderTime != 0L -> {
            // 用 selectedDate 做日期基准，只从 originalReminderTime 取 hour/minute
            val baseCal = Calendar.getInstance().apply { timeInMillis = kotlin.math.abs(originalReminderTime) }
            val c = DateUtils.parseGregorian(selectedDate)
            c.set(Calendar.HOUR_OF_DAY, baseCal.get(Calendar.HOUR_OF_DAY))
            c.set(Calendar.MINUTE, baseCal.get(Calendar.MINUTE))
            c.set(Calendar.SECOND, 0)
            c.timeInMillis
        }
        forceAllDay -> 0L
        else -> {
            val c = DateUtils.parseGregorian(selectedDate)
            c.set(Calendar.HOUR_OF_DAY, eventHour)
            c.set(Calendar.MINUTE, eventMinute)
            c.set(Calendar.SECOND, 0)
            c.timeInMillis
        }
    }
    val displayedYear: Int
    val displayedMonth: Int
    val displayedDay: Int
    if (displayedTime > 0L) {
        val c = Calendar.getInstance().apply { timeInMillis = displayedTime }
        displayedYear = c.get(Calendar.YEAR)
        displayedMonth = c.get(Calendar.MONTH) + 1
        displayedDay = c.get(Calendar.DAY_OF_MONTH)
    } else {
        val parts = selectedDate.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size == 3) {
            displayedYear = parts[0]; displayedMonth = parts[1]; displayedDay = parts[2]
        } else {
            val c = Calendar.getInstance()
            displayedYear = c.get(Calendar.YEAR); displayedMonth = c.get(Calendar.MONTH) + 1; displayedDay = c.get(Calendar.DAY_OF_MONTH)
        }
    }

    fun buildEvent(): com.minirili.app.database.entity.EventEntity {
        val reminderTime: Long = when {
            forceAllDay -> 0L
            else -> {
                // 使用 2000-01-01 作为基准日期，确保时间戳始终为正（调度器只取 hour/minute）
                val c = Calendar.getInstance().apply {
                    set(Calendar.YEAR, 2000)
                    set(Calendar.MONTH, Calendar.JANUARY)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, eventHour)
                    set(Calendar.MINUTE, eventMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                c.timeInMillis
            }
        }
        return com.minirili.app.database.entity.EventEntity(
            id = eventId,
            title = title.text,
            description = description.text,
            type = selectedType,
            tags = tagsText.trim(),
            color = eventColor,
            gregorianDate = selectedDate,
            lunarDate = if (useLunar) selectedDate else "",
            useLunar = useLunar,
            reminderTime = reminderTime,
            reminderOffset = reminderOffset,
            repeatType = repeatType,
            notifyNotification = notifyNotification,
            notifyAlarm = notifyAlarm,
            skipDates = skipDates,
            priority = eventPriority,
            completed = eventCompleted,
            attachments = attachmentsJson
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "编辑事件" else "新建事件") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = { showDeleteEventDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = {
                        if (title.text.isBlank()) return@TextButton
                        if (forceAllDay && (notifyNotification || notifyAlarm)) {
                            scope.launch {
                                snackbarHostState.showSnackbar("全天事件无法发送通知，请取消全天或关闭通知方式")
                            }
                            return@TextButton
                        }
                        val ev = buildEvent()
                        if (isEditing) viewModel.updateEvent(ev) else viewModel.insertEvent(ev)
                        navController.popBackStack()
                    }) { Text("保存", fontWeight = FontWeight.Bold) }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("事件标题 *") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("事件描述") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4
            )

            // ========== 事件类型 + 新增 ==========
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ExposedDropdownMenuBox(
                    expanded = showTypeDropdown,
                    onExpandedChange = { showTypeDropdown = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedType, onValueChange = {}, readOnly = true,
                        label = { Text("事件类型 *") }, modifier = Modifier.menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTypeDropdown) },
                        shape = RoundedCornerShape(8.dp)
                    )
                    ExposedDropdownMenu(expanded = showTypeDropdown, onDismissRequest = { showTypeDropdown = false }) {
                        allTypes.forEach { type ->
                            val isCustom = type !in BUILTIN_TYPES
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    selectedType = type
                                                    showTypeDropdown = false
                                                },
                                                onLongClick = {
                                                    if (isCustom) showDeleteTypeDialog = type
                                                }
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            type,
                                            color = if (isCustom) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isCustom) {
                                            Text(
                                                "长按删除", style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedType = type
                                    showTypeDropdown = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = { showAddTypeDialog = true }) {
                    Text("+", style = MaterialTheme.typography.titleMedium)
                }
            }

            // ========== 记事时间 ==========
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("记事时间", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = !useLunar, onClick = { useLunar = false }, label = { Text("阳历") },
                                colors = invertedChipColors(!useLunar),
                                border = if (!useLunar) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                else FilterChipDefaults.filterChipBorder(enabled = true, selected = !useLunar)
                            )
                            Spacer(Modifier.width(4.dp))
                            FilterChip(
                                selected = useLunar, onClick = { useLunar = true }, label = { Text("农历") },
                                colors = invertedChipColors(useLunar),
                                border = if (useLunar) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                else FilterChipDefaults.filterChipBorder(enabled = true, selected = useLunar)
                            )
                        }
                    }

                    // 全天 + 时间（非全天才显示时间）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("全天", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(4.dp))
                            Switch(checked = forceAllDay, onCheckedChange = {
                                forceAllDay = it
                                if (it) originalReminderTime = 0L
                            })
                        }
                        if (!forceAllDay) {
                            val timeStr = if (displayedTime > 0L) {
                                val c = Calendar.getInstance().apply { timeInMillis = displayedTime }
                                String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
                            } else String.format("%02d:%02d", eventHour, eventMinute)
                            Text(
                                timeStr,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (displayedTime > 0L && displayedTime < System.currentTimeMillis()) Color.Gray
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 阳历/农历·年月日
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (useLunar) {
                                val c = Calendar.getInstance().apply { set(displayedYear, displayedMonth - 1, displayedDay) }
                                val lunarMonth = runCatching { LunarCalendar.getLunarMonthName(c) }.getOrDefault("")
                                val lunarDay = runCatching { LunarCalendar.getLunarDay(c) }.getOrDefault("")
                                val ganzhi = runCatching { LunarCalendar.getGanZhiYear(c) }.getOrDefault("")
                                "${ganzhi}${lunarMonth}月${lunarDay} · ${displayedYear}年${displayedMonth}月${displayedDay}日"
                            } else {
                                "${displayedYear}年${displayedMonth}月${displayedDay}日"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (displayedTime > 0L && displayedTime < System.currentTimeMillis()) Color.Gray
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(onClick = { showDateTimeDialog = true }) { Text("修改") }
                    }
                }
            }

            // ========== 重复类型（下拉） ==========
            ExposedDropdownMenuBox(
                expanded = showRepeatDropdown,
                onExpandedChange = { showRepeatDropdown = it }
            ) {
                OutlinedTextField(
                    value = "重复类型：$repeatLabel",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showRepeatDropdown) },
                    shape = RoundedCornerShape(8.dp)
                )
                ExposedDropdownMenu(expanded = showRepeatDropdown, onDismissRequest = { showRepeatDropdown = false }) {
                    repeatTypeOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                repeatType = value
                                showRepeatDropdown = false
                            }
                        )
                    }
                }
            }

            // ========== 周期事件：跳过明天 ==========
            if (repeatType != "none" && eventId > 0) {
                val tomorrow = remember(selectedDate) {
                    val c = DateUtils.parseGregorian(DateUtils.today())
                    c.add(Calendar.DAY_OF_MONTH, 1)
                    DateUtils.formatGregorian(c)
                }
                OutlinedButton(
                    onClick = { showSkipConfirmDialog = tomorrow },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.EventBusy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("跳过明天（$tomorrow）的提醒")
                }
            }

            // ========== 通知方式 ==========
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("通知方式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = notifyNotification, onCheckedChange = { notifyNotification = it })
                            Text("通知栏", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = notifyAlarm, onCheckedChange = { notifyAlarm = it })
                            Text("闹钟", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // ========== 附件（EVT-08，暂隐藏） ==========
            // 附件功能暂不启用，保留代码便于后续恢复

            // ========== 优先级 + 完成状态 ==========
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("优先级 · 完成状态", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val priorityOptions = listOf(0 to "低", 1 to "中", 2 to "高")
                        priorityOptions.forEach { (value, label) ->
                            FilterChip(
                                selected = eventPriority == value,
                                onClick = { eventPriority = value },
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = eventCompleted, onCheckedChange = { eventCompleted = it })
                        Text("标记为已完成", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // ========== 标签 + 颜色 ==========
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("标签 · 颜色", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = tagsText,
                        onValueChange = { tagsText = it },
                        label = { Text("标签（逗号分隔）") },
                        placeholder = { Text("工作, 家庭") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("事件颜色", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    val colorOptions = listOf(
                        0xE57373 to "红",
                        0x81C784 to "绿",
                        0x64B5F6 to "蓝",
                        0xFFB74D to "橙",
                        0xBA68C8 to "紫",
                        0 to "默认"
                    )
                    // 每行 3 个，双行展示，右侧小屏不再溢出
                    for (rowStart in colorOptions.indices step 3) {
                        val rowItems = colorOptions.drop(rowStart).take(3)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowItems.forEach { (c, label) ->
                                val isSelected = eventColor == c
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { eventColor = c }
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else Color.Transparent,
                                            RoundedCornerShape(16.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (c == 0) MaterialTheme.colorScheme.outline else Color(c.toLong() or 0xFF000000L))
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(label, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            // ========== 提醒（下拉） ==========
            ExposedDropdownMenuBox(
                expanded = showReminderDropdown,
                onExpandedChange = { showReminderDropdown = it }
            ) {
                OutlinedTextField(
                    value = "提醒：$reminderLabel",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showReminderDropdown) },
                    shape = RoundedCornerShape(8.dp)
                )
                ExposedDropdownMenu(expanded = showReminderDropdown, onDismissRequest = { showReminderDropdown = false }) {
                    reminderOptions.forEach { (option, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                reminderOffset = option
                                showReminderDropdown = false
                            }
                        )
                    }
                }
            }
        }
    }

    // 合并的日期+时间对话框
    if (showDateTimeDialog) {
        DateTimePickerDialog(
            initDate = selectedDate,
            initHour = eventHour,
            initMinute = eventMinute,
            useLunar = useLunar,
            onConfirm = { date, h, m ->
                selectedDate = date
                eventHour = h
                eventMinute = m
                originalReminderTime = 0L
                showDateTimeDialog = false
            },
            onDismiss = { showDateTimeDialog = false }
        )
    }

    // 新增类别
    if (showAddTypeDialog) {
        var newType by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTypeDialog = false },
            title = { Text("新增事件类型") },
            text = { OutlinedTextField(value = newType, onValueChange = { newType = it }, singleLine = true, label = { Text("类别名称") }) },
            confirmButton = {
                TextButton(onClick = {
                    EventTypeStore.add(context, newType)
                    customTypesVersion++
                    newType = ""
                    showAddTypeDialog = false
                }, enabled = newType.trim().isNotEmpty()) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAddTypeDialog = false }) { Text("取消") } }
        )
    }

    // 删除类别
    showDeleteTypeDialog?.let { typeToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteTypeDialog = null },
            title = { Text("删除事件类型") },
            text = { Text("确定删除「$typeToDelete」？删除后不影响已有事件。") },
            confirmButton = {
                TextButton(onClick = {
                    EventTypeStore.remove(context, typeToDelete)
                    if (selectedType == typeToDelete) selectedType = "普通"
                    customTypesVersion++
                    showDeleteTypeDialog = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteTypeDialog = null }) { Text("取消") } }
        )
    }

    // 删除事件
    if (showDeleteEventDialog) {
        if (repeatType != "none" && eventId > 0) {
            // 重复事件：选择删除方式
            AlertDialog(
                onDismissRequest = { showDeleteEventDialog = false },
                title = { Text("删除重复事件") },
                text = {
                    Column {
                        Text("该事件为重复事件，请选择删除方式：")
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = deleteChoice == "this",
                                onClick = { deleteChoice = "this" })
                            Text("仅删除本次（$contextDate）", modifier = Modifier.clickable { deleteChoice = "this" })
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = deleteChoice == "all",
                                onClick = { deleteChoice = "all" })
                            Text("删除全部事件", modifier = Modifier.clickable { deleteChoice = "all" })
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (deleteChoice == "this") {
                            viewModel.skipOccurrence(eventId, contextDate)
                        } else {
                            viewModel.deleteEvent(buildEvent())
                        }
                        showDeleteEventDialog = false
                        navController.popBackStack()
                    }) { Text("确认", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { showDeleteEventDialog = false }) { Text("取消") } }
            )
        } else {
            // 非重复事件：原样单确认
            AlertDialog(
                onDismissRequest = { showDeleteEventDialog = false },
                title = { Text("删除事件") },
                text = { Text("确认删除当前事件？此操作不可恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteEvent(buildEvent())
                        showDeleteEventDialog = false
                        navController.popBackStack()
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { showDeleteEventDialog = false }) { Text("取消") } }
            )
        }
    }

    // EVT-10: 周期事件跳过确认
    showSkipConfirmDialog?.let { targetDate ->
        AlertDialog(
            onDismissRequest = { showSkipConfirmDialog = null },
            title = { Text("跳过明天提醒") },
            text = { Text("已跳过 $targetDate 的提醒。后续周期触发不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.skipOccurrence(eventId, targetDate)
                    showSkipConfirmDialog = null
                }) { Text("确认跳过") }
            },
            dismissButton = { TextButton(onClick = { showSkipConfirmDialog = null }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerDialog(
    initDate: String,
    initHour: Int,
    initMinute: Int,
    useLunar: Boolean,
    onConfirm: (String, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val init = DateUtils.parseGregorian(initDate)
    var yearText by remember { mutableStateOf(init.get(Calendar.YEAR).toString()) }
    var monthText by remember { mutableStateOf((init.get(Calendar.MONTH) + 1).toString()) }
    var dayText by remember { mutableStateOf(init.get(Calendar.DAY_OF_MONTH).toString()) }
    var hourText by remember { mutableStateOf(String.format("%02d", initHour)) }
    var minuteText by remember { mutableStateOf(String.format("%02d", initMinute)) }

    val year = yearText.toIntOrNull() ?: init.get(Calendar.YEAR)
    val month = monthText.toIntOrNull()?.coerceIn(1, 12) ?: (init.get(Calendar.MONTH) + 1)
    val maxDay = DateUtils.getDaysInMonth(year, month)
    val day = dayText.toIntOrNull()?.coerceIn(1, maxDay) ?: init.get(Calendar.DAY_OF_MONTH).coerceAtMost(maxDay)
    val hour = hourText.toIntOrNull()?.coerceIn(0, 23) ?: initHour
    val minute = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: initMinute

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (useLunar) "选择农历日期与时间" else "选择日期与时间") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 年月日
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = yearText, onValueChange = { if (it.all { c -> c.isDigit() } || it.isEmpty()) yearText = it },
                        modifier = Modifier.width(76.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center)
                    )
                    Text("年", modifier = Modifier.padding(horizontal = 4.dp))
                    OutlinedTextField(
                        value = monthText, onValueChange = { if (it.all { c -> c.isDigit() } || it.isEmpty()) monthText = it },
                        modifier = Modifier.width(60.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center)
                    )
                    Text("月", modifier = Modifier.padding(horizontal = 4.dp))
                    OutlinedTextField(
                        value = dayText, onValueChange = { if (it.all { c -> c.isDigit() } || it.isEmpty()) dayText = it },
                        modifier = Modifier.width(60.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center)
                    )
                    Text("日", modifier = Modifier.padding(horizontal = 4.dp))
                }
                // 时分
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = hourText, onValueChange = { if (it.all { c -> c.isDigit() } || it.isEmpty()) hourText = it },
                        modifier = Modifier.width(64.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center)
                    )
                    Text(" : ", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 4.dp))
                    OutlinedTextField(
                        value = minuteText, onValueChange = { if (it.all { c -> c.isDigit() } || it.isEmpty()) minuteText = it },
                        modifier = Modifier.width(64.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            val dateStr = if (useLunar) {
                // 用户输入的是农历年月日，转为公历后再存 gregorianDate
                LunarCalendar.lunarToGregorian(year, month, day)
                    ?: String.format("%04d-%02d-%02d", year, month, day) // 转换失败回退
            } else {
                String.format("%04d-%02d-%02d", year, month, day)
            }
            onConfirm(dateStr, hour, minute)
        }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun invertedChipColors(selected: Boolean): SelectableChipColors =
    if (selected) FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        selectedLabelColor = MaterialTheme.colorScheme.primary
    ) else FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

// ===== EVT-08 附件辅助函数 =====

data class AttachmentItem(val name: String, val uri: String)

private fun parseAttachmentItems(json: String): List<AttachmentItem> {
    if (json.isBlank()) return emptyList()
    return try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            AttachmentItem(name = obj.optString("name"), uri = obj.optString("uri"))
        }
    } catch (_: Exception) { emptyList() }
}

private fun serializeAttachments(items: List<AttachmentItem>): String {
    if (items.isEmpty()) return ""
    val arr = org.json.JSONArray()
    items.forEach {
        val obj = org.json.JSONObject()
        obj.put("name", it.name)
        obj.put("uri", it.uri)
        obj.put("type", "file")
        arr.put(obj)
    }
    return arr.toString()
}

private fun openAttachment(context: Context, uriStr: String) {
    runCatching {
        val rawUri = Uri.parse(uriStr)
        // 私有存储里的 file:// 需通过 FileProvider 转 content:// 并授予读权限，外部应用才能打开
        val contentUri: Uri = if (rawUri.scheme == "file") {
            val file = java.io.File(rawUri.path ?: "")
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } else rawUri

        val mime = runCatching { context.contentResolver.getType(contentUri) }.getOrNull() ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "打开附件").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }.onFailure {
        android.widget.Toast.makeText(context, "无法打开：${it.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun deleteAttachmentFile(uriStr: String) {
    runCatching {
        val uri = Uri.parse(uriStr)
        if (uri.scheme == "file") {
            java.io.File(uri.path ?: "").takeIf { it.exists() }?.delete()
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx >= 0) { it.moveToFirst(); it.getString(idx) } else "未知文件"
    } ?: uri.lastPathSegment ?: "未知文件"
}

private fun copyToPrivateStorage(context: Context, uri: Uri): String? {
    return try {
        val fileName = getFileName(context, uri)
        val dir = java.io.File(context.filesDir, "attachments")
        if (!dir.exists()) dir.mkdirs()
        val dest = java.io.File(dir, "${System.currentTimeMillis()}_$fileName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.toURI().toString()
    } catch (_: Exception) { null }
}

private fun escapeJson(s: String): String {
    return s.replace("\\", "\\\\").replace("\"", "\\\"")
}
