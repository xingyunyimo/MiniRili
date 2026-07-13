package com.minirili.app.ui.screens.events

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.minirili.app.database.entity.EventEntity
import com.minirili.app.stopAlarmOnEventOpen
import com.minirili.app.ui.navigation.Screen
import com.minirili.app.ui.viewmodel.EventViewModel
import com.minirili.app.utils.DateUtils
import com.minirili.app.utils.LunarCalendar
import java.util.Calendar

/**
 * 所有事件列表管理页（Bug2）
 *
 * 功能：
 * - 按日期分组倒序展示所有事件（含已完成）
 * - 顶栏支持搜索过滤
 * - 单条事件：点击进详情、长按弹删除确认
 * - 顶部统计：总事件数 / 已完成数
 * - 空态：引导新建
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AllEventsScreen(
    viewModel: EventViewModel,
    navController: NavController
) {
    val allEvents by viewModel.allEvents.collectAsState()
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    var showDeleteTarget by remember { mutableStateOf<EventEntity?>(null) }
    var filterCompleted by remember { mutableStateOf<Boolean?>(null) }  // null=全部, true=只看已完成, false=只看未完成

    val filtered = remember(allEvents, query, filterCompleted) {
        var list = allEvents
        if (filterCompleted != null) list = list.filter { it.completed == filterCompleted }
        if (query.isNotBlank()) {
            val q = query.trim()
            list = list.filter { it.title.contains(q, true) || it.description.contains(q, true) }
        }
        list.sortedByDescending { it.gregorianDate }
    }

    // 按日期分组
    val grouped = remember(filtered) {
        filtered.groupBy { it.gregorianDate }.toSortedMap(reverseOrder())
    }

    val totalCount = allEvents.size
    val completedCount = allEvents.count { it.completed }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("所有事件 ($totalCount)") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { filterCompleted = null }) {
                        Text(
                            if (filterCompleted == null) "全" else "全",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (filterCompleted == null) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    IconButton(onClick = { filterCompleted = false }) {
                        Text(
                            "未完",
                            fontWeight = if (filterCompleted == false) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    IconButton(onClick = { filterCompleted = true }) {
                        Text(
                            "完成",
                            fontWeight = if (filterCompleted == true) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.EventDetail.createRoute(0L)) }) {
                Icon(Icons.Default.Add, contentDescription = "新建事件")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 搜索区域
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索标题或描述") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            )

            // 统计条
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(onClick = { filterCompleted = null }, label = { Text("全部 $totalCount") })
                AssistChip(onClick = { filterCompleted = false }, label = { Text("未完成 ${totalCount - completedCount}") })
                AssistChip(onClick = { filterCompleted = true }, label = { Text("已完成 $completedCount") })
            }

            if (filtered.isEmpty()) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (query.isBlank() && filterCompleted == null) "还没有事件，点右下角 + 新建"
                        else "没有匹配的事件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    grouped.forEach { (dateStr, events) ->
                        stickyHeader {
                            Surface(
                                Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val dateLabel = buildString {
                                        append(dateStr)
                                        val c = DateUtils.parseGregorian(dateStr)
                                        append(" · ").append(DateUtils.getWeekdayFull(c.get(Calendar.DAY_OF_WEEK)))
                                        runCatching {
                                            val lunar = LunarCalendar.getLunarMonthDayName(c)
                                            if (lunar.isNotEmpty()) append(" · $lunar")
                                        }
                                    }
                                    Text(dateLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    Text("${events.size} 项", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    grouped.forEach { (dateStr, events) ->
                        item(key = "group-$dateStr") {
                            Spacer(Modifier.height(2.dp))
                        }
                        grouped[dateStr]!!.forEach { ev ->
                            item(key = "ev-${ev.id}") {
                                EventRow(
                                    event = ev,
                                    onClick = {
                                        stopAlarmOnEventOpen()
                                        navController.navigate(Screen.EventDetail.createRoute(ev.id))
                                    },
                                    onLongClick = { showDeleteTarget = ev }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    showDeleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { showDeleteTarget = null },
            title = { Text("删除事件") },
            text = { Text("确定删除「${target.title}」？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEvent(target)
                    showDeleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteTarget = null }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EventRow(
    event: EventEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (event.completed) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (event.color != 0) {
                Box(
                    Modifier.width(4.dp).height(48.dp).padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(event.color.toLong() or 0xFF000000L))
                )
            }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (event.reminderTime > 0) {
                        val timeStr = runCatching {
                            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(event.reminderTime))
                        }.getOrDefault("")
                        Text(
                            "$timeStr  ",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (event.completed) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        if (event.completed) "✓ ${event.title}" else event.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (event.completed) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (event.description.isNotBlank()) {
                    Text(
                        event.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Row(
                    Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(event.type, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (event.tags.isNotBlank()) {
                        Text("#${event.tags.replace(",", " #")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    if (event.repeatType != "none") {
                        Text("⟳", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            Icon(
                Icons.Default.Delete, contentDescription = "长按删除",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
